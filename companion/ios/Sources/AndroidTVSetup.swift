import Foundation
import SwiftUI

@MainActor
final class AndroidTVSetup: ObservableObject {
    struct WirelessApproval: Identifiable {
        let id = UUID()
        let endpoint: String
        let fingerprint: String
        fileprivate let client: AndroidADB
    }

    @Published var address: String
    @Published var port: String
    @Published var wireless: Bool
    @Published var wirelessPort: String
    @Published var pairingPort: String
    @Published var pairingCode = ""
    @Published var wirelessApproval: WirelessApproval?
    @Published private var pairedHost: String?
    @Published private(set) var connected = false
    @Published private(set) var installed: UInt64?
    @Published private(set) var status: String?
    @Published private(set) var stage: String?
    @Published private(set) var fraction: Double?
    @Published private(set) var optionStatus: [AndroidTVOptions.ID: Bool] = [:]
    @Published private(set) var recoveryBackup: Data?
    @Published private(set) var cleanupCandidates: [AndroidTVCleanup.Candidate]?
    @Published private(set) var cleanupDisabledEarlier: [String] = []
    @Published var cleanupSelection: Set<String> = []
    @Published private(set) var cleanupResult: Int?
    @Published var personalization: AndroidTVPersonalization.Snapshot?
    private var client: AndroidADB?
    private var pairedGUID: String?
    private var task: Task<Void, Never>?

    var busy: Bool { stage != nil }
    var paired: Bool { pairedHost == host }
    private var host: String { address.trimmingCharacters(in: .whitespacesAndNewlines) }

    init(address: String = "", port: Int = 5555, wireless: Bool = false, pairingPort: Int? = nil) {
        self.address = address
        self.port = wireless ? "5555" : String(port)
        self.wireless = wireless
        wirelessPort = wireless && port > 0 ? String(port) : ""
        self.pairingPort = pairingPort.map { String($0) } ?? ""
    }

    func pair() {
        guard wireless, !connected, !busy, wirelessApproval == nil else { return }
        let host = self.host
        guard TVEndpoint.isPrivateIPv4(host), let port = Int(pairingPort), (1...65535).contains(port) else {
            status = "tv_adb_invalid_address"
            return
        }
        let code = pairingCode
        pairingCode = ""
        pairedHost = nil
        pairedGUID = nil
        run("tv_adb_pairing") {
            do {
                let guid = try await AndroidADBPairing.pair(host: host, port: port, code: code)
                try Task.checkCancellation()
                self.pairedGUID = guid
                self.pairedHost = host
                self.status = "tv_adb_paired"
            } catch {
                if !Task.isCancelled { self.status = "tv_adb_pairing_failed" }
            }
        }
    }

    func connect() {
        guard !connected, !busy, wirelessApproval == nil else { return }
        let host = self.host
        let wireless = self.wireless
        let guid = pairedGUID
        guard !wireless || (paired && guid != nil) else {
            status = "tv_adb_wireless_required"
            return
        }
        guard TVEndpoint.isPrivateIPv4(host), let number = Int(wireless ? wirelessPort : port),
              (1...65535).contains(number), !wireless || number != Int(pairingPort) else {
            status = "tv_adb_invalid_address"
            return
        }
        run("tv_adb_connecting") {
            if wireless, let guid {
                let candidate = try await AndroidADB.openWireless(host: host, port: number)
                do {
                    let found = try await Self.installer(candidate.client).shell("getprop persist.adb.wifi.guid")
                    guard found == guid else { throw AndroidADB.Failure.authorization }
                    try Task.checkCancellation()
                    self.wirelessApproval = WirelessApproval(
                        endpoint: "\(host):\(number)",
                        fingerprint: candidate.digest.map { String(format: "%02X", $0) }.joined(separator: ":"),
                        client: candidate.client
                    )
                } catch {
                    await candidate.client.close()
                    throw error
                }
            } else {
                let opened = try await AndroidADB.open(host: host, port: number) { await self.requestApproval() }
                try await self.finishConnect(opened)
            }
        }
    }

    func approveWireless(_ candidate: WirelessApproval) {
        guard !connected, !busy, wireless, paired,
              candidate.endpoint == "\(host):\(Int(wirelessPort) ?? 0)" else { return }
        wirelessApproval = nil
        run("tv_adb_connecting") { try await self.finishConnect(candidate.client) }
    }

    private func finishConnect(_ opened: AndroidADB) async throws {
        do {
            let version = try await Self.installer(opened).installedVersion()
            try Task.checkCancellation()
            client = opened
            installed = version
            connected = true
        } catch {
            await opened.close()
            throw error
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
            let review = try await Self.recovery(client).cleanupReview(catalog: AndroidTVCleanup.bundled)
            try Task.checkCancellation()
            self.cleanupCandidates = review.candidates
            self.cleanupDisabledEarlier = review.disabledEarlier
            self.cleanupSelection = Set(review.candidates.filter(\.suggested).map(\.id))
        }
    }

    func undoCleanup() {
        guard let client, !busy, !cleanupDisabledEarlier.isEmpty else { return }
        let reviewed = cleanupDisabledEarlier
        run("tv_adb_restoring") {
            try await Self.recovery(client).undoCleanup(reviewed)
            try Task.checkCancellation()
            self.resetCleanup()
            self.status = "tv_adb_cleanup_restored"
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

    func reviewPersonalization() {
        guard let client, installed != nil, !busy else { return }
        run("tv_adb_personal_loading") {
            let current = try await Self.personalizer(client).load()
            try Task.checkCancellation()
            self.personalization = current
        }
    }

    func savePersonalization(_ draft: AndroidTVPersonalization.Draft, from baseline: AndroidTVPersonalization.Snapshot) {
        guard let client, !busy, personalization?.id == baseline.id else { return }
        run("tv_adb_personal_saving") {
            try await Self.personalizer(client).save(draft, from: baseline)
            try Task.checkCancellation()
            self.personalization = nil
            self.resetCleanup()
            self.status = "tv_adb_personal_saved"
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
        pairingCode = ""
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

    private static func personalizer(_ client: AndroidADB) -> AndroidTVPersonalization {
        let installer = installer(client)
        return AndroidTVPersonalization(provider: .init { try await installer.shell($0) })
    }

    private func resetConnection() {
        if let client { Task { await client.close() } }
        if let candidate = wirelessApproval { Task { await candidate.client.close() } }
        wirelessApproval = nil
        client = nil
        connected = false
        installed = nil
        optionStatus = [:]
        recoveryBackup = nil
        resetCleanup()
        personalization = nil
    }

    private func resetCleanup() {
        cleanupCandidates = nil
        cleanupDisabledEarlier = []
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
            } catch AndroidTVPersonalization.Failure.changed {
                guard !Task.isCancelled else { return }
                personalization = nil
                status = "tv_adb_personal_changed"
            } catch AndroidTVPersonalization.Failure.unavailable {
                guard !Task.isCancelled else { return }
                personalization = nil
                status = "tv_adb_personal_unavailable"
            } catch AndroidTVPersonalization.Failure.unconfirmed {
                guard !Task.isCancelled else { return }
                personalization = nil
                status = "tv_adb_personal_unconfirmed"
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
