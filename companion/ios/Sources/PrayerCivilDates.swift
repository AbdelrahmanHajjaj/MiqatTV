import Foundation

extension Calendar {
    func prayerDayStart(adding days: Int, to date: Date) -> Date? {
        self.date(byAdding: .day, value: days, to: date).map { startOfDay(for: $0) }
    }

    func fastingEve(before fastDay: Date, minute: Int) -> Date? {
        // Find the nominal previous civil date even when the local zone skipped it.
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        let fields: Set<Calendar.Component> = [.year, .month, .day]
        guard let nominal = utc.date(from: dateComponents(fields, from: fastDay)),
              let previous = utc.date(byAdding: .day, value: -1, to: nominal) else { return nil }
        let expectedDay = utc.dateComponents(fields, from: previous)
        guard let localDay = date(from: expectedDay), dateComponents(fields, from: localDay) == expectedDay else { return nil }
        let hour = minute / 60
        let minuteOfHour = minute % 60
        if let exact = date(bySettingHour: hour, minute: minuteOfHour, second: 0, of: localDay,
                            matchingPolicy: .strict, repeatedTimePolicy: .first), isDate(exact, inSameDayAs: localDay) {
            return exact
        }
        var requested = expectedDay
        requested.hour = hour
        requested.minute = minuteOfHour
        guard let shifted = date(from: requested), isDate(shifted, inSameDayAs: localDay) else { return nil }
        return shifted
    }
}
