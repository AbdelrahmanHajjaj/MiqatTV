import SwiftUI
import UniformTypeIdentifiers

@MainActor
struct TVPackageView: View {
    private struct Platform: Decodable {
        let id: String
        let en: String
        let ar: String
    }

    private static let platforms: [Platform] = PhoneData.load("tv-platforms")
    let preferences: PhonePreferences
    private let setup: TVPackagePlatform?
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var lg = LGSetup()
    @StateObject private var android: AndroidTVSetup
    @State private var platform: TVPackagePlatform
    @State private var channel = TVPackageChannel.production
    @State private var task: Task<Void, Never>?
    @State private var progress: TVPackageDownload.Progress?
    @State private var status: String?
    @State private var package: TVPackageDownload.Package?
    @State private var importing = false
    @State private var confirmingInstall = false
    @State private var confirmingForget = false
    @State private var confirmingSetup = false
    @State private var confirmingRestore = false
    @State private var selectedOption: AndroidTVOptions.Step?
    @State private var exportingRecovery = false
    @State private var confirmingCleanup = false

    init(preferences: PhonePreferences, setup: TVPackagePlatform? = nil, host: String = "", port: Int = 5555) {
        self.preferences = preferences
        self.setup = setup
        _android = StateObject(wrappedValue: AndroidTVSetup(address: host, port: port))
        _platform = State(initialValue: setup ?? .android)
    }

    private var connected: Bool { setup == .lg ? lg.connected : android.connected }
    private var setupBusy: Bool { setup == .lg ? lg.busy : android.busy }
    private var title: String {
        switch setup {
        case .lg: "lg_intro"
        case .android: "tv_adb_setup"
        case nil: "tv_public"
        }
    }

    var body: some View {
        Form {
            if setup == .lg {
                lgConnection
            } else if setup == .android {
                androidConnection
            } else {
                Picker(preferences.text("tv_platform"), selection: $platform) {
                    ForEach(TVPackagePlatform.allCases, id: \.self) { value in
                        if let name = Self.platforms.first(where: { $0.id == value.rawValue }) {
                            Text(preferences.arabic ? name.ar : name.en).tag(value)
                        }
                    }
                }
                .disabled(task != nil)
            }
            Picker(preferences.text("tv_public"), selection: $channel) {
                ForEach(TVPackageChannel.allCases, id: \.self) { value in
                    Text(preferences.text("tv_channel_" + value.rawValue)).tag(value)
                }
            }
            .disabled(task != nil || setupBusy)
            if let progress {
                switch progress {
                case .lookup: ProgressView(preferences.text("tv_public_lookup"))
                case .receiving(let fraction): ProgressView(preferences.text("tv_public_downloading"), value: fraction)
                case .verifying: ProgressView(preferences.text("tv_public_verifying"))
                }
            }
            if let status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if let package {
                LabeledContent(package.url.lastPathComponent, value: setup != nil ? (package.version ?? package.tag) : package.tag)
                if setup != nil {
                    Button(preferences.text("lg_install")) { confirmingInstall = true }
                        .disabled(!connected || setupBusy || package.version == nil)
                        .confirmationDialog(preferences.text("lg_install_confirm"), isPresented: $confirmingInstall,
                                            titleVisibility: .visible) {
                            Button(preferences.text("lg_install")) {
                                if setup == .lg { lg.install(package) }
                                else { android.install(package) }
                            }
                            Button(preferences.text("cancel"), role: .cancel) {}
                        }
                }
                ShareLink(item: package.url).disabled(setupBusy)
            }
            if let task {
                Button(preferences.text("cancel")) { task.cancel() }
            } else if !setupBusy {
                Button(preferences.text("tv_public_download")) { start() }
                    .disabled(setup != nil && !connected)
                Button(preferences.text("tv_local")) { importing = true }
            }
        }
        .navigationTitle(preferences.text(title))
        .onChange(of: platform) { _, _ in clearPackage() }
        .onChange(of: channel) { _, _ in clearPackage() }
        .onAppear { if setup == .lg { lg.refreshIdentity() } }
        .onDisappear {
            stopSetup()
            clearPackage()
        }
        .onChange(of: scenePhase) { _, phase in
            guard setup != nil, phase == .background else { return }
            confirmingInstall = false
            confirmingForget = false
            confirmingSetup = false
            confirmingRestore = false
            selectedOption = nil
            confirmingCleanup = false
            stopSetup()
        }
        .onChange(of: connected) { _, connected in
            if setup != nil && connected && package == nil && !importing && task == nil { start() }
        }
        .onChange(of: android.recoveryBackup) { _, backup in
            exportingRecovery = backup != nil
        }
        .sheet(item: $android.personalization) { draft in
            AndroidTVPersonalizationView(setup: android, preferences: preferences, snapshot: draft)
        }
        .fileExporter(isPresented: Binding(get: { exportingRecovery }, set: { presented in
            exportingRecovery = presented
            if !presented { android.finishRecoveryExport() }
        }),
                      document: android.recoveryBackup.map(TVRecoveryDocument.init(data:)),
                      contentType: .json, defaultFilename: "recovery") { result in
            if case .failure = result { android.finishRecoveryExport(failed: true) }
        }
        .alert(item: $lg.approval) { candidate in
            Alert(title: Text(preferences.text("lg_trust")),
                  message: Text(preferences.text("lg_trust_info") + "\n\n" + candidate.address + "\n" + candidate.fingerprint),
                  primaryButton: .default(Text(preferences.text("lg_trust"))) { lg.approve(candidate) },
                  secondaryButton: .cancel(Text(preferences.text("cancel"))))
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            switch result {
            case .success(let url): importLocal(url)
            case .failure: status = "tv_local_invalid"
            }
        }
    }

    private var androidConnection: some View {
        Section {
            DisclosureGroup(preferences.text("tv_adb_enable")) {
                Text(preferences.text("tv_adb_steps"))
            }
            if !android.connected {
                TextField(preferences.text("lg_address"), text: $android.address)
                    .keyboardType(.numbersAndPunctuation)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .environment(\.layoutDirection, .leftToRight)
                    .disabled(android.busy)
                TextField(preferences.text("tv_adb_port"), text: $android.port)
                    .keyboardType(.numberPad)
                    .environment(\.layoutDirection, .leftToRight)
                    .disabled(android.busy)
            }
            if let stage = android.stage {
                if let value = android.fraction { ProgressView(preferences.text(stage), value: value) }
                else { ProgressView(preferences.text(stage)) }
                Button(preferences.text("cancel")) { android.disconnect() }
            } else if android.connected {
                LabeledContent(preferences.text("lg_address"), value: android.address)
                if let version = android.installed {
                    LabeledContent(preferences.text("tv_adb_version"), value: String(version))
                    Button(preferences.text("lg_launch")) { android.launch() }.disabled(task != nil)
                    Button(preferences.text("tv_adb_configure")) { confirmingSetup = true }
                        .disabled(task != nil)
                        .confirmationDialog(preferences.text("tv_adb_configure_info"), isPresented: $confirmingSetup,
                                            titleVisibility: .visible) {
                            Button(preferences.text("tv_adb_configure")) { android.configureTV() }
                            Button(preferences.text("cancel"), role: .cancel) {}
                        }
                    androidOptions
                    Button(preferences.text("tv_adb_personal")) { android.reviewPersonalization() }.disabled(task != nil)
                    androidCleanup
                } else {
                    Text(preferences.text("lg_not_installed")).foregroundStyle(.secondary)
                }
                Button(preferences.text("tv_adb_restore")) { confirmingRestore = true }
                    .disabled(task != nil)
                    .confirmationDialog(preferences.text("tv_adb_restore_info"), isPresented: $confirmingRestore,
                                        titleVisibility: .visible) {
                        Button(preferences.text("tv_adb_restore")) { android.restoreTV() }
                        Button(preferences.text("cancel"), role: .cancel) {}
                    }
                Button(preferences.text("tv_adb_export")) { android.prepareRecoveryBackup() }
                    .disabled(task != nil)
                Button(preferences.text("lg_disconnect")) { stopSetup() }
            } else {
                Button(preferences.text("lg_connect")) { android.connect() }.disabled(task != nil || importing)
            }
            if let status = android.status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if ["lg_installed", "lg_launched", "tv_adb_configured", "tv_adb_personal_saved"].contains(android.status ?? "") {
                NavigationLink(preferences.text("tv_already")) { TVPairing(preferences: preferences) }
            }
        }
    }

    private var androidCleanup: some View {
        DisclosureGroup(preferences.text("tv_adb_cleanup")) {
            Button(preferences.text("tv_adb_cleanup_scan")) { android.reviewCleanup() }
            if let count = android.cleanupResult {
                LabeledContent(preferences.text("tv_adb_cleanup_disabled"), value: String(count))
            }
            if let candidates = android.cleanupCandidates {
                if candidates.isEmpty {
                    Text(preferences.text("tv_adb_cleanup_empty")).foregroundStyle(.secondary)
                }
                ForEach(candidates) { app in
                    Toggle(isOn: Binding(get: { android.cleanupSelection.contains(app.id) }, set: { value in
                        if value { android.cleanupSelection.insert(app.id) }
                        else { android.cleanupSelection.remove(app.id) }
                    })) {
                        VStack(alignment: .leading) {
                            Text(app.id).lineLimit(2)
                            Text(preferences.text("tv_adb_cleanup_" + app.kind)).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                Button(preferences.text("tv_adb_cleanup_apply")) { confirmingCleanup = true }
                    .disabled(android.cleanupSelection.isEmpty)
                    .confirmationDialog(preferences.text("tv_adb_cleanup_confirm"), isPresented: $confirmingCleanup,
                                        titleVisibility: .visible) {
                        Button(preferences.text("tv_adb_cleanup_apply")) { android.applyCleanup() }
                        Button(preferences.text("cancel"), role: .cancel) {}
                    }
            }
        }
        .disabled(task != nil)
    }

    private var androidOptions: some View {
        DisclosureGroup(preferences.text("tv_adb_optional")) {
            ForEach(AndroidTVOptions.bundled) { option in
                Button(preferences.arabic ? option.ar : option.en) { selectedOption = option }
                if let verified = android.optionStatus[option.id] {
                    Text(preferences.text(verified ? "tv_adb_option_verified" : "tv_adb_option_unconfirmed"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .disabled(task != nil)
        .confirmationDialog(preferences.text(selectedOption?.id.confirmation ?? "tv_adb_optional"),
                            isPresented: Binding(get: { selectedOption != nil }, set: { if !$0 { selectedOption = nil } }),
                            titleVisibility: .visible, presenting: selectedOption) { option in
            Button(preferences.arabic ? option.ar : option.en) { android.apply(option) }
            Button(preferences.text("cancel"), role: .cancel) {}
        }
    }

    private var lgConnection: some View {
        Section {
            DisclosureGroup(preferences.text("lg_setup_steps")) {
                Text(preferences.text("tv_lg_steps"))
            }
            if !lg.connected {
                TextField(preferences.text("lg_address"), text: $lg.address)
                    .keyboardType(.numbersAndPunctuation)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .environment(\.layoutDirection, .leftToRight)
                    .onChange(of: lg.address) { _, _ in lg.refreshIdentity() }
                    .disabled(lg.busy)
                SecureField(preferences.text(lg.savedPassphrase ? "lg_saved_passphrase" : "lg_passphrase"), text: $lg.passphrase)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .disabled(lg.busy)
                Toggle(preferences.text("lg_remember"), isOn: $lg.remember).disabled(lg.busy)
            }
            if let stage = lg.stage {
                if let fraction = lg.fraction { ProgressView(preferences.text(stage), value: fraction) }
                else { ProgressView(preferences.text(stage)) }
                Button(preferences.text("cancel")) { lg.disconnect() }
            } else if lg.connected {
                LabeledContent(preferences.text("lg_address"), value: lg.address)
                if let version = lg.installed {
                    LabeledContent(preferences.text("lg_current_version"), value: version)
                    Button(preferences.text("lg_launch")) { lg.launch() }.disabled(task != nil)
                } else {
                    Text(preferences.text("lg_not_installed")).foregroundStyle(.secondary)
                }
                Button(preferences.text("lg_disconnect")) {
                    task?.cancel()
                    lg.disconnect()
                }
            } else {
                Button(preferences.text("lg_connect")) { lg.connect() }.disabled(task != nil || importing)
                if lg.hasIdentity {
                    Button(preferences.text("lg_forget_key"), role: .destructive) { confirmingForget = true }
                        .confirmationDialog(preferences.text("lg_forget_key_info"), isPresented: $confirmingForget,
                                            titleVisibility: .visible) {
                            Button(preferences.text("lg_forget_key"), role: .destructive) { lg.forgetIdentity() }
                            Button(preferences.text("cancel"), role: .cancel) {}
                        }
                }
            }
            if let status = lg.status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if lg.storageError { Text(preferences.text("lg_save_failed")).foregroundStyle(.secondary) }
            if lg.status == "lg_launched" || lg.status == "lg_installed" {
                NavigationLink(preferences.text("tv_already")) { TVPairing(preferences: preferences) }
            }
        }
    }

    private func clearPackage() {
        package?.discard()
        package = nil
        status = nil
    }

    private func stopSetup() {
        selectedOption = nil
        confirmingCleanup = false
        if setup == .lg { lg.disconnect() }
        if setup == .android { android.disconnect() }
        task?.cancel()
    }

    private func start() {
        guard task == nil, setup == nil || (connected && !setupBusy) else { return }
        clearPackage()
        progress = .lookup
        let selection = (platform, channel)
        task = Task {
            defer {
                task = nil
                progress = nil
            }
            do {
                let ready = try await TVPackageDownload.download(platform: selection.0, channel: selection.1) { update in
                    await MainActor.run { if !Task.isCancelled { progress = update } }
                }
                if Task.isCancelled { ready.discard(); return }
                package = ready
            } catch TVReleaseError.unavailable {
                status = "tv_public_unavailable"
            } catch {
                status = Task.isCancelled ? "tv_public_cancelled" : "tv_public_failed"
            }
        }
    }

    private func importLocal(_ url: URL) {
        guard task == nil, !setupBusy else { return }
        clearPackage()
        progress = .verifying
        let selection = platform
        task = Task {
            defer { task = nil; progress = nil }
            do {
                let ready = try await TVPackageDownload.importLocal(url, platform: selection)
                if Task.isCancelled { ready.discard(); return }
                package = ready
            } catch {
                status = Task.isCancelled ? "tv_public_cancelled" : "tv_local_invalid"
            }
        }
    }
}

private struct TVRecoveryDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    let data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        guard let contents = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        data = contents
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
