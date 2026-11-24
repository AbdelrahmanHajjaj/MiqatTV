import AppIntents

struct PrayerWidgetPalette: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Theme")
    static let defaultQuery = PrayerWidgetPaletteQuery()
    static let phone = Self(id: "app", name: "")

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation {
        if id == Self.phone.id { return DisplayRepresentation(title: "Use phone theme") }
        return DisplayRepresentation(title: "\(name)")
    }
}

struct PrayerWidgetPaletteQuery: EntityQuery {
    private func choices() -> [PrayerWidgetPalette] {
        let saved = WidgetSnapshot.load()?.palettes ?? []
        return [.phone] + saved.map { .init(id: $0.id, name: $0.name) }
    }

    func suggestedEntities() async throws -> [PrayerWidgetPalette] { choices() }

    func entities(for identifiers: [String]) async throws -> [PrayerWidgetPalette] {
        let available = choices()
        return identifiers.compactMap { id in available.first { $0.id == id } }
    }

    func defaultResult() async -> PrayerWidgetPalette? { .phone }
}
