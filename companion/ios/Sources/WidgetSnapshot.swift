import Foundation

struct WidgetSnapshot: Codable {
    static let appGroup = "group.tv.miqat.ios"
    static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appending(path: "prayer-widget.json")
    }

    struct Day: Codable {
        let start: Date
        let times: [Date]
        let hijri: String
        let gregorian: String
    }

    struct Palette: Codable {
        let id: String
        let name: String
        let colors: [String: String]
    }

    let days: [Day]
    let names: [String]
    let arabic: Bool
    let eastern: Bool
    let clock: String
    let timezone: String
    let colors: [String: String]
    var palettes: [Palette]?

    static func load() -> Self? {
        guard let url = file, let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(Self.self, from: data),
              snapshot.names.count == 6, snapshot.days.allSatisfy({ $0.times.count == 6 }) else { return nil }
        return snapshot
    }

    func colors(for id: String?) -> [String: String] {
        guard let id, let palette = palettes?.first(where: { $0.id == id }) else { return colors }
        return colors.merging(palette.colors) { _, selected in selected }
    }

    static func opacity(percent: Int) -> Double {
        Double(min(100, max(0, percent))) / 100
    }
}
