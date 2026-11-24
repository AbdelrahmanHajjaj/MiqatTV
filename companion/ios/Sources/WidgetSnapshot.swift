import Foundation

struct WidgetSnapshot: Codable {
    static let appGroup = "group.tv.miqat.ios"
    static let widgetKind = "miqat.prayer"
    static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appending(path: "prayer-widget.json")
    }

    struct Day: Codable {
        let start: Date
        let times: [Date]?
        let hijri: String
        let gregorian: String
        var transitions: [Transition]?
    }

    struct Transition: Codable {
        let start: Date
        let nextIndex: Int
        let nextTime: Date
    }

    struct Palette: Codable {
        let id: String
        let name: String
        let colors: [String: String]
    }

    struct Scene: Codable {
        let id: String
        let name: String
    }

    let days: [Day]
    let names: [String]
    let arabic: Bool
    let eastern: Bool
    let clock: String
    let timezone: String
    let colors: [String: String]
    var palettes: [Palette]?
    var scenes: [Scene]?
    var phoneScene: String?
    var locationConfirmed: Bool?
    var locationMessage: String?
    var unavailableMessage: String?
    var refreshMessage: String?

    static func load() -> Self? {
        guard let url = file, let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(Self.self, from: data),
              snapshot.names.count == 6, snapshot.days.allSatisfy({ $0.times.map { $0.count == 6 } ?? true }) else { return nil }
        return snapshot
    }

    var locale: Locale {
        let language = arabic ? "ar-EG" : "en-US"
        return Locale(identifier: language + (eastern ? "-u-nu-arab" : "-u-nu-latn"))
    }

    private var calendar: Calendar {
        var result = Calendar(identifier: .gregorian)
        result.timeZone = TimeZone(identifier: timezone) ?? TimeZone(secondsFromGMT: 0)!
        return result
    }

    func day(at date: Date) -> Day? {
        let start = calendar.startOfDay(for: date)
        return days.first { $0.start == start }
    }

    func nextPrayer(at date: Date) -> (index: Int, time: Date)? {
        guard locationConfirmed == true, let day = day(at: date), day.times?.count == 6,
              let transition = day.transitions?.last(where: { $0.start <= date }),
              [0, 2, 3, 4, 5].contains(transition.nextIndex), transition.nextTime > date else { return nil }
        return (transition.nextIndex, transition.nextTime)
    }

    func needsRefresh(at date: Date) -> Bool {
        guard locationConfirmed == true else { return false }
        guard let current = day(at: date) else { return true }
        return current.start == days.last?.start && current.times != nil && nextPrayer(at: date) == nil
    }

    func previousPrayer(at date: Date, next: (index: Int, time: Date)) -> Date? {
        let current = day(at: date)
        if next.index == 0 && current?.times?.first == next.time {
            return calendar.date(byAdding: .day, value: -1, to: date).flatMap { day(at: $0)?.times?.last }
        }
        let index = next.index == 0 ? 5 : next.index == 2 ? 0 : next.index - 1
        guard let times = current?.times, times.indices.contains(index), times[index] < next.time else { return nil }
        return times[index]
    }

    func colors(for id: String?) -> [String: String] {
        guard let id, let palette = palettes?.first(where: { $0.id == id }) else { return colors }
        return colors.merging(palette.colors) { _, selected in selected }
    }

    func sceneID(for choice: String?) -> String? {
        let id = choice == "app" ? phoneScene : choice
        guard let id, id != "plain", scenes?.contains(where: { $0.id == id }) == true else { return nil }
        return id
    }

    static func opacity(percent: Int) -> Double {
        Double(min(100, max(0, percent))) / 100
    }
}
