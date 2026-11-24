import Foundation
import Security
import SwiftUI
import VisionKit

struct TVIdentity: Decodable {
    let protocolVersion: Int
    let profile: String
    let name: String
    private enum CodingKeys: String, CodingKey { case protocolVersion = "protocol", profile, name }
    var valid: Bool { protocolVersion == 1 && ["android", "lg"].contains(profile) && !name.isEmpty && name.utf8.count <= 256 }
}

@MainActor
final class PairedTVs: ObservableObject {
    @Published private(set) var records: [PairedTV] = []
    @Published private(set) var inaccessible = false
    @Published var settingsEdits: [String: TVSettingEdit] = [:]

    func load() {
        do {
            records = try TVKeychain.load()
            inaccessible = false
        } catch {
            inaccessible = true
        }
    }

    func save(_ record: PairedTV) throws {
        guard !inaccessible else { throw TVConnectionError.credentialStorage }
        let next = records.filter { $0.id != record.id } + [record]
        try TVKeychain.save(next)
        records = next
    }

    func remove(_ record: PairedTV) throws {
        guard !inaccessible else { throw TVConnectionError.credentialStorage }
        let next = records.filter { $0.id != record.id }
        try TVKeychain.save(next)
        records = next
        settingsEdits[record.id] = nil
    }

    func rename(_ id: String, to name: String) throws {
        guard !inaccessible, let index = records.firstIndex(where: { $0.id == id && !$0.pending }) else {
            throw TVConnectionError.credentialStorage
        }
        var next = records
        next[index].name = name
        try TVKeychain.save(next)
        records = next
    }
}

struct TVPairing: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    @State private var code = ""
    @State private var displayCode: String?
    @State private var scan = false
    @State private var task: Task<Void, Never>?
    @State private var failed = false

    var body: some View {
        Form {
            if let displayCode {
                Section {
                    Text(displayCode).font(.largeTitle.monospacedDigit())
                    Text(preferences.text("pair_match"))
                    ProgressView(preferences.text("pair_waiting"))
                }
            } else if task != nil {
                ProgressView(preferences.text("pair_connecting"))
            } else {
                if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    Button(preferences.text("scan_qr")) { scan = true }
                }
                TextField(preferences.text("pair_verify"), text: $code, axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .privacySensitive()
                Button(preferences.text("pair")) { begin(code) }
                    .disabled(code.isEmpty || store.inaccessible)
            }
            if failed { Text(preferences.text("pair_failed")).foregroundStyle(.secondary) }
        }
        .navigationTitle(preferences.text("pair"))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(preferences.text("cancel")) { task?.cancel(); dismiss() }
            }
        }
        .sheet(isPresented: $scan) {
            TVCodeScanner { result in
                scan = false
                if let result { begin(result) } else { failed = true }
            }
        }
        .onDisappear { task?.cancel() }
        .onChange(of: scenePhase) { _, phase in if phase == .background { task?.cancel() } }
    }

    private func begin(_ input: String) {
        guard task == nil else { return }
        failed = false
        code = ""
        task = Task {
            defer { task = nil; displayCode = nil }
            do {
                try await pair(input)
                dismiss()
            } catch {
                failed = !Task.isCancelled
            }
        }
    }

    private struct PairResponse: Decodable {
        let state: String
        let code: String?
        let id: String?
        let secret: String?
    }

    private func pair(_ input: String) async throws {
        let pair = try TVPairQR.decode(input)
        let link = try TVConnection(pair.endpoint)
        defer { link.close() }
        if let saved = store.records.first(where: { $0.id == pair.pin }) {
            let repaired = PairedTV(endpoint: pair.endpoint, clientID: saved.clientID, secret: saved.secret,
                                   name: saved.name, profile: saved.profile, pending: saved.pending)
            let existing = try? await verify(repaired, link: link)
            if let verified = existing {
                try Task.checkCancellation()
                try store.save(verified)
                return
            }
        }
        var bytes = Data(count: 32)
        let rngStatus = bytes.withUnsafeMutableBytes { (buffer: UnsafeMutableRawBufferPointer) in
            SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
        }
        guard rngStatus == errSecSuccess else { throw TVConnectionError.invalid }
        let nonce = bytes.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let digest = TVPairQR.hexHash(Data((pair.token + ":" + nonce).utf8))
        guard let first = UInt32(digest.prefix(8), radix: 16) else { throw TVConnectionError.invalid }
        let expected = String(format: "%06u", first % 1_000_000)
        let started = try await link.request("pair", as: PairResponse.self,
            body: ["token": pair.token, "nonce": nonce, "name": "Miqat iPhone"])
        guard started.state == "pending", started.code == expected else { throw TVConnectionError.unconfirmed }
        displayCode = expected
        let deadline = ContinuousClock.now + .milliseconds(TVProtocol.current.pairWindowMillis)
        while ContinuousClock.now < deadline {
            try await Task.sleep(for: .seconds(1))
            let status = try await link.request("pair-status", as: PairResponse.self, body: ["token": pair.token, "nonce": nonce])
            if status.state == "pending" { continue }
            guard status.state == "approved", let id = status.id, let secret = status.secret,
                  TVPairQR.tokenShape(id), TVPairQR.tokenShape(secret) else { throw TVConnectionError.unconfirmed }
            let grant = PairedTV(endpoint: pair.endpoint, clientID: id, secret: secret, name: "TV", profile: "", pending: true)
            try Task.checkCancellation()
            try store.save(grant)
            try store.save(await verify(grant, link: link))
            return
        }
        throw TVConnectionError.unconfirmed
    }

    private func verify(_ record: PairedTV, link: TVConnection) async throws -> PairedTV {
        if record.pending {
            _ = try? await link.request("pair-complete", as: PairResponse.self, body: [:], credential: record.credential)
        }
        try Task.checkCancellation()
        let identity = try await link.request("info", as: TVIdentity.self, credential: record.credential)
        guard identity.valid else { throw TVConnectionError.unconfirmed }
        var confirmed = record
        confirmed.pending = false
        confirmed.name = identity.name
        confirmed.profile = identity.profile
        return confirmed
    }
}

struct TVCodeScanner: UIViewControllerRepresentable {
    let scanned: (String?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(scanned) }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced, recognizesMultipleItems: false, isGuidanceEnabled: true, isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        Task { @MainActor in
            do { try scanner.startScanning() } catch { scanned(nil) }
        }
        return scanner
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: DataScannerViewController, coordinator: Coordinator) {
        uiViewController.stopScanning()
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let receive: (String?) -> Void
        private var delivered = false
        init(_ receive: @escaping (String?) -> Void) { self.receive = receive }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for item in addedItems {
                if case .barcode(let barcode) = item, let code = barcode.payloadStringValue {
                    delivered = true
                    dataScanner.stopScanning()
                    receive(code)
                    return
                }
            }
        }
    }
}
