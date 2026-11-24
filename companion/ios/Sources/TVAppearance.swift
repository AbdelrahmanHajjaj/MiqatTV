import SwiftUI

struct TVAppearancePreview: View {
    let preferences: PhonePreferences
    let colors: [String: String]
    let background: String?
    let lighting: Int?

    var body: some View {
        let ground = PhoneAppearance.color(colors["lapis-900"]) ?? .black
        let panel = PhoneAppearance.color(colors["lapis-800"]) ?? ground
        let ink = PhoneAppearance.color(colors["plaster-100"]) ?? .white
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
            VStack(spacing: 8) {
                Image(systemName: "sun.horizon.fill").foregroundStyle(accent).font(.title2)
                Text(preferences.text("appearancePreview")).font(.caption.weight(.semibold))
                HStack(spacing: 4) {
                    ForEach(preferences.prayerNames().indices, id: \.self) { index in
                        Text(preferences.prayerNames()[index])
                            .font(.caption2).lineLimit(1).minimumScaleFactor(0.55)
                            .frame(maxWidth: .infinity).padding(.vertical, 5)
                            .background(index == 2 ? accent : panel, in: .rect(cornerRadius: 4))
                            .foregroundStyle(index == 2 ? ground : ink)
                    }
                }
            }
            .foregroundStyle(ink)
            .padding(12)
        }
        .frame(height: 112)
        .clipShape(.rect(cornerRadius: 10))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(preferences.text("appearancePreview"))
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
    @State var draft: TVThemeDraft

    private var changes: [String: String]? {
        draft.changes(keys: TVProfileCatalog.data.themeKeys, backgrounds: backgrounds,
                      surfaces: TVProfileCatalog.data.themeSurfaces, maximum: maximum)
    }

    var body: some View {
        NavigationStack {
            Form {
                TVAppearancePreview(preferences: preferences, colors: draft.colors ?? [:],
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
