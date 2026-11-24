import SwiftUI
import UserNotifications

struct AlertSettings: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    @ObservedObject var notifications: PrayerNotifications
    @Environment(\.openURL) private var openURL
    @State private var testDate: Date?
    @State private var testFailed = false
    @State private var testTask: Task<Void, Never>?

    var body: some View {
        Form {
            Section {
                Toggle(preferences.text("phone_alerts"), isOn: $preferences.alertsEnabled)
                if notifications.permission == .denied {
                    Button(preferences.text("phone_permissions")) {
                        if let settings = URL(string: UIApplication.openSettingsURLString) { openURL(settings) }
                    }
                }
                if !preferences.locationConfirmed {
                    Text(preferences.text("phone_location_needed")).foregroundStyle(.secondary)
                }
                if notifications.scheduleFailed {
                    Text(preferences.text("phone_test_failed")).foregroundStyle(.secondary)
                }
            }
            Section(preferences.text("phone_prayer_settings")) {
                ForEach([0, 2, 3, 4, 5], id: \.self) { index in
                    NavigationLink(preferences.prayerNames()[index]) {
                        PrayerAlertSettings(preferences: $preferences, schedule: schedule, index: index)
                    }
                }
                NavigationLink(preferences.text("phone_default_audio_title")) {
                    AdhanSettings(preferences: $preferences)
                }
            }
            Section(preferences.text("phone_quiet_title")) {
                Toggle(preferences.text("phone_quiet_enabled"), isOn: $preferences.quietHours.enabled)
                if preferences.quietHours.enabled {
                    PrayerClockPicker(preferences: preferences, label: "phone_quiet_start", minute: $preferences.quietHours.start)
                    PrayerClockPicker(preferences: preferences, label: "phone_quiet_end", minute: $preferences.quietHours.end)
                    Picker(preferences.text("phone_quiet_mode"), selection: $preferences.quietHours.mode) {
                        Text(preferences.text("phone_off")).tag(0)
                        Text(preferences.text("phone_silent")).tag(4)
                    }
                }
            }
            Section {
                if let date = testDate {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        let seconds = Int(ceil(date.timeIntervalSince(context.date)))
                        if seconds > 0 {
                            Text(preferences.text("phone_test_pending").replacingOccurrences(of: "%s",
                                with: preferences.displayDigits(String(seconds))))
                            Button(preferences.text("phone_test_cancel")) { cancelTest() }
                        } else {
                            Text(preferences.text("phone_test_sent"))
                            testButtons
                        }
                    }
                } else {
                    testButtons
                }
                if testFailed { Text(preferences.text("phone_test_failed")).foregroundStyle(.secondary) }
            }
        }
        .navigationTitle(preferences.text("phone_audio"))
        .onChange(of: preferences.alertsEnabled) { _, enabled in
            if enabled { Task { _ = await notifications.authorize() } }
        }
    }

    private var testButtons: some View {
        Group {
            Button(preferences.text("phone_test_notification")) { startTest(adhan: false) }
            Button(preferences.text("phone_test_adhan")) { startTest(adhan: true) }
        }
        .disabled(testTask != nil)
    }

    private func startTest(adhan: Bool) {
        testFailed = false
        testTask = Task {
            testDate = await notifications.test(preferences: preferences, adhan: adhan)
            testFailed = !Task.isCancelled && testDate == nil
            testTask = nil
        }
    }

    private func cancelTest() {
        testTask?.cancel()
        notifications.cancelTest()
        testDate = nil
    }
}

struct PrayerAlertSettings: View {
    @EnvironmentObject private var audio: PhoneAdhan
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    let index: Int
    private let soundModes: [(Int, String)] = [(2, "phone_adhan"), (1, "phone_notification"), (4, "phone_silent")]

    var body: some View {
        let plain = schedule.day(at: .now, prefs: preferences)?[index]
        let setting = $preferences.prayerAlerts[index]
        let calendar = PrayerSchedule.civilCalendar(preferences)
        let symbols = calendar.shortStandaloneWeekdaySymbols
        Form {
            Toggle(preferences.text("phone_prayer_enabled"), isOn: setting.enabled)
            Section {
                Picker(preferences.text("phone_mode"), selection: setting.mode) {
                    ForEach(soundModes.indices, id: \.self) { i in
                        Text(preferences.text(soundModes[i].1)).tag(soundModes[i].0)
                    }
                }
                timeStepper("phone_offset", value: $preferences.offsets[index], range: -30...30, date: plain)
                timeStepper("phone_before", value: setting.before, range: 0...60,
                            date: plain?.addingTimeInterval(-Double(setting.before.wrappedValue * 60)))
                timeStepper("phone_after", value: setting.after, range: 0...60,
                            date: plain?.addingTimeInterval(Double(setting.after.wrappedValue * 60)))
            }
            .disabled(!setting.enabled.wrappedValue)
            Section(preferences.text("phone_adhan")) {
                Toggle(preferences.text("phone_default_audio"), isOn: setting.useDefaultAudio)
                AdhanSelectionRows(preferences: preferences, selection: setting.audio,
                    inherit: index == 0 ? preferences.adhan.fajrVoice : preferences.adhan.selection.voice,
                    defaultLevelAndLength: setting.useDefaultAudio.wrappedValue ? preferences.adhan.selection : nil)
            }
            .disabled(!setting.enabled.wrappedValue)
            Section(preferences.text("phone_weekdays")) {
                ForEach(0..<7, id: \.self) { weekday in
                    let bit = 1 << weekday
                    Toggle(symbols[weekday], isOn: Binding(
                        get: { (setting.days.wrappedValue & bit) != 0 },
                        set: { enabled in
                            if enabled { setting.days.wrappedValue |= bit } else { setting.days.wrappedValue &= ~bit }
                        }
                    ))
                    if (setting.days.wrappedValue & bit) != 0 {
                        Picker(preferences.text("phone_day_sound"), selection: setting.dayModes[weekday]) {
                            Text(preferences.text("phone_use_defaults")).tag(-1)
                            ForEach(soundModes.indices, id: \.self) { i in
                                Text(preferences.text(soundModes[i].1)).tag(soundModes[i].0)
                            }
                        }
                    }
                }
            }
            .disabled(!setting.enabled.wrappedValue)
        }
        .navigationTitle(preferences.prayerNames()[index])
        .onDisappear { audio.stop(previewOnly: true) }
    }

    private func timeStepper(_ label: String, value: Binding<Int>, range: ClosedRange<Int>, date: Date?) -> some View {
        Stepper(value: value, in: range) {
            VStack(alignment: .leading) {
                Text(preferences.text(label))
                let number = preferences.displayDigits(String(value.wrappedValue))
                Text(number + (date.map { " · " + preferences.time($0) } ?? "")).foregroundStyle(.secondary)
            }
        }
    }
}

struct FastingSettings: View {
    @Binding var preferences: PhonePreferences
    @ObservedObject var notifications: PrayerNotifications

    var body: some View {
        Form {
            Section {
                Toggle(preferences.text("phone_fasting_eve"), isOn: $preferences.fastingAlerts.eve)
                if preferences.fastingAlerts.eve {
                    PrayerClockPicker(preferences: preferences, label: "phone_fasting_eve_time", minute: $preferences.fastingAlerts.eveMinute)
                }
                Toggle(preferences.text("phone_suhoor"), isOn: $preferences.fastingAlerts.suhoor)
                if preferences.fastingAlerts.suhoor {
                    Stepper(value: $preferences.fastingAlerts.suhoorMinutes, in: 5...120, step: 5) {
                        LabeledContent(preferences.text("phone_suhoor_minutes"),
                                       value: preferences.displayDigits(String(preferences.fastingAlerts.suhoorMinutes)))
                    }
                }
                Toggle(preferences.text("phone_iftar"), isOn: $preferences.fastingAlerts.iftar)
                if !preferences.locationConfirmed {
                    Text(preferences.text("phone_location_needed")).foregroundStyle(.secondary)
                }
                if notifications.permission == .denied {
                    Text(preferences.text("phone_permission_missing")).foregroundStyle(.secondary)
                }
            }
            Section(preferences.text("phone_fasting_days")) {
                Toggle(preferences.text("phone_fasting_ramadan"), isOn: $preferences.fastingAlerts.ramadan)
                Toggle(preferences.text("phone_fasting_ashura"), isOn: $preferences.fastingAlerts.ashura)
                Toggle(preferences.text("phone_fasting_arafah"), isOn: $preferences.fastingAlerts.arafah)
                Toggle(preferences.text("phone_fasting_white_days"), isOn: $preferences.fastingAlerts.whiteDays)
                Toggle(preferences.text("phone_fasting_monday_thursday"), isOn: $preferences.fastingAlerts.mondayThursday)
            }
        }
        .navigationTitle(preferences.text("phone_fasting"))
        .onChange(of: [preferences.fastingAlerts.eve, preferences.fastingAlerts.suhoor, preferences.fastingAlerts.iftar]) { old, new in
            if zip(old, new).contains(where: { !$0.0 && $0.1 }) { Task { _ = await notifications.authorize() } }
        }
    }
}

struct PrayerClockPicker: View {
    let preferences: PhonePreferences
    let label: String
    @Binding var minute: Int

    var body: some View {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return DatePicker(preferences.text(label), selection: Binding(
            get: { Date(timeIntervalSince1970: Double(minute * 60)) },
            set: { date in minute = calendar.component(.hour, from: date) * 60 + calendar.component(.minute, from: date) }
        ), displayedComponents: .hourAndMinute)
        .environment(\.calendar, calendar)
        .environment(\.timeZone, calendar.timeZone)
    }
}
