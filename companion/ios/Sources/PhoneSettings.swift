import SwiftUI

struct PhoneSettings: View {
    @EnvironmentObject private var library: AdhanLibrary
    @EnvironmentObject private var audio: PhoneAdhan
    @EnvironmentObject private var radio: QuranRadio
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    @ObservedObject var notifications: PrayerNotifications
    let startSetup: () -> Void
    @State private var reset = false
    @State private var resetFailed = false

    var body: some View {
        Form {
            Section {
                NavigationLink(preferences.text("phone_calculation")) {
                    LocationSettings(preferences: $preferences, schedule: schedule)
                }
                NavigationLink(preferences.text("prayerOffsets")) {
                    PrayerOffsets(preferences: $preferences, schedule: schedule)
                }
                NavigationLink(preferences.text("phone_audio")) {
                    AlertSettings(preferences: $preferences, schedule: schedule, notifications: notifications)
                }
                NavigationLink(preferences.text("phone_fasting")) {
                    FastingSettings(preferences: $preferences, notifications: notifications)
                }
                if !preferences.setupFinished {
                    Button(preferences.text("setup_start"), action: startSetup)
                }
            }
            Section {
                NavigationLink(preferences.text("phone_appearance")) {
                    Form {
                        DisplaySettings(preferences: $preferences)
                        NavigationLink(preferences.text("appearancePreview")) {
                            PrayerHome(preferences: .constant(preferences), schedule: schedule)
                        }
                    }
                        .navigationTitle(preferences.text("phone_appearance"))
                }
                NavigationLink(preferences.text("aboutMiqat")) {
                    AboutMiqat(preferences: preferences)
                }
            }
            Section {
                Button(preferences.text("phone_reset"), role: .destructive) { reset = true }
            }
        }
        .navigationTitle(preferences.text("phone_settings"))
        .alert(preferences.text("phone_reset"), isPresented: $reset) {
            Button(preferences.text("phone_reset"), role: .destructive) {
                do {
                    audio.stop()
                    radio.stop()
                    try library.reset()
                    notifications.cancelTest()
                    try PhoneStore.shared.clearAdhanClaims()
                    preferences = PhonePreferences()
                } catch { resetFailed = true }
            }
            Button(preferences.text("cancel"), role: .cancel) {}
        } message: {
            Text(preferences.text("phone_reset_confirm"))
        }
        .alert(preferences.text("phone_save_failed"), isPresented: $resetFailed) {
            Button(preferences.text("done"), role: .cancel) {}
        }
    }
}

struct DisplaySettings: View {
    @Binding var preferences: PhonePreferences

    var body: some View {
        Picker(preferences.text("language"), selection: $preferences.language) {
            Text(preferences.text("system")).tag("system")
            Text("العربية").tag("ar")
            Text("English").tag("en")
        }
        Picker(preferences.text("phone_digits"), selection: $preferences.digits) {
            Text(preferences.arabic ? "حسب اللغة" : "Follow language").tag("auto")
            Text("123").tag("western")
            Text("١٢٣").tag("eastern")
        }
        Picker(preferences.text("phone_clock"), selection: $preferences.clock) {
            Text(preferences.displayDigits("12")).tag("12")
            Text(preferences.displayDigits("24")).tag("24")
        }
        Toggle(preferences.text("phone_home_background"), isOn: $preferences.showArtwork)
        Picker(preferences.text("phone_theme"), selection: $preferences.theme) {
            let names = PhoneAppearance.themeNames(preferences)
            ForEach(PhoneAppearance.catalog.themeOrder.indices, id: \.self) { index in
                let id = PhoneAppearance.catalog.themeOrder[index]
                Text(names.indices.contains(index) ? names[index] : id).tag(id)
            }
        }
        Picker(preferences.text("phone_background"), selection: $preferences.background) {
            ForEach(PhoneAppearance.catalog.backgrounds, id: \.self) { id in
                Text(PhoneAppearance.backgroundName(id, preferences: preferences)).tag(id)
            }
        }
        Stepper(value: $preferences.displaySize, in: 80...150, step: 10) {
            LabeledContent(preferences.text("phone_size"), value: preferences.displayDigits("\(preferences.displaySize)%"))
        }
        Toggle(preferences.text("phone_reduce_motion"), isOn: $preferences.reduceMotion)
    }
}

struct PrayerOffsets: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule

    var body: some View {
        let times = schedule.day(at: .now, prefs: preferences)
        Form {
            ForEach(preferences.prayerNames().indices, id: \.self) { index in
                Stepper(value: $preferences.offsets[index], in: -30...30) {
                    VStack(alignment: .leading) {
                        Text(preferences.prayerNames()[index])
                        Text(preferences.displayDigits(String(preferences.offsets[index]))
                             + (times.map { " · " + preferences.time($0[index]) } ?? ""))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(preferences.text("prayerOffsets"))
    }
}

struct AboutMiqat: View {
    let preferences: PhonePreferences

    var body: some View {
        List {
            let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
            LabeledContent(preferences.text("aboutVersion"), value: preferences.displayDigits(version))
            Text(preferences.text("aboutDeveloper"))
            Text(preferences.text("aboutSadaqah"))
            if let url = PhoneData.feedback["github"] {
                Link(preferences.text("aboutGithub"), destination: url)
            }
        }
        .navigationTitle(preferences.text("aboutMiqat"))
    }
}
