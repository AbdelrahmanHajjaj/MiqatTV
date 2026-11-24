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
    private let setupLG: Bool
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var lg = LGSetup()
    @State private var platform: TVPackagePlatform
    @State private var channel = TVPackageChannel.production
    @State private var task: Task<Void, Never>?
    @State private var progress: TVPackageDownload.Progress?
    @State private var status: String?
    @State private var package: TVPackageDownload.Package?
    @State private var importing = false
    @State private var confirmingInstall = false
    @State private var confirmingForget = false

    init(preferences: PhonePreferences, setupLG: Bool = false) {
        self.preferences = preferences
        self.setupLG = setupLG
        _platform = State(initialValue: setupLG ? .lg : .android)
    }

    var body: some View {
        Form {
            if setupLG {
                lgConnection
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
            .disabled(task != nil || lg.busy)
            if let progress {
                switch progress {
                case .lookup: ProgressView(preferences.text("tv_public_lookup"))
                case .receiving(let fraction): ProgressView(preferences.text("tv_public_downloading"), value: fraction)
                case .verifying: ProgressView(preferences.text("tv_public_verifying"))
                }
            }
            if let status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if let package {
                LabeledContent(package.url.lastPathComponent, value: setupLG ? (package.version ?? package.tag) : package.tag)
                if setupLG {
                    Button(preferences.text("lg_install")) { confirmingInstall = true }
                        .disabled(!lg.connected || lg.busy || package.version == nil)
                        .confirmationDialog(preferences.text("lg_install_confirm"), isPresented: $confirmingInstall,
                                            titleVisibility: .visible) {
                            Button(preferences.text("lg_install")) { lg.install(package) }
                            Button(preferences.text("cancel"), role: .cancel) {}
                        }
                }
                ShareLink(item: package.url).disabled(lg.busy)
            }
            if let task {
                Button(preferences.text("cancel")) { task.cancel() }
            } else if !lg.busy {
                Button(preferences.text("tv_public_download")) { start() }
                    .disabled(setupLG && !lg.connected)
                Button(preferences.text("tv_local")) { importing = true }
            }
        }
        .navigationTitle(preferences.text(setupLG ? "lg_intro" : "tv_public"))
        .onChange(of: platform) { _, _ in clearPackage() }
        .onChange(of: channel) { _, _ in clearPackage() }
        .onAppear { if setupLG { lg.refreshIdentity() } }
        .onDisappear {
            if setupLG { lg.disconnect() }
            task?.cancel()
            clearPackage()
        }
        .onChange(of: scenePhase) { _, phase in
            guard setupLG, phase == .background else { return }
            confirmingInstall = false
            confirmingForget = false
            lg.disconnect()
            task?.cancel()
        }
        .onChange(of: lg.connected) { _, connected in
            if setupLG && connected && package == nil && !importing && task == nil { start() }
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

    private func start() {
        guard task == nil, !setupLG || (lg.connected && !lg.busy) else { return }
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
        guard task == nil, !lg.busy else { return }
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
