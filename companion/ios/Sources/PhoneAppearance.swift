import SwiftUI
import ImageIO
import UIKit

struct PhoneAppearance: Decodable {
    struct Theme: Decodable {
        let colors: [String: String]
        let background: String?
        let lighting: Int?
        let surface: String?
    }

    let themes: [String: Theme]
    let backgrounds: [String]
    let themeOrder: [String]
    static let catalog: Self = PhoneData.load("phone-appearance")

    static func color(_ role: String, preferences: PhonePreferences) -> Color {
        let theme = catalog.themes[preferences.theme] ?? catalog.themes["mihrab"]
        return color(theme?.colors[role]) ?? .black
    }

    static func color(_ hex: String?) -> Color? {
        guard let hex, hex.hasPrefix("#"), hex.count == 7,
              let value = UInt32(hex.dropFirst(), radix: 16) else { return nil }
        return Color(red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255,
                     blue: Double(value & 255) / 255)
    }

    static func themeNames(_ preferences: PhonePreferences) -> [String] {
        PhoneData.list("themeNames", arabic: preferences.arabic)
    }

    static func themeName(_ id: String, preferences: PhonePreferences) -> String {
        let names = themeNames(preferences)
        guard let index = catalog.themeOrder.firstIndex(of: id), names.indices.contains(index) else { return id }
        return names[index]
    }

    static func backgroundName(_ id: String, preferences: PhonePreferences) -> String {
        if id == "plain" { return preferences.arabic ? "بلا خلفية" : "Plain" }
        let names = PhoneData.list("backgroundNames", arabic: preferences.arabic)
        guard let index = catalog.backgrounds.firstIndex(of: id), index > 0,
              names.count >= catalog.backgrounds.count - 1 else { return id }
        return names[names.count - (catalog.backgrounds.count - index)]
    }
}

struct PhoneBackdrop: View {
    let ground: Color
    let scene: String
    let shade: Double
    var portrait = true
    var maxPixelSize = 1600
    private static let images = NSCache<NSString, UIImage>()

    var body: some View {
        GeometryReader { frame in
            ZStack {
                ground
                if let image = artwork() {
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(width: frame.size.width, height: frame.size.height)
                        .clipped()
                    ground.opacity(shade)
                }
            }
        }
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private func artwork() -> UIImage? {
        guard scene != "plain", PhoneAppearance.catalog.backgrounds.contains(scene) else { return nil }
        let ext = scene == "miqat_signature" && portrait ? "png" : "jpg"
        let key = "\(scene).\(ext):\(maxPixelSize)" as NSString
        if let cached = Self.images.object(forKey: key) { return cached }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        guard let url = Bundle.main.url(forResource: scene, withExtension: ext),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let result = UIImage(cgImage: image)
        Self.images.totalCostLimit = 32 * 1024 * 1024
        Self.images.setObject(result, forKey: key, cost: image.bytesPerRow * image.height)
        return result
    }
}

struct PhoneDisplayScale: ViewModifier {
    @Environment(\.dynamicTypeSize) private var systemSize
    @Environment(\.accessibilityReduceMotion) private var systemReducesMotion
    let preferences: PhonePreferences

    func body(content: Content) -> some View {
        let percent = min(150, max(80, preferences.displaySize))
        let adjusted = Self.scaledSize(from: systemSize, percent: percent)
        content
            .dynamicTypeSize(systemSize.isAccessibilitySize ? max(adjusted, systemSize) : adjusted)
            .transaction {
                if systemReducesMotion || preferences.reduceMotion {
                    $0.animation = nil
                    $0.disablesAnimations = true
                }
            }
    }

    private static func scaledSize(from system: DynamicTypeSize, percent: Int) -> DynamicTypeSize {
        let sizes: [(DynamicTypeSize, UIContentSizeCategory)] = [
            (.xSmall, .extraSmall), (.small, .small), (.medium, .medium), (.large, .large),
            (.xLarge, .extraLarge), (.xxLarge, .extraExtraLarge), (.xxxLarge, .extraExtraExtraLarge),
            (.accessibility1, .accessibilityMedium), (.accessibility2, .accessibilityLarge),
            (.accessibility3, .accessibilityExtraLarge), (.accessibility4, .accessibilityExtraExtraLarge),
            (.accessibility5, .accessibilityExtraExtraExtraLarge),
        ]
        guard percent != 100, let current = sizes.firstIndex(where: { $0.0 == system }) else { return system }
        let points = sizes.map { size in
            UIFont.preferredFont(forTextStyle: .body,
                                 compatibleWith: UITraitCollection(preferredContentSizeCategory: size.1)).pointSize
        }
        let target = points[current] * CGFloat(percent) / 100
        let nearest = points.indices.min(by: { abs(points[$0] - target) < abs(points[$1] - target) }) ?? current
        return sizes[nearest].0
    }
}
