import Foundation
import SwiftUI

@MainActor
final class AndroidTVSetup: ObservableObject {
    @Published var address: String
    @Published var port: String
    @Published private(set) var connected = false
    @Published private(set) var installed: UInt64?
    @Published private(set) var status: String?
    @Published private(set) var stage: String?
    @Published private(set) var fraction: Double?
    @Published private(set) var optionStatus: [AndroidTVOptions.ID: Bool] = [:]
    @Published private(set) var recoveryBackup: Data?
    @Published private(set) var cleanupCandidates: [AndroidTVCleanup.Candidate]?
    @Published var cleanupSelection: Set<String> = []
    @Published private(set) var cleanupResult: Int?
    private var client: AndroidADB?
    private var task: Task<Void, Never>?

    var busy: Bool { stage != nil }

    init(address: String = "", port: Int = 5555) {
        self.address = address
        self.port = String(port)
    }

    func connect() {
        guard !connected, !busy else { return }
        let host = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard TVEndpoint.isPrivateIPv4(host), let number = Int(port), (1...65535).contains(number) else {
            status = "tv_adb_invalid_address"
            return
        }
        run("tv_adb_connecting") {
            let opened = try await AndroidADB.open(host: host, port: number) { await self.requestApproval() }
            do {
                let version = try await Self.installer(opened).installedVersion()
                try Task.checkCancellation()
                self.client = opened
                self.installed = version
                self.connected = true
            } catch {
                await opened.close()
                throw error
            }
        }
    }

    func install(_ package: TVPackageDownload.Package) {
        guard let client, let version = package.version, !busy else { return }
        run("lg_sending") {
            try await Self.installer(client).install(package.url, version: version) { await self.report($0) }
            try Task.checkCancellation()
            self.installed = UInt64(version)
            self.optionStatus = [:]
            self.status = "lg_installed"
        }
    }

    func launch() {
        guard let client, installed != nil, !busy else { return }
        run("lg_launching") {
            try await Self.installer(client).launch()
            try Task.checkCancellation()
            self.status = "lg_launched"
        }
    }

    func configureTV() {
        guard let client, installed != nil, !busy else { return }
        run("tv_adb_configuring") {
            try await Self.recovery(client).configure()
            try Task.checkCancellation()
            self.status = "tv_adb_configured"
        }
    }

    func restoreTV() {
        guard let client, !busy else { return }
        run("tv_adb_restoring") {
            try await Self.recovery(client).restore()
            try Task.checkCancellation()
            self.resetCleanup()
            self.status = "tv_adb_restored"
        }
    }

    func reviewCleanup() {
        guard let client, installed != nil, !busy else { return }
        resetCleanup()
        run("tv_adb_cleanup_loading") {
            let list = try await Self.recovery(client).cleanupReview(catalog: AndroidTVCleanup.bundled)
            try Task.checkCancellation()
            self.cleanupCandidates = list
            self.cleanupSelection = Set(list.filter(\.suggested).map(\.id))
        }
    }

    func applyCleanup() {
        guard let client, installed != nil, !busy, !cleanupSelection.isEmpty else { return }
        let selection = cleanupSelection
        resetCleanup()
        run("tv_adb_cleanup_working") {
            let count = try await Self.recovery(client).clean(selection, catalog: AndroidTVCleanup.bundled)
            try Task.checkCancellation()
            self.cleanupResult = count
        }
    }

    func apply(_ option: AndroidTVOptions.Step) {
        guard let client, installed != nil, !busy else { return }
        run("tv_adb_applying_option") {
            let confirmed = try await option.apply(using: Self.installer(client))
            try Task.checkCancellation()
            self.optionStatus[option.id] = confirmed
        }
    }

    func prepareRecoveryBackup() {
        guard let client, !busy else { return }
        recoveryBackup = nil
        run("tv_adb_preparing_backup") {
            let data = try await Self.recovery(client).export()
            try Task.checkCancellation()
            self.recoveryBackup = data
        }
    }

    func finishRecoveryExport(failed: Bool = false) {
        recoveryBackup = nil
        if failed { status = "tv_adb_export_failed" }
    }

    func disconnect() {
        task?.cancel()
        task = nil
        resetConnection()
        stage = nil
        fraction = nil
        status = "lg_reconnect"
    }

    private static func installer(_ client: AndroidADB) -> AndroidTVInstallation {
        AndroidTVInstallation { command, input, progress in
            try await client.exchange(command, input: input, progress: progress)
        }
    }

    private static func recovery(_ client: AndroidADB) -> AndroidTVRecovery {
        let installer = installer(client)
        return AndroidTVRecovery { command in try await installer.shell(command) }
    }

    private func resetConnection() {
        if let client { Task { await client.close() } }
        client = nil
        connected = false
        installed = nil
        optionStatus = [:]
        recoveryBackup = nil
        resetCleanup()
    }

    private func resetCleanup() {
        cleanupCandidates = nil
        cleanupSelection = []
        cleanupResult = nil
    }

    private func run(_ label: String, operation: @escaping @MainActor () async throws -> Void) {
        stage = label
        status = nil
        task = Task {
            defer {
                if !Task.isCancelled {
                    task = nil
                    stage = nil
                    fraction = nil
                }
            }
            do {
                try await operation()
            } catch AndroidADB.Failure.wirelessRequired {
                if !Task.isCancelled { status = "tv_adb_wireless_required" }
            } catch AndroidTVInstallation.Failure.downgrade {
                if !Task.isCancelled { status = "tv_adb_downgrade" }
            } catch AndroidTVRecovery.Failure.noRecord {
                if !Task.isCancelled { status = "tv_adb_no_recovery" }
            } catch AndroidTVRecovery.Failure.unsupportedTV {
                if !Task.isCancelled { status = "tv_adb_setup_unsupported" }
            } catch AndroidTVRecovery.Failure.cleanupNotReady {
                if !Task.isCancelled { status = "tv_adb_cleanup_unavailable" }
            } catch {
                guard !Task.isCancelled else { return }
                resetConnection()
                status = "tv_adb_failed"
            }
        }
    }

    private func requestApproval() {
        if !Task.isCancelled { stage = "tv_adb_approval" }
    }

    private func report(_ update: AndroidTVInstallation.Stage) {
        guard !Task.isCancelled else { return }
        switch update {
        case .sending(let value): stage = "lg_sending"; fraction = value
        case .verifying: stage = "lg_verifying_install"; fraction = nil
        }
    }
}
