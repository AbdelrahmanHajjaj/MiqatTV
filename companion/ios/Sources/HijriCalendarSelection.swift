import Foundation

struct HijriCalendarSelection {
    let civil: Calendar
    let hijri: Calendar
    let correction: Int
    private let opened: Date
    private(set) var selected: Date
    private(set) var month: Date

    init(opened: Date, civil: Calendar, hijri: Calendar, correction: Int, selectedDay: Date? = nil) {
        self.civil = civil
        self.hijri = hijri
        self.correction = correction
        let noon = civil.date(bySettingHour: 12, minute: 0, second: 0, of: opened) ?? opened
        self.opened = noon
        selected = noon
        month = civil.date(byAdding: .day, value: correction, to: noon) ?? noon
        if let selectedDay { select(selectedDay, followMonth: true) }
    }

    var selectedHijri: Date { civil.date(byAdding: .day, value: correction, to: selected) ?? selected }

    var days: [Date] {
        guard let interval = hijri.dateInterval(of: .month, for: month),
              let count = hijri.range(of: .day, in: .month, for: month)?.count else { return [] }
        let noon = civil.date(bySettingHour: 12, minute: 0, second: 0, of: interval.start) ?? interval.start
        return (0..<count).compactMap { civil.date(byAdding: .day, value: $0, to: noon) }
    }

    func civilDay(_ corrected: Date) -> Date {
        civil.date(byAdding: .day, value: -correction, to: corrected) ?? corrected
    }

    func canSelect(_ day: Date) -> Bool {
        let distance = civil.dateComponents([.day], from: civil.startOfDay(for: opened),
                                            to: civil.startOfDay(for: day)).day
        return distance.map { (-366...366).contains($0) } ?? false
    }

    mutating func select(_ day: Date, followMonth: Bool = false) {
        guard canSelect(day) else { return }
        selected = civil.date(bySettingHour: 12, minute: 0, second: 0, of: day) ?? day
        if followMonth { month = selectedHijri }
    }

    func nextDay(_ direction: Int) -> Date? {
        civil.date(byAdding: .day, value: direction, to: selected).flatMap { canSelect($0) ? $0 : nil }
    }

    func nextMonth(_ direction: Int) -> Date? {
        guard let start = hijri.dateInterval(of: .month, for: month)?.start,
              let next = hijri.date(byAdding: .month, value: direction, to: start),
              let interval = hijri.dateInterval(of: .month, for: next) else { return nil }
        let boundary = direction < 0 ? civil.date(byAdding: .day, value: -1, to: interval.end) : interval.start
        guard let boundary, canSelect(civilDay(boundary)) else { return nil }
        return next
    }

    mutating func moveMonth(_ direction: Int) {
        if let next = nextMonth(direction) { month = next }
    }
}
