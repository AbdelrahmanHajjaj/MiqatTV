import MiqatKit
import SwiftUI

struct PhoneSettings: View {
    @EnvironmentObject private var library: AdhanLibrary
    @EnvironmentObject private var audio: PhoneAdhan
    @EnvironmentObject private var radio: QuranRadio
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    @ObservedObject var notifications: PrayerNotifications
    let startSetup: () -> Void
    @State private var reset = false
    @State private var resetFailed = false

    var body: some View {
        Form {
            Section {
                NavigationLink(preferences.text("phone_calculation")) {
                    LocationSettings(preferences: $preferences, schedule: schedule)
                }
                NavigationLink(preferences.text("prayerOffsets")) {
                    PrayerOffsets(preferences: $preferences, schedule: schedule)
                }
                NavigationLink(preferences.text("phone_audio")) {
                    AlertSettings(preferences: $preferences, schedule: schedule, notifications: notifications)
                }
                NavigationLink(preferences.text("phone_fasting")) {
                    FastingSettings(preferences: $preferences, notifications: notifications)
                }
                if !preferences.setupFinished {
                    Button(preferences.text("setup_start"), action: startSetup)
                }
            }
            Section {
                NavigationLink(preferences.text("phone_appearance")) {
                    PhoneAppearanceEditor(preferences: $preferences, schedule: schedule)
                }
                NavigationLink(preferences.text("aboutMiqat")) {
                    AboutMiqat(preferences: preferences)
                }
            }
            Section {
                Button(preferences.text("phone_reset"), role: .destructive) { reset = true }
            }
        }
        .navigationTitle(preferences.text("phone_settings"))
        .alert(preferences.text("phone_reset"), isPresented: $reset) {
            Button(preferences.text("phone_reset"), role: .destructive) {
                do {
                    audio.stop()
                    radio.stop()
                    try library.reset()
                    notifications.cancelTest()
                    try PhoneStore.shared.clearAdhanClaims()
                    preferences = PhonePreferences()
                } catch { resetFailed = true }
            }
            Button(preferences.text("cancel"), role: .cancel) {}
        } message: {
            Text(preferences.text("phone_reset_confirm"))
        }
        .alert(preferences.text("phone_save_failed"), isPresented: $resetFailed) {
            Button(preferences.text("done"), role: .cancel) {}
        }
    }
}

struct DisplaySettings: View {
    @Binding var preferences: PhonePreferences

    var body: some View {
        Picker(preferences.text("language"), selection: $preferences.language) {
            Text(preferences.text("system")).tag("system")
            Text("العربية").tag("ar")
            Text("English").tag("en")
        }
        Picker(preferences.text("phone_digits"), selection: $preferences.digits) {
            Text(preferences.arabic ? "حسب اللغة" : "Follow language").tag("auto")
            Text("123").tag("western")
            Text("١٢٣").tag("eastern")
        }
        Picker(preferences.text("phone_clock"), selection: $preferences.clock) {
            Text(preferences.displayDigits("12")).tag("12")
            Text(preferences.displayDigits("24")).tag("24")
        }
        Toggle(preferences.text("phone_home_background"), isOn: $preferences.showArtwork)
        Stepper(value: $preferences.displaySize, in: 80...150, step: 10) {
            LabeledContent(preferences.text("phone_size"), value: preferences.displayDigits("\(preferences.displaySize)%"))
        }
        Toggle(preferences.text("phone_reduce_motion"), isOn: $preferences.reduceMotion)
    }
}

struct PhoneAppearanceEditor: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    var finish: (() -> Void)?

    var body: some View {
        let ground = PhoneAppearance.color("lapis-900", preferences: preferences)
        let ink = PhoneAppearance.color("plaster-100", preferences: preferences)
        Form {
            NavigationLink {
                PrayerHome(preferences: .constant(preferences), schedule: schedule, preview: true)
            } label: {
                Label(preferences.text("appearancePreview"), systemImage: "eye")
                    .font(.headline)
                    .foregroundStyle(ink)
                    .frame(maxWidth: .infinity, minHeight: 86, alignment: .leading)
                    .padding(.horizontal, 16)
                    .background {
                        PhoneBackdrop(ground: ground, scene: preferences.background, shade: 0.74, portrait: false)
                    }
                    .clipShape(.rect(cornerRadius: 16))
            }
            Section(preferences.text("phone_theme")) {
                rail(selected: $preferences.theme, ids: PhoneAppearance.catalog.themeOrder,
                     name: { PhoneAppearance.themeName($0, preferences: preferences) }) { id in
                    let colors = PhoneAppearance.catalog.themes[id]?.colors
                    HStack(spacing: 0) {
                        ForEach(["lapis-900", "lapis-800", "gold-400", "plaster-100"], id: \.self) { role in
                            PhoneAppearance.color(colors?[role]) ?? ground
                        }
                    }
                }
            }
            Section(preferences.text("phone_background")) {
                rail(selected: $preferences.background, ids: PhoneAppearance.catalog.backgrounds,
                     name: { PhoneAppearance.backgroundName($0, preferences: preferences) }) { id in
                    PhoneBackdrop(ground: ground, scene: id, shade: 0, portrait: false, maxPixelSize: 240)
                }
            }
            Section { DisplaySettings(preferences: $preferences) }
            if let finish { Button(preferences.text("setup_finish"), action: finish) }
        }
        .scrollContentBackground(.hidden)
        .background { PhoneBackdrop(ground: ground, scene: preferences.background, shade: 0.76).ignoresSafeArea() }
        .navigationTitle(preferences.text(finish == nil ? "phone_appearance" : "setup_appearance"))
        .toolbarBackground(ground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private func rail<Swatch: View>(selected: Binding<String>, ids: [String], name: @escaping (String) -> String,
                                    @ViewBuilder swatch: @escaping (String) -> Swatch) -> some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 9) {
                ForEach(ids, id: \.self) { id in
                    Button { selected.wrappedValue = id } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            swatch(id)
                                .frame(height: 46)
                                .clipShape(.rect(cornerRadius: 8))
                                .overlay(alignment: .topTrailing) {
                                    if id == selected.wrappedValue {
                                        Image(systemName: "checkmark.circle.fill")
                                            .symbolRenderingMode(.palette)
                                            .foregroundStyle(.white, .black)
                                            .padding(5)
                                    }
                                }
                            Text(name(id)).font(.caption).lineLimit(2)
                        }
                        .frame(width: 110, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(id == selected.wrappedValue ? .isSelected : [])
                }
            }
        }
        .scrollIndicators(.hidden)
    }
}

struct PrayerOffsets: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule

    var body: some View {
        let times = preferences.locationConfirmed ? schedule.day(at: .now, prefs: preferences) : nil
        Form {
            ForEach(preferences.prayerNames().indices, id: \.self) { index in
                PrayerMinuteControl(preferences: preferences, label: preferences.prayerNames()[index],
                                    value: $preferences.offsets[index], range: -30...30, preview: times?[index])
            }
        }
        .navigationTitle(preferences.text("prayerOffsets"))
    }
}

struct PrayerMinuteControl: View {
    let preferences: PhonePreferences
    let label: String
    @Binding var value: Int
    let range: ClosedRange<Int>
    let preview: Date?
    @State private var draft = ""
    @FocusState private var editing: Bool
    @ScaledMetric private var inputWidth = 48.0

    private var formatted: String { preferences.displayDigits(String(value)) }

    var body: some View {
        VStack(alignment: .leading) {
            Text(label)
            HStack {
                TextField(label, text: Binding(get: { editing ? draft : formatted }, set: update))
                    .focused($editing)
                    .keyboardType(.numbersAndPunctuation)
                    .submitLabel(.done)
                    .onSubmit { editing = false }
                    .textFieldStyle(.roundedBorder)
                    .monospacedDigit()
                    .frame(width: inputWidth)
                    .onChange(of: editing) { _, active in if active { draft = formatted } }
                Text(preview.map(preferences.time) ?? "—").foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Stepper(label, value: Binding(get: { value }, set: { next in
                    editing = false
                    value = next
                }), in: range)
                .labelsHidden()
            }
        }
    }

    private func update(_ text: String) {
        draft = text
        let normalized = DisplayText.shared.digits(value: text, eastern: false)
            .replacingOccurrences(of: "−", with: "-").trimmingCharacters(in: .whitespacesAndNewlines)
        if let next = Int(normalized), range.contains(next), next != value { value = next }
    }
}

struct AboutMiqat: View {
    let preferences: PhonePreferences

    var body: some View {
        List {
            let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
            LabeledContent(preferences.text("aboutVersion"), value: preferences.displayDigits(version))
            Text(preferences.text("aboutDeveloper"))
            Text(preferences.text("aboutSadaqah"))
            if let url = PhoneData.feedback["github"] {
                Link(preferences.text("aboutGithub"), destination: url)
            }
        }
        .navigationTitle(preferences.text("aboutMiqat"))
    }
}
