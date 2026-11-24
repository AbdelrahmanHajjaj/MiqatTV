import AppIntents
import ImageIO
import SwiftUI

struct PrayerWidgetScene: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Background scene")
    static let defaultQuery = PrayerWidgetSceneQuery()
    static let plain = Self(id: "plain", name: "")
    static let phone = Self(id: "app", name: "")

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation {
        switch id {
        case Self.plain.id: DisplayRepresentation(title: "Plain background")
        case Self.phone.id: DisplayRepresentation(title: "Use phone background")
        default: DisplayRepresentation(title: "\(name)")
        }
    }
}

struct PrayerWidgetSceneQuery: EntityQuery {
    private func choices() -> [PrayerWidgetScene] {
        let scenes = WidgetSnapshot.load()?.scenes ?? []
        return [.plain, .phone] + scenes.map { .init(id: $0.id, name: $0.name) }
    }

    func suggestedEntities() async throws -> [PrayerWidgetScene] { choices() }

    func entities(for identifiers: [String]) async throws -> [PrayerWidgetScene] {
        let available = choices()
        return identifiers.compactMap { id in available.first { $0.id == id } }
    }

    func defaultResult() async -> PrayerWidgetScene? { .plain }
}

struct PrayerWidgetBackdrop: View {
    let sceneID: String?
    let ground: Color
    let opacity: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                ground
                if opacity > 0, let sceneID, let image = thumbnail(sceneID) {
                    Image(decorative: image, scale: 1)
                        .resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                    LinearGradient(colors: [ground.opacity(0.6), ground.opacity(0.9)],
                                   startPoint: .top, endPoint: .bottom)
                }
            }
        }
        .opacity(opacity)
    }

    private func thumbnail(_ id: String) -> CGImage? {
        guard id.range(of: "^[a-z_]+$", options: .regularExpression) != nil,
              let url = Bundle.main.url(forResource: id, withExtension: "jpg", subdirectory: "backgrounds"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 768,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }
}
