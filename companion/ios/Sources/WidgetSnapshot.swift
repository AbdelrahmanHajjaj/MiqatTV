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

    let days: [Day]
    let names: [String]
    let arabic: Bool
    let eastern: Bool
    let clock: String
    let timezone: String
    let colors: [String: String]
}
