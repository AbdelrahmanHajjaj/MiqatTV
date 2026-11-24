import Foundation

struct AndroidTVSetupProvider {
    private static let prefix = "content://tv.miqat.home.setup/"
    let shell: @Sendable (String) async throws -> String

    func query<Value: Decodable>(_ type: Value.Type, path: String) async throws -> Value {
        let result = try await shell("content query --uri \(Self.prefix)\(path)")
        let row = "Row: 0 json="
        guard result.hasPrefix(row) else { throw AndroidTVPersonalization.Failure.unavailable }
        do { return try JSONDecoder().decode(type, from: Data(result.dropFirst(row.count).utf8)) }
        catch { throw AndroidTVPersonalization.Failure.unavailable }
    }

    fileprivate func update(_ edits: [(name: String, value: String)]) async throws {
        guard !edits.isEmpty else { return }
        let commands = edits.map { edit in
            let value = "'" + edit.value.replacingOccurrences(of: "'", with: "'\\''") + "'"
            return "content insert --uri \(Self.prefix)settings --bind name:s:\(edit.name) --bind value:s:\(value)"
        }
        _ = try await shell(commands.joined(separator: " && "))
    }
}

struct AndroidTVPersonalization {
    enum Failure: Error, Equatable { case unavailable, changed, invalid, unconfirmed }

    struct Info: Decodable, Sendable {
        let api: Int
        let favouriteSlots: Int
        let city: String
        let locale: String
        let adhan: Bool
        let remindVoice: Bool

        enum CodingKeys: String, CodingKey {
            case api, city, locale, adhan
            case favouriteSlots = "favourite_slots", remindVoice = "remind_voice"
        }
    }

    struct City: Decodable, Identifiable, Sendable {
        let id: String
        let en: String
        let ar: String
        let countryEn: String
        let countryAr: String
    }

    struct App: Decodable, Identifiable, Sendable {
        let p: String
        let en: String
        let ar: String
        let fav: Int
        var id: String { p }
    }

    struct Draft: Equatable, Sendable {
        var city: String
        var locale: String
        var adhan: Bool
        var remindVoice: Bool
        var favourites: [String]
    }

    struct Snapshot: Identifiable, Sendable {
        let id = UUID()
        let cities: [City]
        let apps: [App]
        let minimum: Int
        let maximum: Int
        let current: Draft
    }

    static let locales = ["system", "en", "ar"]
    let provider: AndroidTVSetupProvider

    func load() async throws -> Snapshot {
        let info = try await provider.query(Info.self, path: "info")
        guard (1...2).contains(info.api), Self.locales.contains(info.locale), (4...9).contains(info.favouriteSlots) else {
            throw Failure.unavailable
        }
        let cities = try await provider.query([City].self, path: "cities")
        let apps = try await provider.query([App].self, path: "apps")
        let cityIDs = Set(cities.map(\.id))
        let appIDs = Set(apps.map(\.p))
        let favourites = apps.filter { $0.fav >= 0 }.sorted { $0.fav < $1.fav }
        let minimum = min(4, apps.count)
        let maximum = min(info.favouriteSlots, apps.count)
        guard cityIDs.count == cities.count, cityIDs.contains(info.city), appIDs.count == apps.count,
              cityIDs.allSatisfy({ Self.validID($0, pattern: #"^[a-zA-Z0-9_-]+$"#) }),
              appIDs.allSatisfy({ Self.validID($0, pattern: #"^[a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+$"#) }),
              (minimum...maximum).contains(favourites.count),
              favourites.map(\.fav) == Array(0..<favourites.count) else { throw Failure.unavailable }
        let draft = Draft(city: info.city, locale: info.locale, adhan: info.adhan, remindVoice: info.remindVoice,
                          favourites: favourites.map(\.p))
        return Snapshot(cities: cities, apps: apps, minimum: minimum, maximum: maximum, current: draft)
    }

    func save(_ draft: Draft, from baseline: Snapshot) async throws {
        let current = try await load()
        guard current.current == baseline.current else { throw Failure.changed }
        guard current.cities.contains(where: { $0.id == draft.city }), Self.locales.contains(draft.locale),
              (current.minimum...current.maximum).contains(draft.favourites.count),
              Set(draft.favourites).count == draft.favourites.count,
              Set(draft.favourites).isSubset(of: Set(current.apps.map(\.p))) else { throw Failure.invalid }

        let original = baseline.current
        var edits: [(name: String, value: String)] = []
        if draft.city != original.city { edits.append(("city", draft.city)) }
        if draft.locale != original.locale { edits.append(("locale", draft.locale)) }
        if draft.adhan != original.adhan { edits.append(("adhan", draft.adhan ? "on" : "off")) }
        if draft.remindVoice != original.remindVoice { edits.append(("remind_voice", draft.remindVoice ? "on" : "off")) }
        if draft.favourites != original.favourites { edits.append(("favourites", draft.favourites.joined(separator: ","))) }
        guard !edits.isEmpty else { return }

        try await provider.update(edits)
        let applied = try await load()
        guard applied.current == draft else { throw Failure.unconfirmed }
    }

    private static func validID(_ value: String, pattern: String) -> Bool {
        value.utf8.count <= 255 && !value.contains(where: \.isNewline)
            && value.range(of: pattern, options: .regularExpression) != nil
    }
}
