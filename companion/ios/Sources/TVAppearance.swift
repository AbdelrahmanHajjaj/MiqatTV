import MiqatKit
import SwiftUI

struct TVAppearancePreview: View {
    let layout: TVPreviewLayout
    let colors: [String: String]
    let background: String?
    let lighting: Int?

    var body: some View {
        let ground = PhoneAppearance.color(colors["lapis-900"]) ?? .black
        let accent = PhoneAppearance.color(colors["gold-400"]) ?? .yellow
        ZStack {
            GeometryReader { area in
                if let background, PhoneAppearance.catalog.backgrounds.contains(background),
                   let url = Bundle.main.url(forResource: background, withExtension: "jpg"),
                   let image = UIImage(contentsOfFile: url.path) {
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(width: area.size.width, height: area.size.height)
                        .clipped()
                    ground.opacity(0.62)
                } else {
                    ground
                }
                LinearGradient(colors: [accent.opacity(Double(min(100, max(0, lighting ?? 35))) / 300), .clear],
                               startPoint: .topTrailing, endPoint: .bottomLeading)
            }
            Canvas { context, size in
                var context = context
                context.scaleBy(x: size.width / 640, y: size.height / 360)
                drawHome(context, ground: ground, accent: accent)
            }
        }
        .aspectRatio(16.0 / 9.0, contentMode: .fit)
        .clipShape(.rect(cornerRadius: 10))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(PhoneData.text("preview", arabic: layout.arabic))
    }

    private func drawHome(_ context: GraphicsContext, ground: Color, accent: Color) {
        let panel = PhoneAppearance.color(colors["lapis-800"]) ?? ground
        let ink = PhoneAppearance.color(colors["plaster-100"]) ?? .white
        let favourite = PhoneAppearance.color(colors["lapis-700"]) ?? panel
        let muted = PhoneAppearance.color(colors["mist-400"]) ?? ink
        let names = PhoneData.list("prayerNames", arabic: layout.arabic)
        let asr = names.indices.contains(3) ? names[3] : ""
        let clock = layout.twelveHourClock ? (layout.arabic ? "2:30 م" : "2:30 PM") : "14:30"
        func label(_ value: String, x: CGFloat, y: CGFloat, size: CGFloat, color: Color) {
            let digits = DisplayText.shared.digits(value: value, eastern: layout.easternDigits)
            let text = Text(digits).font(.system(size: size)).foregroundColor(color)
            context.draw(text, at: CGPoint(x: x, y: y))
        }
        func tile(_ rect: CGRect, color: Color) {
            context.fill(Path(roundedRect: rect, cornerRadius: 8), with: .color(color))
        }

        if let background, !["solid", "plain", "theme"].contains(background),
           !PhoneAppearance.catalog.backgrounds.contains(background) {
            var pattern = Path()
            for offset in stride(from: -360, through: 640, by: 48) {
                let x = CGFloat(offset)
                pattern.move(to: CGPoint(x: x, y: 0))
                pattern.addLine(to: CGPoint(x: x + 360, y: 360))
                pattern.move(to: CGPoint(x: x + 360, y: 0))
                pattern.addLine(to: CGPoint(x: x, y: 360))
            }
            context.stroke(pattern, with: .color(accent.opacity(0.11)), lineWidth: 1)
        }
        label(clock, x: layout.arabic ? 90 : 550, y: 38, size: 24, color: ink)
        label(PhoneData.text("living", arabic: layout.arabic), x: 320, y: 38, size: 19, color: accent)
        label(PhoneData.text("preview", arabic: layout.arabic), x: 320, y: 337, size: 14, color: muted)

        let hasMihrab = layout.countdown == .mihrabLeft || layout.countdown == .mihrabRight
        let left = layout.countdown == .mihrabLeft
        if hasMihrab {
            let x: CGFloat = left ? 24 : 410
            let arch = TVMihrab.design.path(in: CGRect(x: x, y: 77, width: 206, height: 183))
            let opacity = Double(UiContract.shared.mihrabOpacity(value: layout.opacity)) / 100
            context.fill(arch, with: .color(panel.opacity(opacity)))
            context.stroke(arch, with: .color(accent), lineWidth: 2)
            label(asr, x: x + 103, y: 160, size: 23, color: accent)
            label("00:45", x: x + 103, y: 198, size: 32, color: ink)
        } else if layout.countdown == .stripTop || layout.countdown == .stripBottom {
            let top: CGFloat = layout.countdown == .stripBottom ? 292 : 76
            tile(CGRect(x: 24, y: top, width: 592, height: 42), color: panel)
            label(asr + "  00:45", x: 320, y: top + 21, size: 20, color: accent)
        }

        let start: CGFloat = hasMihrab && left ? 254 : 24
        let width: CGFloat = hasMihrab ? 362 : 592
        let indices = layout.arabic ? Array(layout.prayerIndices.reversed()) : layout.prayerIndices
        let times = ["05:10", "", "12:00", "15:15", "17:30", "19:00"]
        if !indices.isEmpty {
            let cell = width / CGFloat(indices.count)
            for (column, index) in indices.enumerated() {
                let x = start + CGFloat(column) * cell
                tile(CGRect(x: x, y: 135, width: cell - 5, height: 55), color: panel)
                label(times[index], x: x + (cell - 5) / 2, y: 162, size: 15, color: ink)
            }
        }
        let cell = width / 4
        for row in 0..<layout.favouriteRows {
            for column in 0..<4 {
                tile(CGRect(x: start + CGFloat(column) * cell, y: 208 + CGFloat(row) * 39,
                            width: cell - 7, height: 30), color: favourite)
            }
        }
    }
}

private struct TVMihrab: Decodable {
    let width: CGFloat
    let height: CGFloat
    let outer: String
    static let design: Self = PhoneData.load("mihrab")

    func path(in rect: CGRect) -> Path {
        let tokens = outer.replacingOccurrences(of: "([MLCZ])", with: " $1 ", options: .regularExpression)
            .split(whereSeparator: \.isWhitespace)
        var remaining = tokens[...]
        var result = Path()
        while let command = remaining.popFirst() {
            if command == "Z" { result.closeSubpath(); continue }
            let count = command == "C" ? 6 : 2
            let points = remaining.prefix(count).compactMap { Double($0) }
            guard points.count == count else { return Path() }
            remaining = remaining.dropFirst(count)
            func point(_ index: Int) -> CGPoint {
                CGPoint(x: rect.minX + CGFloat(points[index]) * rect.width / width,
                        y: rect.minY + CGFloat(points[index + 1]) * rect.height / height)
            }
            switch command {
            case "M": result.move(to: point(0))
            case "L": result.addLine(to: point(0))
            case "C": result.addCurve(to: point(4), control1: point(0), control2: point(2))
            default: return Path()
            }
        }
        return result
    }
}

struct TVThemeEditor: View {
    @Environment(\.dismiss) private var dismiss
    let preferences: PhonePreferences
    let title: String
    let backgrounds: [String]
    let maximum: Int
    let editable: Bool
    let save: ([String: String]) -> Void
    let layout: TVPreviewLayout
    @State var draft: TVThemeDraft

    private var changes: [String: String]? {
        draft.changes(keys: TVProfileCatalog.data.themeKeys, backgrounds: backgrounds,
                      surfaces: TVProfileCatalog.data.themeSurfaces, maximum: maximum)
    }

    var body: some View {
        NavigationStack {
            Form {
                TVAppearancePreview(layout: layout, colors: draft.colors ?? [:],
                                    background: draft.background, lighting: draft.lighting)
                ForEach(TVProfileCatalog.data.themeKeys, id: \.self) { key in
                    HStack {
                        Circle().fill(PhoneAppearance.color(draft.colors?[key]) ?? .clear)
                            .frame(width: 18, height: 18).accessibilityHidden(true)
                        TextField(preferences.text(key), text: Binding(
                            get: { draft.colors?[key] ?? "" },
                            set: { draft.colors?[key] = $0 }
                        ))
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                }
                if changes == nil { Text(preferences.text("invalid")).foregroundStyle(.secondary) }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(preferences.text("cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(preferences.text("done")) {
                        guard let changes else { return }
                        save(changes)
                        dismiss()
                    }
                    .disabled(!editable || changes == nil)
                }
            }
        }
    }
}
