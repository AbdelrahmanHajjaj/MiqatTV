import Foundation

struct TVThemeDraft: Codable {
    let version: Int
    var colors: [String: String]?
    var background: String?
    var lighting: Int?
    var surface: String?

    static func read(_ value: String) -> Self? {
        if value.isEmpty {
            return Self(version: 1, colors: nil, background: nil, lighting: nil, surface: nil)
        }
        let data = Data(value.utf8)
        guard data.count <= 8192,
              let fields = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              Set(fields.keys).isSubset(of: ["version", "colors", "background", "lighting", "surface"]),
              fields.values.allSatisfy({ !($0 is NSNull) }),
              let theme = try? JSONDecoder().decode(Self.self, from: data),
              theme.version == 1 else { return nil }
        return theme
    }

    func changes(keys: [String], backgrounds: [String], surfaces: [String], maximum: Int) -> [String: String]? {
        guard version == 1,
              (colors ?? [:]).allSatisfy({ key, value in
                  keys.contains(key) && value.utf8.count == 7
                      && value.range(of: "^#[0-9a-fA-F]{6}$", options: .regularExpression) != nil
              }),
              background.map({ $0 != "theme" && backgrounds.contains($0) }) ?? true,
              lighting.map({ (0...100).contains($0) }) ?? true,
              surface.map(surfaces.contains) ?? true,
              let data = try? JSONEncoder().encode(self), data.count <= maximum else { return nil }
        return ["appearance.custom-theme": String(decoding: data, as: UTF8.self), "appearance.theme": "custom"]
    }
}
