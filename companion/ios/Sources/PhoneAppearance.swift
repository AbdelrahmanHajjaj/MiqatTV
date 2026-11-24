import SwiftUI

struct PhoneAppearance: Decodable {
    struct Theme: Decodable { let colors: [String: String] }

    let themes: [String: Theme]
    let backgrounds: [String]
    let themeOrder: [String]
    static let catalog: Self = PhoneData.load("phone-appearance")

    static func color(_ role: String, preferences: PhonePreferences) -> Color {
        let theme = catalog.themes[preferences.theme] ?? catalog.themes["mihrab"]
        let value = theme?.colors[role].flatMap { UInt32($0.dropFirst(), radix: 16) } ?? 0
        return Color(red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255,
                     blue: Double(value & 255) / 255)
    }

    static func themeNames(_ preferences: PhonePreferences) -> [String] {
        PhoneData.list("themeNames", arabic: preferences.arabic)
    }

    static func backgroundName(_ id: String, preferences: PhonePreferences) -> String {
        if id == "plain" { return preferences.arabic ? "بلا خلفية" : "Plain" }
        let names = PhoneData.list("backgroundNames", arabic: preferences.arabic)
        guard let index = catalog.backgrounds.firstIndex(of: id), index > 0,
              names.count >= catalog.backgrounds.count - 1 else { return id }
        return names[names.count - (catalog.backgrounds.count - index)]
    }
}

struct PhoneDisplayScale: ViewModifier {
    @Environment(\.dynamicTypeSize) private var systemSize
    let preferences: PhonePreferences

    func body(content: Content) -> some View {
        let sizes = DynamicTypeSize.allCases
        let current = sizes.firstIndex(of: systemSize) ?? 3
        let shift = (preferences.displaySize - 100) / 10
        let adjusted = sizes[min(sizes.count - 1, max(0, current + shift))]
        content
            .dynamicTypeSize(systemSize.isAccessibilitySize ? max(adjusted, systemSize) : adjusted)
            .transaction { if preferences.reduceMotion { $0.disablesAnimations = true } }
    }
}
