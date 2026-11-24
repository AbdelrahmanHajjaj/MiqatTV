import Combine
import Foundation
import MiqatKit
import UserNotifications

struct PrayerAlert: Codable, Equatable {
    var enabled = true
    var mode = 2
    var before = 0
    var after = 0
    var days = 127
    var dayModes = Array(repeating: -1, count: 7)
    var useDefaultAudio = true
    var audio = AdhanSelection(voice: "inherit")

    var valid: Bool {
        (0...4).contains(mode) && (0...60).contains(before) && (0...60).contains(after)
            && (0...127).contains(days) && dayModes.count == 7 && dayModes.allSatisfy { (-1...4).contains($0) }
    }
}

struct QuietHours: Codable, Equatable {
    var enabled = false
    var start = 1320
    var end = 420
    var mode = 4

    var valid: Bool { (0...1439).contains(start) && (0...1439).contains(end) && [0, 3, 4].contains(mode) }
}

struct FastingAlerts: Codable, Equatable {
    var eve = false
    var eveMinute = 1200
    var suhoor = false
    var suhoorMinutes = 30
    var iftar = false
    var ramadan = true
    var ashura = false
    var arafah = false
    var whiteDays = false
    var mondayThursday = false

    var enabled: Bool { eve || suhoor || iftar }
    var valid: Bool { (5...120).contains(suhoorMinutes) && (0...1439).contains(eveMinute) }
}

@MainActor
final class PrayerNotifications: ObservableObject {
    static let shared = PrayerNotifications()
    @Published private(set) var permission = UNAuthorizationStatus.notDetermined
    @Published private(set) var scheduleFailed = false
    @Published private(set) var alertSoundsBlocked = false
    @Published private(set) var testDate: Date?
    @Published private(set) var schedulingTest = false
    @Published private(set) var testFailed = false
    private let center = UNUserNotificationCenter.current()
    private var pending: (id: UUID, work: Task<Bool, Never>)?
    private var testCancelled = false
    private static let schedulePrefix = "miqat.schedule."

    var permissionMissing: Bool { permission == .denied || alertSoundsBlocked }

    func authorize() async -> Bool {
        do {
            let allowed = try await center.requestAuthorization(options: [.alert, .sound])
            permission = await center.notificationSettings().authorizationStatus
            return allowed
        } catch {
            return false
        }
    }

    @discardableResult
    func refresh(preferences: PhonePreferences, schedule: PrayerSchedule) -> Task<Bool, Never> {
        let previous = pending?.work
        previous?.cancel()
        let id = UUID()
        let work = Task { [weak self] in
            _ = await previous?.value
            guard let self else { return false }
            defer { if self.pending?.id == id { self.pending = nil } }
            guard !Task.isCancelled else { return false }
            return await self.reconcile(preferences: preferences, schedule: schedule)
        }
        pending = (id, work)
        return work
    }

    func cancelTest() {
        testCancelled = true
        testDate = nil
        testFailed = false
        removeTestNotifications()
    }

    private func removeTestNotifications() {
        center.removePendingNotificationRequests(withIdentifiers: [AdhanNotification.testID])
        center.removeDeliveredNotifications(withIdentifiers: [AdhanNotification.testID])
    }

    func startTest(preferences: PhonePreferences, adhan: Bool) {
        guard !schedulingTest, testDate.map({ $0 <= .now }) != false else { return }
        cancelTest()
        testCancelled = false
        schedulingTest = true
        Task { await scheduleTest(preferences: preferences, adhan: adhan) }
    }

    private func scheduleTest(preferences: PhonePreferences, adhan: Bool) async {
        defer { schedulingTest = false }
        guard !testCancelled else { return }
        let allowed = await authorize()
        guard !testCancelled, !Task.isCancelled else { return }
        guard allowed else { testFailed = true; return }
        let audible = await center.notificationSettings().soundSetting == .enabled
        guard !testCancelled, !Task.isCancelled else { return }
        let when = Date.now.addingTimeInterval(30)
        let content = UNMutableNotificationContent()
        content.title = preferences.text(adhan ? "phone_test_adhan" : "phone_test_notification")
        content.sound = PrayerNotificationRequest.shortSound
        if adhan && audible {
            content.userInfo["adhanPrayer"] = 2
            content.userInfo["adhanTime"] = when.timeIntervalSince1970 * 1000
        }
        do {
            try await center.add(UNNotificationRequest(identifier: AdhanNotification.testID, content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 30, repeats: false)))
            if testCancelled || Task.isCancelled { removeTestNotifications(); return }
            testDate = when
        } catch {
            if !testCancelled && !Task.isCancelled { testFailed = true }
        }
    }

    func claimAdhan(_ request: AdhanNotification, preferences: PhonePreferences, schedule: PrayerSchedule) -> Bool {
        let now = Date.now
        let rules = PhonePrayerRules.shared
        guard rules.due(time: request.time, now: now.timeIntervalSince1970 * 1000) else { return false }
        if request.isTest {
            guard request.prayer == 2, !testCancelled, !schedulingTest,
                  testDate.map({ $0.timeIntervalSince1970 * 1000 == request.time }) != false else { return false }
        } else {
            guard preferences.locationConfirmed, preferences.alertsEnabled, preferences.valid else { return false }
            let start = Date(timeIntervalSince1970: request.time / 1000 - 1)
            let valid = Self.plans(preferences, schedule: schedule, now: start, days: -1...1).contains { plan in
                plan.fullAdhan && plan.event.time == request.time && Int(plan.event.prayer) == request.prayer
                    && Self.schedulePrefix + plan.event.id == request.identifier
            }
            guard valid else { return false }
        }
        return PhoneStore.shared.claimAdhan(request.claimID)
    }

    private func reconcile(preferences: PhonePreferences, schedule: PrayerSchedule) async -> Bool {
        let permissions = await center.notificationSettings()
        let existing = await center.pendingNotificationRequests()
        let hasWidget: Bool
        if preferences.locationConfirmed && !Task.isCancelled {
            hasWidget = await PrayerBackgroundRefresh.hasPrayerWidget()
        } else {
            hasWidget = false
        }
        guard !Task.isCancelled else { return false }
        permission = permissions.authorizationStatus
        let ours = existing.filter { $0.identifier.hasPrefix(Self.schedulePrefix) }
        guard preferences.locationConfirmed, preferences.valid,
              [.authorized, .provisional, .ephemeral].contains(permissions.authorizationStatus) else {
            center.removePendingNotificationRequests(withIdentifiers: ours.map(\.identifier))
            PrayerBackgroundRefresh.schedule(enabled: preferences.valid && hasWidget)
            scheduleFailed = false
            alertSoundsBlocked = false
            return true
        }

        let now = Date.now
        PrayerBackgroundRefresh.schedule(enabled: hasWidget || preferences.alertsEnabled || preferences.fastingAlerts.enabled)
        let requests = Self.plans(preferences, schedule: schedule, now: now).prefix(60).map { plan in
            PrayerNotificationRequest(id: Self.schedulePrefix + plan.event.id, time: plan.event.time,
                title: plan.title, body: plan.body, audible: plan.audible,
                adhanPrayer: plan.fullAdhan && permissions.soundSetting == .enabled ? Int(plan.event.prayer) : nil)
        }
        alertSoundsBlocked = permissions.soundSetting == .disabled && requests.contains(where: \.audible)
        let ids = Set(requests.map(\.id))
        let existingByID = Dictionary(ours.map { ($0.identifier, $0) }, uniquingKeysWith: { first, _ in first })
        center.removePendingNotificationRequests(withIdentifiers: existingByID.keys.filter { !ids.contains($0) })
        scheduleFailed = false
        for request in requests {
            guard !Task.isCancelled else { return false }
            if let pending = existingByID[request.id], request.matches(pending) { continue }
            guard request.date > .now else { continue }
            do {
                try await center.add(request.makeRequest())
            } catch {
                scheduleFailed = true
            }
        }
        return !scheduleFailed
    }

    private struct Plan {
        let event: ReminderEvent
        let title: String
        let body: String
        let audible: Bool
        var fullAdhan = false
    }

    private static func plans(_ prefs: PhonePreferences, schedule: PrayerSchedule, now: Date,
                              days: ClosedRange<Int> = -1...7) -> [Plan] {
        let calendar = PrayerSchedule.civilCalendar(prefs)
        let today = calendar.startOfDay(for: now)
        let nowMs = now.timeIntervalSince1970 * 1000
        var plans: [Plan] = []
        for offset in days {
            guard let date = calendar.prayerDayStart(adding: offset, to: today) else { continue }
            let times = schedule.day(at: date, prefs: prefs)
            let weekday = calendar.component(.weekday, from: date) - 1
            if prefs.alertsEnabled, let times {
                let alerts = prefs.prayerAlerts
                let modes = alerts.enumerated().map { index, setting -> Int in
                    index != 1 && setting.enabled ? setting.mode : 0
                }
                let events = PhonePrayerRules.shared.futureDayEvents(times: PrayerSchedule.kotlinTimes(times), now: nowMs,
                    modes: kotlinInts(modes), before: kotlinInts(alerts.map(\.before)),
                    after: kotlinInts(alerts.map(\.after)))
                for event in events {
                    let index = Int(event.prayer)
                    let alert = alerts[index]
                    let timing = PrayerNotificationRequest.localTiming(prayerTime: event.prayerTime,
                        eventTime: event.time, calendar: calendar)
                    let dayMode = PhonePrayerRules.shared.effectiveMode(mode: Int32(alert.mode),
                        enabled: alert.enabled, dayMode: Int32(alert.dayModes[timing.weekday]))
                    let quiet = prefs.quietHours
                    let mode = ReminderRules.shared.alertMode(mode: Double(dayMode), weekday: Double(timing.weekday),
                        daysMask: Double(alert.days), minute: Double(timing.minute), quiet: quiet.enabled,
                        start: Double(quiet.start), end: Double(quiet.end), quietMode: Double(quiet.mode))
                    guard mode > 0 else { continue }
                    let key = event.kind == "before" ? "phone_advance_title" : event.kind == "after" ? "phone_after_title" : "phone_adhan_title"
                    let title = prefs.text(key).replacingOccurrences(of: "%s", with: prefs.prayerNames()[index])
                    let fullAdhan = mode == 2 && event.kind == "call"
                    let audible = mode == 1 || (mode == 2 && (!fullAdhan
                        || prefs.adhan.forPrayer(index, settings: alert).level > 0))
                    plans.append(.init(event: event, title: title, body: prefs.time(times[index]),
                                       audible: audible, fullAdhan: fullAdhan && audible))
                }
            }
            plans.append(contentsOf: fastingPlans(prefs, calendar: calendar, date: date, times: times, weekday: weekday)
                .filter { $0.event.time > nowMs })
        }
        return plans.sorted { $0.event.time == $1.event.time ? $0.event.id < $1.event.id : $0.event.time < $1.event.time }
    }

    private static func fastingPlans(_ prefs: PhonePreferences, calendar: Calendar, date: Date, times: [Date]?, weekday: Int) -> [Plan] {
        let options = prefs.fastingAlerts
        guard options.enabled,
              let corrected = calendar.date(byAdding: .day, value: prefs.hijriOffset, to: date) else { return [] }
        let hijri = PrayerSchedule.hijriCalendar(prefs).dateComponents([.month, .day], from: corrected)
        guard let month = hijri.month, let day = hijri.day else { return [] }
        let eve = options.eve ? calendar.fastingEve(before: date, minute: options.eveMinute) : nil
        let optionsCore = FastingPreferences(ramadan: options.ramadan, ashura: options.ashura, arafah: options.arafah,
            whiteDays: options.whiteDays, mondayThursday: options.mondayThursday,
            suhoorMinutes: options.suhoor ? Int32(options.suhoorMinutes) : 0, iftar: options.iftar,
            eveMinutes: options.eve ? KotlinInt(value: Int32(options.eveMinute)) : nil)
        let events = FastingRules.shared.events(month: Int32(month), day: Int32(day), weekday: Int32(weekday),
            fajr: times.map { $0[0].timeIntervalSince1970 * 1000 } ?? .nan,
            maghrib: times.map { $0[4].timeIntervalSince1970 * 1000 } ?? .nan,
            preferences: optionsCore, fastDayStart: Int64(date.timeIntervalSince1970 * 1000),
            eve: eve.map { KotlinLong(value: Int64($0.timeIntervalSince1970 * 1000)) })
        return events.map { event in
            if event.kind == "fasting-eve" {
                return .init(event: event, title: prefs.text("phone_fasting_eve_notice"), body: prefs.hijriDate(date), audible: true)
            }
            let suhoor = event.kind == "suhoor"
            let time = prefs.time(Date(timeIntervalSince1970: event.prayerTime / 1000))
            return .init(event: event, title: prefs.text(suhoor ? "phone_suhoor_notice" : "phone_iftar_notice"),
                         body: prefs.text(suhoor ? "phone_suhoor_time" : "phone_iftar_time").replacingOccurrences(of: "%s", with: time),
                         audible: true)
        }
    }

    private static func kotlinInts(_ values: [Int]) -> KotlinIntArray {
        let array = KotlinIntArray(size: Int32(values.count))
        for (index, value) in values.enumerated() { array.set(index: Int32(index), value: Int32(value)) }
        return array
    }
}
