import MiqatKit
import SwiftUI

struct YourTVs: View {
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    @State private var forget: PairedTV?
    @State private var error = false
    @State private var demo = false

    var body: some View {
        List {
            Section {
                NavigationLink(preferences.text("pair")) { TVPairing(preferences: preferences) }
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
        available && ["toggle", "choice", "number", "list"].contains(kind)
            && (canonical != nil || id.range(of: "^remote\\.key-[0-9]{1,5}$", options: .regularExpression) != nil && kind == "choice")
            && (kind != "number" || canonical?.range != nil)
    }

    var limit: Int { min(9, max(1, canonical?.maximum ?? 9)) }
}

struct TVSettingsState: Decodable {
    let controls: [TVControl]
    var values: [String: String]
    var status: String?
}

struct TVProfileCatalog: Decodable {
    let profiles: [String: [TVControl]]
    let sections: [String: Localized<String>]
    static let data: Self = PhoneData.load("tv-profiles")

    func preview(_ profile: String) -> TVSettingsState {
        let controls = profiles[profile] ?? []
        return TVSettingsState(controls: controls, values: Dictionary(
            controls.compactMap { control in control.default.map { (control.id, $0) } }, uniquingKeysWith: { first, _ in first }
        ))
    }
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

    var body: some View {
        Form {
            if tv == nil { Text(preferences.text("demo")).foregroundStyle(.secondary) }
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
                        ForEach(sections[section] ?? []) { control in
                            TVSettingRow(preferences: preferences, control: control,
                                         value: snapshot.values[control.id] ?? "", save: { set(control.id, $0) })
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
        .toolbar {
            Button { load() } label: {
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
            if snapshot == nil { snapshot = TVProfileCatalog.data.preview(profile) }
            if remote == nil { remote = TVRemoteState(commands: TVProtocol.current.commands, inputs: [], media: nil) }
            status = nil
            editable = true
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
        guard action == nil, editable, var snapshot, snapshot.values[id] != value else { return }
        if tv == nil {
            snapshot.values[id] = value
            self.snapshot = snapshot
            return
        }
        connect {
            let result = try await $0.request("settings", as: TVSettingsState.self,
                body: ["expected": snapshot.values, "changes": [id: value]], credential: $1)
            guard let resultStatus = result.status, ["saved", "conflict", "busy", "uncertain"].contains(resultStatus) else {
                throw TVConnectionError.unconfirmed
            }
            try Task.checkCancellation()
            self.snapshot = result
            self.status = resultStatus == "saved" ? nil : resultStatus == "uncertain" ? "stale" : resultStatus
            self.editable = resultStatus != "uncertain"
        }
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
        if tv == nil { status = "demo"; return }
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
    let media: Media?
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
