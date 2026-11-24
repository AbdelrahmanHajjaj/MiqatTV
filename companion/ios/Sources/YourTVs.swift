import MiqatKit
import SwiftUI

struct YourTVs: View {
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    @State private var forget: PairedTV?
    @State private var error = false
    @AppStorage("show-demo-tvs") private var demo = false
    @AppStorage("demo-tv-name-android") private var androidName = "Android / Google TV"
    @AppStorage("demo-tv-name-lg") private var lgName = "LG webOS"

    var body: some View {
        List {
            Section {
                NavigationLink(preferences.text("pair")) { TVPairing(preferences: preferences) }
                NavigationLink(preferences.text("tv_public_download")) { TVPackageView(preferences: preferences) }
                NavigationLink(preferences.text("tv_find_search")) { TVSetupDiscoveryView(preferences: preferences) }
                NavigationLink(preferences.text("tv_adb_setup")) { TVPackageView(preferences: preferences, setup: .android) }
                NavigationLink(preferences.text("lg_intro")) { TVPackageView(preferences: preferences, setup: .lg) }
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
                    NavigationLink(androidName) {
                        TVDetails(preferences: preferences, tv: nil, profile: "android", demoName: $androidName)
                    }
                    NavigationLink(lgName) { TVDetails(preferences: preferences, tv: nil, profile: "lg", demoName: $lgName) }
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
    var conflicts: [String]?
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
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    let tv: PairedTV?
    let profile: String
    var demoName: Binding<String>?
    @State private var page = "settings"
    @State private var snapshot: TVSettingsState?
    @State private var remote: TVRemoteState?
    @State private var action: Task<Void, Never>?
    @State private var status: String?
    @State private var editable = false
    @State private var demoTV: TVDemo?
    @State private var themeEdit: ThemeEdit?
    @State private var redirectEdit: RedirectEdit?
    @State private var rename = false
    @State private var draftName = ""

    private var pendingEdit: TVSettingEdit? {
        get { store.settingsEdits[tv?.id ?? "demo-" + profile] }
        nonmutating set { store.settingsEdits[tv?.id ?? "demo-" + profile] = newValue }
    }

    private var name: String {
        if let tv { return store.records.first(where: { $0.id == tv.id })?.name ?? tv.name }
        return demoName?.wrappedValue ?? (profile == "lg" ? "LG webOS" : "Android / Google TV")
    }

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
                if let pendingEdit { review(pendingEdit, snapshot: snapshot, controls: controls) }
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
                    .disabled(!editable || action != nil || pendingEdit != nil)
                }
            } else if page == "remote", let remote {
                remoteRows(remote)
            }
        }
        .navigationTitle(name)
        .alert(preferences.text("rename"), isPresented: $rename) {
            TextField(preferences.text("rename"), text: $draftName)
            Button(preferences.text("done")) { renameTV() }
                .disabled(validName == nil)
            Button(preferences.text("cancel"), role: .cancel) {}
        }
        .sheet(item: $themeEdit) { edit in
            TVThemeEditor(preferences: preferences, title: edit.title,
                backgrounds: edit.backgrounds, maximum: edit.maximum, editable: editable && action == nil,
                save: { changes in apply(changes, expected: edit.baseline) }, layout: edit.layout, draft: edit.draft)
        }
        .sheet(item: $redirectEdit) { edit in
            TVRedirectEditor(preferences: preferences, title: edit.title, apps: edit.apps,
                savedValue: edit.savedValue, editable: editable && action == nil,
                save: { value in apply(["device.redirects": value], expected: edit.baseline) }, draft: edit.draft)
        }
        .toolbar {
            Button {
                demoTV?.endpoint.online = true
                load()
            } label: {
                Image(systemName: "arrow.clockwise").accessibilityLabel(preferences.text("refresh"))
            }
            .disabled(action != nil)
            Menu {
                Button(preferences.text("rename")) { draftName = name; rename = true }
                    .disabled(store.inaccessible && tv != nil)
            } label: {
                Image(systemName: "ellipsis.circle").accessibilityLabel(preferences.text("more_actions"))
            }
        }
        .onAppear { load() }
        .onChange(of: page) { _, _ in editable = false; action?.cancel(); loadAfterCancel() }
        .onDisappear { action?.cancel() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                action?.cancel()
                if tv != nil { editable = false; status = "stale" }
            }
        }
    }

    private var validName: String? {
        let name = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...60).contains(name.utf16.count), !name.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
        else { return nil }
        return name
    }

    private func renameTV() {
        guard let validName else { return }
        if let tv {
            do { try store.rename(tv.id, to: validName) }
            catch { status = "vault_failed" }
        } else {
            demoName?.wrappedValue = validName
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
            if editable { useRefreshedSettings(demoTV.read()) }
            remote = demoTV.remote
            status = editable ? nil : "disconnected"
            return
        }
        editable = false
        status = nil
        let selected = page
        connect(failure: selected == "settings" ? "stale" : "remote_failed") {
            if selected == "settings" {
                let state = try await $0.request("settings", as: TVSettingsState.self, credential: $1)
                try Task.checkCancellation()
                useRefreshedSettings(state)
            } else {
                let state = try await $0.remote(credential: $1)
                try Task.checkCancellation()
                remote = state
            }
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

    private func useRefreshedSettings(_ state: TVSettingsState) {
        snapshot = state
        if let pendingEdit, pendingEdit.remaining(against: state.values).isEmpty { self.pendingEdit = nil }
    }

    private func apply(_ changes: [String: String], expected: [String: String]) {
        guard action == nil, editable, let snapshot else { return }
        let draft = TVSettingEdit(baseline: expected, changes: changes)
        guard !draft.remaining(against: snapshot.values).isEmpty else {
            pendingEdit = nil
            return
        }
        pendingEdit = draft
        if tv == nil {
            guard let result = demoTV?.apply(expected: expected, changes: changes) else { status = "invalid"; return }
            finishEdit(result)
            return
        }
        connect(failure: "uncertain") {
            let result = try await $0.request("settings", as: TVSettingsState.self,
                body: ["expected": expected, "changes": changes], credential: $1)
            guard let resultStatus = result.status, ["saved", "conflict", "busy", "uncertain"].contains(resultStatus) else {
                throw TVConnectionError.unconfirmed
            }
            try Task.checkCancellation()
            finishEdit(result)
        }
    }

    private func finishEdit(_ result: TVSettingsState) {
        snapshot = result
        status = result.status == "saved" ? nil : result.status
        editable = result.status != "uncertain"
        if result.status == "saved" || (editable && pendingEdit?.remaining(against: result.values).isEmpty == true) {
            pendingEdit = nil
        } else {
            pendingEdit?.conflicts = (result.conflicts ?? []).filter { result.values[$0] != nil }
        }
    }

    private func review(_ draft: TVSettingEdit, snapshot: TVSettingsState, controls: [TVControl]) -> some View {
        let changes = draft.remaining(against: snapshot.values)
        let available = Set(controls.map(\.id))
        let canReplace = editable && action == nil && Set(changes.keys).isSubset(of: available)
        return Section(preferences.text("tv_unsaved_settings")) {
            ForEach(draft.reviewKeys, id: \.self) { id in
                let control = controls.first { $0.id == id }
                VStack(alignment: .leading, spacing: 4) {
                    Text(control?.title.value(preferences.arabic) ?? id).font(.headline)
                    if editable {
                        LabeledContent(preferences.text("current"), value: reviewValue(snapshot.values[id], control: control))
                    }
                    LabeledContent(preferences.text("your_edit"), value: reviewValue(draft.requestedValue(for: id), control: control))
                }
                .lineLimit(3)
            }
            Button(preferences.text(draft.conflicts.isEmpty ? "tv_retry_save" : "replace")) {
                apply(changes, expected: snapshot.values)
            }
            .disabled(!canReplace)
            Button(preferences.text("keep_current"), role: .cancel) { pendingEdit = nil; status = nil }
                .disabled(action != nil)
        }
    }

    private func reviewValue(_ value: String?, control: TVControl?) -> String {
        guard let value, !value.isEmpty else { return "—" }
        if let option = control?.options.first(where: { $0.value == value }) {
            return preferences.arabic ? option.ar : option.en
        }
        return preferences.displayDigits(value)
    }

    private func editTheme(_ snapshot: TVSettingsState) {
        let values = snapshot.values
        guard editable, action == nil,
              let control = snapshot.controls.first(where: { $0.id == "appearance.custom-theme" && $0.supported }),
              let value = values[control.id], var draft = TVThemeDraft.read(value) else {
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
        guard let draft = installed else { status = "invalid"; return }
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

    private func connect(failure: String = "stale", _ operation: @escaping (TVConnection, String) async throws -> Void) {
        guard let tv, action == nil, scenePhase == .active else { return }
        action = Task {
            defer { action = nil }
            do {
                let link = try TVConnection(tv.endpoint)
                defer { link.close() }
                try await operation(link, tv.credential)
            } catch {
                if !Task.isCancelled {
                    status = failure
                    editable = false
                }
            }
        }
    }

    @ViewBuilder private func remoteRows(_ state: TVRemoteState) -> some View {
        let commands = state.commands.filter { TVProtocol.current.commands.contains($0) }
        if commands.isEmpty {
            Text(preferences.text("remote_unavailable")).foregroundStyle(.secondary)
        } else {
            Section {
                if commands.contains("device.home") { commandButton("tv_home", command: "device.home", icon: "house") }
                if commands.contains("device.inputs") {
                    if state.inputs.isEmpty {
                        commandButton("tv_inputs", command: "device.inputs", icon: "rectangle.connected.to.line.below")
                    } else {
                        Menu {
                            Button(preferences.text("tv_inputs")) { command("device.inputs", value: "") }
                            ForEach(state.inputs, id: \.value) { input in
                                Button(input.label) { command("device.inputs", value: input.value) }
                            }
                        } label: {
                            Label(preferences.text("tv_inputs"), systemImage: "rectangle.connected.to.line.below")
                        }
                        .disabled(!canSend("device.inputs"))
                    }
                }
                if commands.contains("device.exit-input") {
                    commandButton("tv_input", command: "device.exit-input", icon: "tv")
                }
                if let selectedInput = state.inputs.first(where: { $0.value == state.input }) {
                    LabeledContent(preferences.text("tv_inputs"), value: selectedInput.label)
                }
            }
            mediaControls(state, kind: "radio", title: "radio_title", play: "radio.play", stop: "radio.stop")
            mediaControls(state, kind: "adhan", title: "adhan_preview", play: "tests.adhan-short", stop: "tests.cancel")
        }
    }

    @ViewBuilder private func mediaControls(_ state: TVRemoteState, kind: String, title: String, play: String, stop: String) -> some View {
        if state.commands.contains(play) || state.commands.contains(stop) {
            Section(preferences.text(title)) {
                if editable, let status = state.statusKey(for: kind) {
                    Text(preferences.text(status)).foregroundStyle(.secondary)
                }
                HStack(spacing: 14) {
                    if state.commands.contains(play) {
                        commandButton("play", command: play, icon: "play.fill")
                            .accessibilityLabel(preferences.text("play") + " · " + preferences.text(title))
                    }
                    if state.commands.contains(stop) {
                        commandButton("stop", command: stop, icon: "stop.fill")
                            .accessibilityLabel(preferences.text("stop") + " · " + preferences.text(title))
                    }
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private func commandButton(_ text: String, command id: String, icon: String) -> some View {
        Button { command(id, value: "") } label: { Label(preferences.text(text), systemImage: icon) }
            .disabled(!canSend(id))
    }

    private func canSend(_ id: String, value: String = "") -> Bool {
        action == nil && scenePhase == .active
            && remote?.permits(id, value: value, fresh: editable, allowed: TVProtocol.current.commands) == true
    }

    private func command(_ id: String, value: String) {
        guard canSend(id, value: value) else { return }
        if tv == nil {
            demoTV?.send(id, value: value)
            remote = demoTV?.remote
            return
        }
        connect(failure: "remote_uncertain") {
            let result = try await $0.request("command", as: TVRemoteState.Result.self,
                body: ["id": id, "value": value], credential: $1)
            try Task.checkCancellation()
            switch result.status {
            case .uncertain:
                self.status = "remote_uncertain"
                self.editable = false
            case .busy: self.status = "tv_busy"
            case .unavailable: self.status = "unavailable"
            case .accepted:
                do {
                    let state = try await $0.remote(credential: $1)
                    try Task.checkCancellation()
                    self.remote = state
                    self.status = "remote_accepted"
                    self.editable = true
                } catch {
                    if !Task.isCancelled {
                        self.status = "remote_readback_failed"
                        self.editable = false
                    }
                }
            }
        }
    }
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
