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

    func sceneID(for choice: String?) -> String? {
        let id = choice == "app" ? phoneScene : choice
        guard let id, id != "plain", scenes?.contains(where: { $0.id == id }) == true else { return nil }
        return id
    }

    static func opacity(percent: Int) -> Double {
        Double(min(100, max(0, percent))) / 100
    }
}
