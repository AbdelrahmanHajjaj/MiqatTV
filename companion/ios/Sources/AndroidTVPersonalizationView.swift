import SwiftUI

@MainActor
struct AndroidTVPersonalizationView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var setup: AndroidTVSetup
    let preferences: PhonePreferences
    let snapshot: AndroidTVPersonalization.Snapshot
    @State private var draft: AndroidTVPersonalization.Draft

    init(setup: AndroidTVSetup, preferences: PhonePreferences, snapshot: AndroidTVPersonalization.Snapshot) {
        self.setup = setup
        self.preferences = preferences
        self.snapshot = snapshot
        _draft = State(initialValue: snapshot.current)
    }

    var body: some View {
        NavigationStack {
            Form {
                if let stage = setup.stage { ProgressView(preferences.text(stage)) }
                Section {
                    NavigationLink {
                        CityPicker(preferences: preferences, cities: snapshot.cities, selection: $draft.city)
                    } label: {
                        LabeledContent(preferences.text("tv_adb_personal_city"), value: cityName)
                    }
                    Picker(preferences.text("language"), selection: $draft.locale) {
                        Text(preferences.text("tv_adb_personal_locale")).tag("system")
                        Text(verbatim: "English").tag("en")
                        Text(verbatim: "العربية").tag("ar")
                    }
                    Toggle(preferences.text("tv_adb_personal_adhan"), isOn: $draft.adhan)
                    Toggle(preferences.text("tv_adb_personal_reminder"), isOn: $draft.remindVoice)
                }
                .disabled(setup.busy)
                if snapshot.maximum > 0 {
                    Section {
                        LabeledContent(preferences.text("tv_adb_personal_selected"), value: "\(draft.favourites.count) / \(snapshot.maximum)")
                        if draft.favourites.count < snapshot.minimum {
                            Text(preferences.text("tv_adb_personal_minimum")).foregroundStyle(.secondary)
                        }
                        ForEach(snapshot.apps) { app in
                            Button {
                                if draft.favourites.contains(app.p) {
                                    draft.favourites.removeAll { $0 == app.p }
                                } else if draft.favourites.count < snapshot.maximum {
                                    draft.favourites.append(app.p)
                                }
                            } label: {
                                LabeledContent(preferences.arabic ? app.ar : app.en,
                                               value: draft.favourites.firstIndex(of: app.p).map { String($0 + 1) } ?? "")
                            }
                            .disabled(!draft.favourites.contains(app.p) && draft.favourites.count == snapshot.maximum)
                        }
                    } header: {
                        Text(preferences.text("tv_adb_personal_favourites"))
                    }
                    .disabled(setup.busy)
                }
            }
            .navigationTitle(preferences.text("tv_adb_personal"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(preferences.text("cancel")) { dismiss() }.disabled(setup.busy)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(preferences.text("tv_adb_personal_save")) { setup.savePersonalization(draft, from: snapshot) }
                        .disabled(setup.busy || draft == snapshot.current || draft.favourites.count < snapshot.minimum)
                }
            }
        }
        .interactiveDismissDisabled(setup.busy)
    }

    private var cityName: String {
        guard let city = snapshot.cities.first(where: { $0.id == draft.city }) else { return draft.city }
        return preferences.arabic ? city.ar : city.en
    }

    private struct CityPicker: View {
        @Environment(\.dismiss) private var dismiss
        let preferences: PhonePreferences
        let cities: [AndroidTVPersonalization.City]
        @Binding var selection: String
        @State private var search = ""

        var body: some View {
            List(matches) { city in
                Button {
                    selection = city.id
                    dismiss()
                } label: {
                    LabeledContent(preferences.arabic ? city.ar : city.en,
                                   value: preferences.arabic ? city.countryAr : city.countryEn)
                }
            }
            .navigationTitle(preferences.text("tv_adb_personal_city"))
            .searchable(text: $search, prompt: Text(preferences.text("phone_search")))
        }

        private var matches: [AndroidTVPersonalization.City] {
            let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
            let result = query.isEmpty ? cities : cities.filter { city in
                [city.en, city.ar, city.countryEn, city.countryAr].contains { $0.localizedStandardContains(query) }
            }
            return result.sorted {
                let left = preferences.arabic ? $0.ar : $0.en
                let right = preferences.arabic ? $1.ar : $1.en
                return left.localizedStandardCompare(right) == .orderedAscending
            }
        }
    }
}
