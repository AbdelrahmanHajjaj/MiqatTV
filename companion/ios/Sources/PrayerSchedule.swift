import Foundation
import JavaScriptCore
import MiqatKit
import WidgetKit

final class PrayerSchedule {
    static let shared = PrayerSchedule()
    private let js = JSContext()!
    private var lastDay: Date?
    private var lastLocation: PhonePreferences?
    private var cachedTimes: [Date]?

    init() {
        for name in ["adhan", "Astronomy"] {
            let url = Bundle.main.url(forResource: name, withExtension: "js")!
            js.evaluateScript(try! String(contentsOf: url, encoding: .utf8), withSourceURL: url)
        }
    }

    static func civilCalendar(_ prefs: PhonePreferences) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: prefs.arabic ? "ar" : "en")
        calendar.timeZone = prefs.zone
        return calendar
    }

    static func hijriCalendar(_ prefs: PhonePreferences) -> Calendar {
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.timeZone = prefs.zone
        calendar.locale = Locale(identifier: prefs.arabic ? "ar" : "en")
        return calendar
    }

    func day(at date: Date, prefs: PhonePreferences) -> [Date]? {
        let calendar = Self.civilCalendar(prefs)
        let start = calendar.startOfDay(for: date)
        if start == lastDay, prefs == lastLocation { return cachedTimes }
        lastDay = start
        lastLocation = prefs
        cachedTimes = nil
        let civil = calendar.dateComponents([.year, .month, .day], from: date)
        let method = PhoneData.methods.first { $0.id == prefs.method } ?? PhoneData.methods[0]
        guard prefs.valid, let year = civil.year, let month = civil.month, let day = civil.day,
              let encoded = try? JSONEncoder().encode(method),
              let definition = try? JSONSerialization.jsonObject(with: encoded),
              let values = js.objectForKeyedSubscript("miqatPrayerTimes")?.call(withArguments: [
                year, month, day, prefs.coordinates.latitude, prefs.coordinates.longitude,
                definition, prefs.hanafi, prefs.highLatitude, prefs.offsets,
              ])?.toArray() as? [NSNumber],
              values.count == 6, values.allSatisfy({ $0.doubleValue.isFinite }) else { return nil }
        let rounded = values.map {
            Date(timeIntervalSince1970: PrayerRules.shared.floorToMinute(epochMilliseconds: $0.doubleValue) / 1000)
        }
        cachedTimes = rounded
        return rounded
    }

    func current(at date: Date, prefs: PhonePreferences) -> DisplayDay {
        let calendar = Self.civilCalendar(prefs)
        if let current = displayDay, current.start == calendar.startOfDay(for: date), displayPreferences == prefs {
            return current
        }
        let today = day(at: date, prefs: prefs)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: date).flatMap { day(at: $0, prefs: prefs) }
        let result = DisplayDay(start: calendar.startOfDay(for: date), today: today, tomorrow: tomorrow)
        displayDay = result
        displayPreferences = prefs
        return result
    }

    private var displayDay: DisplayDay?
    private var displayPreferences: PhonePreferences?

    func publishWidget(preferences: PhonePreferences) {
        guard let file = WidgetSnapshot.file else { return }
        let calendar = Self.civilCalendar(preferences)
        let today = calendar.startOfDay(for: .now)
        var days = (-1...8).compactMap { offset -> WidgetSnapshot.Day? in
            guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { return nil }
            let times = preferences.locationConfirmed ? day(at: date, prefs: preferences) : nil
            return .init(start: date, times: times, hijri: preferences.hijriDate(date), gregorian: preferences.gregorianDate(date))
        }
        for index in days.indices {
            let current = days[index]
            guard let times = current.times else { continue }
            let tomorrow = days.indices.contains(index + 1) ? days[index + 1].times : nil
            let display = DisplayDay(start: current.start, today: times, tomorrow: tomorrow)
            let starts = Set([current.start] + times.filter { $0 >= current.start && calendar.isDate($0, inSameDayAs: current.start) })
            days[index].transitions = starts.sorted().compactMap { date in
                guard let next = display.next(at: date) else { return nil }
                return .init(start: date, nextIndex: Int(next.next), nextTime: Date(timeIntervalSince1970: next.nextTime / 1000))
            }
        }
        let appearance = PhoneAppearance.catalog
        let names = PhoneAppearance.themeNames(preferences)
        let palettes = appearance.themeOrder.enumerated().compactMap { index, id -> WidgetSnapshot.Palette? in
            guard let theme = appearance.themes[id] else { return nil }
            return .init(id: id, name: names.indices.contains(index) ? names[index] : id, colors: theme.colors)
        }
        let colors = appearance.themes[preferences.theme]?.colors ?? appearance.themes["mihrab"]?.colors ?? [:]
        let scenes = appearance.backgrounds.filter { $0 != "plain" }.map { id in
            WidgetSnapshot.Scene(id: id, name: PhoneAppearance.backgroundName(id, preferences: preferences))
        }
        let snapshot = WidgetSnapshot(days: days, names: preferences.prayerNames(), arabic: preferences.arabic,
            eastern: preferences.digits == "eastern" || (preferences.digits == "auto" && preferences.arabic),
            clock: preferences.clock, timezone: preferences.zone.identifier, colors: colors, palettes: palettes,
            scenes: scenes, phoneScene: preferences.background, locationConfirmed: preferences.locationConfirmed,
            locationMessage: preferences.text("phone_location_needed"), unavailableMessage: preferences.text("phone_unavailable"))
        do {
            let data = try JSONEncoder().encode(snapshot)
            try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            var localFile = file
            var attributes = URLResourceValues()
            attributes.isExcludedFromBackup = true
            try localFile.setResourceValues(attributes)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetSnapshot.widgetKind)
        } catch {
            // Widget continues using the last complete snapshot.
        }
    }

    struct DisplayDay {
        let start: Date
        let today: [Date]?
        let tomorrow: [Date]?

        func next(at date: Date) -> PrayerSelection? {
            PhonePrayerRules.shared.nextPrayer(
                today: today.map(PrayerSchedule.kotlinTimes), tomorrow: tomorrow.map(PrayerSchedule.kotlinTimes),
                now: date.timeIntervalSince1970 * 1000
            )
        }

    }

    static func kotlinTimes(_ dates: [Date]) -> KotlinDoubleArray {
        let result = KotlinDoubleArray(size: Int32(dates.count))
        for (index, date) in dates.enumerated() {
            result.set(index: Int32(index), value: date.timeIntervalSince1970 * 1000)
        }
        return result
    }
}

extension PhonePreferences {
    func time(_ date: Date) -> String {
        let format = DateFormatter()
        format.locale = Locale(identifier: arabic ? "ar-EG" : "en-US")
        format.timeZone = zone
        format.dateFormat = clock == "24" ? "HH:mm" : "h:mm a"
        return displayDigits(format.string(from: date))
    }

    func gregorianDate(_ date: Date) -> String {
        let format = DateFormatter()
        format.locale = Locale(identifier: arabic ? "ar-EG" : "en")
        format.calendar = PrayerSchedule.civilCalendar(self)
        format.timeZone = zone
        format.setLocalizedDateFormatFromTemplate("EEE d MMM y")
        return displayDigits(format.string(from: date))
    }

    func hijriDate(_ date: Date) -> String {
        let calendar = PrayerSchedule.hijriCalendar(self)
        let corrected = calendar.date(byAdding: .day, value: hijriOffset, to: date) ?? date
        let parts = calendar.dateComponents([.year, .month, .day], from: corrected)
        let months = PhoneData.list("hijriMonths", arabic: arabic)
        guard let day = parts.day, let month = parts.month, let year = parts.year,
              months.indices.contains(month - 1) else { return "" }
        return displayDigits("\(day) \(months[month - 1]) \(year)\(text("hijriSuffix"))")
    }
}
