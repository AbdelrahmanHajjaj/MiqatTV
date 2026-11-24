import CryptoKit
import Foundation
import SwiftUI

@MainActor
final class LGSetup: ObservableObject {
    struct Candidate: Identifiable {
        let address: String
        let hostKey: Data
        var id: String { address }
        var fingerprint: String {
            "SHA256:" + Data(SHA256.hash(data: hostKey)).base64EncodedString().replacingOccurrences(of: "=", with: "")
        }
    }

    private static let lastAddress = "lg-installation-address"
    @Published var address = UserDefaults.standard.string(forKey: LGSetup.lastAddress) ?? ""
    @Published var passphrase = ""
    @Published var remember = false
    @Published var approval: Candidate?
    @Published private(set) var savedPassphrase = false
    @Published private(set) var hasIdentity = false
    @Published private(set) var connected = false
    @Published private(set) var installed: String?
    @Published private(set) var status: String?
    @Published private(set) var storageError = false
    @Published private(set) var stage: String?
    @Published private(set) var fraction: Double?
    private var client: LGSSH?
    private var task: Task<Void, Never>?

    var busy: Bool { stage != nil }

    func refreshIdentity() {
        let record: LGInstallCredentials.Record?
        do {
            if let host = LGSSH.privateAddress(address) { record = try LGInstallCredentials.load(host) }
            else { record = nil }
            storageError = false
        } catch {
            record = nil
            storageError = true
        }
        hasIdentity = record != nil
        savedPassphrase = record?.passphrase != nil
        remember = savedPassphrase
    }

    func connect() {
        guard !connected, !busy else { return }
        guard let host = LGSSH.privateAddress(address) else { status = "lg_invalid_host"; return }
        let record: LGInstallCredentials.Record?
        do { record = try LGInstallCredentials.load(host) }
        catch { storageError = true; return }

        let phrase = passphrase.isEmpty ? (record?.passphrase ?? "") : passphrase
        guard !phrase.isEmpty else { status = "lg_passphrase_required"; return }
        let keep = remember
        run("lg_connecting") {
            let opened = try await LGDeveloperMode.connect(address: host, approvedKey: record?.hostKey, passphrase: phrase)
            var accepted = false
            defer { if !accepted { opened.close() } }
            let version = try await LGInstallation(transport: opened).installedVersion()
            try Task.checkCancellation()
            self.passphrase = ""
            var saved = record
            if saved?.passphrase != (keep ? phrase : nil) {
                saved?.passphrase = keep ? phrase : nil
                if let saved {
                    do {
                        try LGInstallCredentials.save(saved, for: host)
                        self.savedPassphrase = saved.passphrase != nil
                        self.storageError = false
                    } catch {
                        self.storageError = true
                    }
                }
            }
            if keep && self.savedPassphrase { UserDefaults.standard.set(host, forKey: Self.lastAddress) }
            else if UserDefaults.standard.string(forKey: Self.lastAddress) == host {
                UserDefaults.standard.removeObject(forKey: Self.lastAddress)
            }
            self.client = opened
            accepted = true
            self.connected = true
            self.installed = version
            self.status = "lg_connected"
        }
    }

    func approve(_ candidate: Candidate) {
        guard !busy, !connected, LGSSH.privateAddress(address) == candidate.address else { return }
        do {
            guard try LGInstallCredentials.load(candidate.address) == nil else { return }
            try LGInstallCredentials.save(.init(hostKey: candidate.hostKey, passphrase: nil), for: candidate.address)
            hasIdentity = true
            storageError = false
            connect()
        } catch {
            storageError = true
        }
    }

    func forgetIdentity() {
        guard !busy, !connected, let host = LGSSH.privateAddress(address) else { return }
        do {
            try LGInstallCredentials.forget(host)
            if UserDefaults.standard.string(forKey: Self.lastAddress) == host {
                UserDefaults.standard.removeObject(forKey: Self.lastAddress)
            }
            approval = nil
            passphrase = ""
            status = nil
            refreshIdentity()
        } catch {
            storageError = true
        }
    }

    func install(_ package: TVPackageDownload.Package) {
        guard let client, let version = package.version, !busy else { return }
        run("lg_sending") {
            try await LGInstallation(transport: client).install(package.url, version: version) { update in
                await self.report(update)
            }
            try Task.checkCancellation()
            self.installed = version
            self.status = "lg_installed"
        }
    }

    func launch() {
        guard let client, installed != nil, !busy else { return }
        run("lg_launching") {
            try await LGInstallation(transport: client).launch()
            try Task.checkCancellation()
            self.status = "lg_launched"
        }
    }

    func disconnect() {
        task?.cancel()
        task = nil
        approval = nil
        passphrase = ""
        resetConnection()
        stage = nil
        fraction = nil
        status = "lg_reconnect"
    }

    private func resetConnection() {
        client?.close()
        client = nil
        connected = false
        installed = nil
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
            } catch LGSSH.Failure.approveHostKey(let key) {
                guard !Task.isCancelled, let host = LGSSH.privateAddress(address) else { return }
                approval = Candidate(address: host, hostKey: key)
            } catch LGSSH.Failure.changedHostKey {
                if !Task.isCancelled { status = "lg_changed_key" }
            } catch LGInstallation.Failure.downgrade {
                if !Task.isCancelled { status = "lg_downgrade" }
            } catch {
                guard !Task.isCancelled else { return }
                resetConnection()
                status = "lg_failed"
            }
        }
    }

    private func report(_ update: LGInstallation.Stage) {
        guard !Task.isCancelled else { return }
        fraction = nil
        switch update {
        case .sending(let value): stage = "lg_sending"; fraction = value
        case .checkingTransfer: stage = "lg_checking_transfer"
        case .installing: stage = "lg_installing"
        case .verifyingVersion: stage = "lg_verifying_install"
        }
    }
}
