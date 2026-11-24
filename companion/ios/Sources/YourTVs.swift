import MiqatKit
import SwiftUI

struct YourTVs: View {
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    @State private var forget: PairedTV?
    @State private var error = false
    @AppStorage("show-demo-tvs") private var demo = false

    var body: some View {
        List {
            Section {
                NavigationLink(preferences.text("pair")) { TVPairing(preferences: preferences) }
                NavigationLink(preferences.text("tv_public_download")) { TVPackageView(preferences: preferences) }
                NavigationLink(preferences.text("tv_find_search")) { TVSetupDiscoveryView(preferences: preferences) }
                NavigationLink(preferences.text("lg_intro")) { TVPackageView(preferences: preferences, setupLG: true) }
                Toggle(preferences.text("demo"), isOn: $demo)
            }
            Section {
                ForEach(store.records.filter { !$0.pending }) { tv in
                    NavigationLink(tv.name) { TVDetails(preferences: preferences, tv: tv, profile: tv.profile) }
                        .swipeActions {
                            Button(preferences.text("forget_tv"), role: .destructive) { forget = tv }
                        }
                }
                if demo {
                    NavigationLink("Android / Google TV") { TVDetails(preferences: preferences, tv: nil, profile: "android") }
                    NavigationLink("LG webOS") { TVDetails(preferences: preferences, tv: nil, profile: "lg") }
                }
            }
            if error || store.inaccessible {
                Text(preferences.text("phone_save_failed")).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(preferences.text("tvs"))
        .onAppear { store.load() }
        .alert(preferences.text("forget_tv_info"), isPresented: Binding(
            get: { forget != nil }, set: { if !$0 { forget = nil } }
        )) {
            Button(preferences.text("forget_tv"), role: .destructive) {
                if let forget { do { try store.remove(forget) } catch { error = true } }
            }
            Button(preferences.text("cancel"), role: .cancel) {}
        }
    }
}

struct TVControl: Decodable, Identifiable {
    struct Option: Decodable { let value: String; let en: String; let ar: String }
    let id: String
    let section: String
    let kind: String
    let title: Localized<String>
    let options: [Option]
    let available: Bool
    let range: String?
    let maximum: Int?
    let `default`: String?

    private var canonical: TVControl? {
        TVProfileCatalog.data.profiles.values.lazy.flatMap { $0 }.first { $0.id == id && $0.kind == kind }
    }

    var supported: Bool {
        available && (["toggle", "choice", "number", "list"].contains(kind)
            || (kind == "text" && ["appearance.custom-theme", "device.redirects"].contains(id)))
            && (canonical != nil || id.range(of: "^remote\\.key-[0-9]{1,5}$", options: .regularExpression) != nil && kind == "choice")
            && (kind != "number" || canonical?.range != nil)
    }

    var limit: Int { min(9, max(1, canonical?.maximum ?? 9)) }
    var textLimit: Int { min(8192, max(0, canonical?.maximum ?? 0)) }
}

struct TVSettingsState: Decodable {
    let controls: [TVControl]
    var values: [String: String]
    var status: String?
    var systemLanguage: String?
}

struct TVProfileCatalog: Decodable {
    let profiles: [String: [TVControl]]
    let sections: [String: Localized<String>]
    let themeKeys: [String]
    let themeSurfaces: [String]
    let demoSystemLanguage: String
    static let data: Self = PhoneData.load("tv-profiles")
}

struct TVDetails: View {
    @Environment(\.scenePhase) private var scenePhase
    let preferences: PhonePreferences
    let tv: PairedTV?
    let profile: String
    @State private var page = "settings"
    @State private var snapshot: TVSettingsState?
    @State private var remote: TVRemoteState?
    @State private var action: Task<Void, Never>?
    @State private var status: String?
    @State private var editable = false
    @State private var demoTV: TVDemo?
    @State private var themeEdit: ThemeEdit?
    @State private var redirectEdit: RedirectEdit?
    @State private var unsavedText: [String: String] = [:]

    private struct ThemeEdit: Identifiable {
        let id = UUID()
        let baseline: [String: String]
        let title: String
        let backgrounds: [String]
        let maximum: Int
        let draft: TVThemeDraft
        let layout: TVPreviewLayout
    }

    private struct RedirectEdit: Identifiable {
        let id = UUID()
        let baseline: [String: String]
        let title: String
        let apps: [TVControl.Option]
        let savedValue: String?
        let draft: TVRedirects
    }

    var body: some View {
        Form {
            if tv == nil {
                Text(preferences.text("demo")).foregroundStyle(.secondary)
                if let demoTV {
                    Section(preferences.text("demo_tools")) {
                        Button(preferences.text(editable ? "disconnect" : "reconnect")) {
                            demoTV.endpoint.online.toggle()
                            load()
                        }
                        Button(preferences.text("fail_next")) {
                            demoTV.endpoint.failNextSave = true
                            status = "fail_armed"
                        }
                        .disabled(!editable)
                    }
                }
            }
            Picker(preferences.text("tvs"), selection: $page) {
                Text(preferences.text("settings")).tag("settings")
                Text(preferences.text("remote_title")).tag("remote")
            }
            .pickerStyle(.segmented)
            if let status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if action != nil { ProgressView() }
            if page == "settings", let snapshot {
                let controls = snapshot.controls.filter { $0.supported && snapshot.values[$0.id] != nil }
                let sections = Dictionary(grouping: controls, by: \.section)
                ForEach(sections.keys.sorted(), id: \.self) { section in
                    Section(TVProfileCatalog.data.sections[section]?.value(preferences.arabic)
                        ?? preferences.text("settings")) {
                        if section == "appearance" {
                            appearancePreview(snapshot)
                        }
                        ForEach(sections[section] ?? []) { control in
                            if control.id == "appearance.custom-theme" {
                                Button(control.title.value(preferences.arabic)) { editTheme(snapshot) }
                            } else if control.id == "device.redirects" {
                                Button(control.title.value(preferences.arabic)) { editRedirects(snapshot, control: control) }
                            } else {
                                TVSettingRow(preferences: preferences, control: control,
                                             value: snapshot.values[control.id] ?? "", save: { set(control.id, $0) })
                            }
                        }
                    }
                    .disabled(!editable || action != nil)
                }
            } else if page == "remote", let remote {
                remoteRows(remote)
                    .disabled(!editable || action != nil)
            }
        }
        .navigationTitle(tv?.name ?? (profile == "lg" ? "LG webOS" : "Android / Google TV"))
        .sheet(item: $themeEdit) { edit in
            TVThemeEditor(preferences: preferences, title: edit.title,
                backgrounds: edit.backgrounds, maximum: edit.maximum, editable: editable && action == nil,
                save: { changes in
                    unsavedText["appearance.custom-theme"] = changes["appearance.custom-theme"]
                    apply(changes, expected: edit.baseline)
                }, layout: edit.layout, draft: edit.draft)
        }
        .sheet(item: $redirectEdit) { edit in
            TVRedirectEditor(preferences: preferences, title: edit.title, apps: edit.apps,
                savedValue: edit.savedValue, editable: editable && action == nil,
                save: { value in
                    unsavedText["device.redirects"] = value
                    apply(["device.redirects": value], expected: edit.baseline)
                }, draft: edit.draft)
        }
        .toolbar {
            Button {
                demoTV?.endpoint.online = true
                load()
            } label: {
                Image(systemName: "arrow.clockwise").accessibilityLabel(preferences.text("refresh"))
            }
            .disabled(action != nil)
        }
        .onAppear { load() }
        .onChange(of: page) { _, _ in action?.cancel(); loadAfterCancel() }
        .onDisappear { action?.cancel() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                action?.cancel()
                if tv != nil { editable = false; status = "stale" }
            }
        }
    }

    private func loadAfterCancel() {
        let previous = action
        Task {
            await previous?.value
            if scenePhase == .active { load() }
        }
    }

    private func load() {
        guard action == nil, scenePhase == .active else { return }
        if tv == nil {
            if demoTV == nil { demoTV = TVDemo(profile: profile, controls: TVProfileCatalog.data.profiles[profile] ?? []) }
            guard let demoTV else { return }
            editable = demoTV.endpoint.online
            if editable { snapshot = demoTV.read() }
            remote = demoTV.remote
            status = editable ? nil : "disconnected"
            return
        }
        editable = false
        status = nil
        let selected = page
        connect {
            if selected == "settings" {
                snapshot = try await $0.request("settings", as: TVSettingsState.self, credential: $1)
            } else {
                remote = try await $0.request("remote", as: TVRemoteState.self, credential: $1)
            }
            try Task.checkCancellation()
            editable = true
        }
    }

    private func set(_ id: String, _ value: String) {
        guard action == nil, editable, let snapshot, snapshot.values[id] != value else { return }
        if id == "appearance.theme", value == "custom",
           snapshot.values["appearance.custom-theme"]?.isEmpty != false {
            editTheme(snapshot)
            return
        }
        apply([id: value], expected: snapshot.values)
    }

    private func apply(_ changes: [String: String], expected: [String: String]) {
        guard action == nil, editable, let snapshot else { return }
        guard changes.contains(where: { snapshot.values[$0.key] != $0.value }) else {
            for key in changes.keys { unsavedText[key] = nil }
            return
        }
        if tv == nil {
            guard let result = demoTV?.apply(expected: expected, changes: changes) else { status = "invalid"; return }
            self.snapshot = result
            status = result.status
            if result.status == "saved" { for key in changes.keys { unsavedText[key] = nil } }
            return
        }
        connect {
            let result = try await $0.request("settings", as: TVSettingsState.self,
                body: ["expected": expected, "changes": changes], credential: $1)
            guard let resultStatus = result.status, ["saved", "conflict", "busy", "uncertain"].contains(resultStatus) else {
                throw TVConnectionError.unconfirmed
            }
            try Task.checkCancellation()
            self.snapshot = result
            self.status = resultStatus == "saved" ? nil : resultStatus == "uncertain" ? "stale" : resultStatus
            self.editable = resultStatus != "uncertain"
            if resultStatus == "saved" { for key in changes.keys { unsavedText[key] = nil } }
        }
    }

    private func editTheme(_ snapshot: TVSettingsState) {
        let values = snapshot.values
        guard editable, action == nil,
              let control = snapshot.controls.first(where: { $0.id == "appearance.custom-theme" && $0.supported }),
              let value = unsavedText[control.id] ?? values[control.id], var draft = TVThemeDraft.read(value) else {
            status = "invalid"
            return
        }
        let keys = TVProfileCatalog.data.themeKeys
        let backgrounds = snapshot.controls.first(where: { $0.id == "appearance.background" })?.options.map(\.value) ?? []
        guard draft.changes(keys: keys, backgrounds: backgrounds, surfaces: TVProfileCatalog.data.themeSurfaces,
                            maximum: control.textLimit) != nil else {
            status = "invalid"
            return
        }
        let colors = themeColors(values)
        draft.colors = Dictionary(uniqueKeysWithValues: keys.compactMap { key in
            (draft.colors?[key] ?? colors[key]).map { (key, $0) }
        })
        themeEdit = ThemeEdit(baseline: values, title: control.title.value(preferences.arabic),
                              backgrounds: backgrounds, maximum: control.textLimit, draft: draft,
                              layout: TVPreviewLayout(values: values, systemLanguage: snapshot.systemLanguage))
    }

    private func editRedirects(_ snapshot: TVSettingsState, control: TVControl) {
        guard editable, action == nil, let value = snapshot.values[control.id],
              let options = snapshot.controls.first(where: { $0.id == "favourites.order" })?.options else { return }
        let apps = options.filter { !$0.value.isEmpty && !$0.value.contains(",") && !$0.value.contains("=") }
        let ids = Set(apps.map(\.value))
        let installed = TVRedirects(value, apps: ids, maximum: control.textLimit)
        let retained = unsavedText[control.id].flatMap { TVRedirects($0, apps: ids, maximum: control.textLimit) }
        guard let draft = retained ?? installed else { status = "invalid"; return }
        redirectEdit = RedirectEdit(baseline: snapshot.values, title: control.title.value(preferences.arabic),
                                    apps: apps, savedValue: installed?.value, draft: draft)
    }

    private func themeColors(_ values: [String: String]) -> [String: String] {
        let themes = PhoneAppearance.catalog.themes
        let id = values["appearance.theme"] ?? "mihrab"
        let base = themes["mihrab"]?.colors ?? [:]
        if id == "custom", let draft = TVThemeDraft.read(values["appearance.custom-theme"] ?? "") {
            return base.merging(draft.colors ?? [:]) { _, custom in custom }
        }
        return base.merging(themes[id]?.colors ?? [:]) { _, selected in selected }
    }

    private func appearancePreview(_ snapshot: TVSettingsState) -> some View {
        let values = snapshot.values
        let id = values["appearance.theme"] ?? "mihrab"
        let theme = PhoneAppearance.catalog.themes[id]
        let custom = id == "custom" ? TVThemeDraft.read(values["appearance.custom-theme"] ?? "") : nil
        let background = values["appearance.background"]
        return TVAppearancePreview(layout: TVPreviewLayout(values: values, systemLanguage: snapshot.systemLanguage),
            colors: themeColors(values),
            background: background == "theme" || background == nil ? custom?.background ?? theme?.background : background,
            lighting: values["appearance.lighting"].flatMap { Int($0) } ?? custom?.lighting ?? theme?.lighting)
    }

    private func connect(_ operation: @escaping (TVConnection, String) async throws -> Void) {
        guard let tv, action == nil, scenePhase == .active else { return }
        action = Task {
            defer { action = nil }
            do {
                let link = try TVConnection(tv.endpoint)
                defer { link.close() }
                try await operation(link, tv.credential)
            } catch {
                if !Task.isCancelled {
                    status = page == "settings" ? "stale" : "remote_uncertain"
                    editable = false
                }
            }
        }
    }

    private func remoteRows(_ state: TVRemoteState) -> some View {
        let commands = state.commands.filter { TVProtocol.current.commands.contains($0) }
        return Section {
            if let media = state.media, !media.state.isEmpty {
                Text(preferences.text(media.state == "playing" ? "phone_playing" : ""))
                    .foregroundStyle(.secondary)
            }
            if let selectedInput = state.inputs.first(where: { $0.value == state.input }) {
                LabeledContent(preferences.text("inputsTitle"), value: selectedInput.label)
            }
            if commands.contains("device.home") { commandButton("home", command: "device.home") }
            if commands.contains("device.inputs") {
                commandButton("inputsTitle", command: "device.inputs")
                ForEach(state.inputs.indices, id: \.self) { index in
                    let input = state.inputs[index]
                    Button(input.label) { command("device.inputs", value: input.value) }
                }
            }
            if commands.contains("device.exit-input") { commandButton("back", command: "device.exit-input") }
            if commands.contains("radio.play") { commandButton("radio_title", command: "radio.play") }
            if commands.contains("radio.stop") { commandButton("phone_radio_stop", command: "radio.stop") }
            if commands.contains("tests.adhan-short") { commandButton("phone_test_adhan", command: "tests.adhan-short") }
            if commands.contains("tests.cancel") { commandButton("phone_stop", command: "tests.cancel") }
        }
    }

    private func commandButton(_ text: String, command id: String) -> some View {
        Button(preferences.text(text)) { command(id, value: "") }
    }

    private func command(_ id: String, value: String) {
        guard editable, action == nil, TVProtocol.current.commands.contains(id), value.utf8.count <= 2048 else { return }
        if tv == nil {
            demoTV?.send(id, value: value)
            remote = demoTV?.remote
            return
        }
        connect {
            let result = try await $0.request("command", as: TVRemoteState.Result.self,
                body: ["id": id, "value": value], credential: $1)
            guard ["accepted", "busy", "unavailable", "uncertain"].contains(result.status) else {
                throw TVConnectionError.unconfirmed
            }
            try Task.checkCancellation()
            if result.status == "uncertain" { self.status = "remote_uncertain"; self.editable = false }
            else {
                self.status = result.status == "accepted" ? "remote_accepted" : result.status
                self.remote = try await $0.request("remote", as: TVRemoteState.self, credential: $1)
            }
        }
    }
}

struct TVRemoteState: Decodable {
    struct Input: Decodable { let value: String; let label: String }
    struct Media: Decodable { let state: String }
    struct Result: Decodable { let status: String }
    let commands: [String]
    let inputs: [Input]
    var media: Media?
    var input: String?
}

struct TVSettingRow: View {
    let preferences: PhonePreferences
    let control: TVControl
    let value: String
    let save: (String) -> Void

    var body: some View {
        let title = control.title.value(preferences.arabic)
        switch control.kind {
        case "toggle":
            Toggle(title, isOn: Binding(get: { value == "true" }, set: { save($0 ? "true" : "false") }))
        case "choice":
            Picker(title, selection: Binding(get: { value }, set: { save($0) })) {
                if !control.options.contains(where: { $0.value == value }) { Text(value).tag(value) }
                ForEach(control.options.indices, id: \.self) { index in
                    let option = control.options[index]
                    Text(preferences.arabic ? option.ar : option.en).tag(option.value)
                }
            }
        case "number":
            if let number = Int(value) {
                Stepper {
                    LabeledContent(title, value: preferences.displayDigits(value))
                } onIncrement: {
                    adjust(number, direction: 1)
                } onDecrement: {
                    adjust(number, direction: -1)
                }
            }
        case "list":
            NavigationLink(title) {
                TVFavouriteList(preferences: preferences, options: control.options, limit: control.limit,
                                initial: value.split(separator: ",").map(String.init), save: save)
            }
        default:
            EmptyView()
        }
    }

    private func adjust(_ number: Int, direction: Double) {
        let adjusted = UiContract.shared.adjustNumber(id: control.id, value: Double(number), direction: direction)
        save(String(adjusted))
    }
}

struct TVFavouriteList: View {
    @Environment(\.dismiss) private var dismiss
    let preferences: PhonePreferences
    let options: [TVControl.Option]
    let limit: Int
    let initial: [String]
    let save: (String) -> Void
    @State private var order: [String] = []

    var body: some View {
        List {
            ForEach(order, id: \.self) { value in
                if let option = options.first(where: { $0.value == value }) { Text(preferences.arabic ? option.ar : option.en) }
            }
            .onDelete { order.remove(atOffsets: $0) }
            .onMove { order.move(fromOffsets: $0, toOffset: $1) }
            ForEach(options.indices, id: \.self) { index in
                let option = options[index]
                if !order.contains(option.value) {
                    Button(preferences.arabic ? option.ar : option.en) { order.append(option.value) }
                        .disabled(order.count >= limit || option.value.contains(","))
                }
            }
        }
        .environment(\.editMode, .constant(.active))
        .navigationTitle(preferences.text("homeFavourites"))
        .onAppear { order = initial.filter { id in options.contains { $0.value == id } } }
        .toolbar {
            Button(preferences.text("done")) { save(order.joined(separator: ",")); dismiss() }
        }
    }
}
