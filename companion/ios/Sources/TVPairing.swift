import AVFoundation
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
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var store: PairedTVs
    let preferences: PhonePreferences
    @AppStorage("pairing-phone-name") private var savedPhoneName = "Miqat iPhone"
    @State private var phoneName = ""
    @State private var code = ""
    @State private var displayCode: String?
    @State private var scan = false
    @State private var cameraTask: Task<Void, Never>?
    @State private var task: Task<Void, Never>?
    @State private var failure: String?

    private var validName: String? {
        let name = phoneName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.utf16.count <= 64,
              name.rangeOfCharacter(from: .controlCharacters) == nil else { return nil }
        return name
    }

    var body: some View {
        Form {
            if let displayCode {
                Section {
                    Text(displayCode).font(.largeTitle.monospacedDigit())
                        .environment(\.layoutDirection, .leftToRight)
                    Text(preferences.text("pair_match"))
                    ProgressView(preferences.text("pair_waiting"))
                }
            } else if task != nil {
                ProgressView(preferences.text("pair_connecting"))
            } else {
                Text(preferences.text("tv_pair_steps")).font(.footnote)
                TextField(preferences.text("phone_name"), text: $phoneName)
                if DataScannerViewController.isSupported {
                    Button(preferences.text("scan_qr"), action: startScan)
                        .disabled(cameraTask != nil || validName == nil || store.inaccessible)
                    if failure == "camera_failed", AVCaptureDevice.authorizationStatus(for: .video) == .denied {
                        Button(preferences.text("settings")) {
                            if let settings = URL(string: UIApplication.openSettingsURLString) { openURL(settings) }
                        }
                    }
                }
                TextField(preferences.text("pair_verify"), text: $code, axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .privacySensitive()
                Button(preferences.text("pair")) { begin(code) }
                    .disabled(cameraTask != nil || code.isEmpty || validName == nil || store.inaccessible)
            }
            if let failure { Text(preferences.text(failure)).foregroundStyle(.secondary) }
            if store.inaccessible { Text(preferences.text("vault_failed")).foregroundStyle(.secondary) }
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
                if let result { begin(result) } else { failure = "camera_failed" }
            }
        }
        .onAppear { phoneName = savedPhoneName }
        .onDisappear { task?.cancel(); cameraTask?.cancel() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                task?.cancel()
                cameraTask?.cancel()
                scan = false
            }
        }
    }

    private func startScan() {
        guard cameraTask == nil, validName != nil, !store.inaccessible else { return }
        failure = nil
        cameraTask = Task {
            defer { cameraTask = nil }
            let authorization = AVCaptureDevice.authorizationStatus(for: .video)
            var allowed = authorization == .authorized
            if authorization == .notDetermined { allowed = await AVCaptureDevice.requestAccess(for: .video) }
            guard !Task.isCancelled else { return }
            if allowed && DataScannerViewController.isAvailable { scan = true }
            else { failure = "camera_failed" }
        }
    }

    private func begin(_ input: String) {
        guard task == nil, let name = validName, !store.inaccessible else { return }
        guard let qr = try? TVPairQR.decode(input) else { failure = "invalid_qr"; return }
        failure = nil
        savedPhoneName = name
        code = ""
        task = Task {
            defer { task = nil; displayCode = nil }
            do {
                try await pair(qr, name: name)
                dismiss()
            } catch {
                if !Task.isCancelled { failure = "pair_failed" }
            }
        }
    }

    private struct PairResponse: Decodable {
        let state: String
        let code: String?
        let id: String?
        let secret: String?
    }

    private func pair(_ pair: TVPairQR, name: String) async throws {
        let link = try TVConnection(pair.endpoint)
        defer { link.close() }
        if let saved = store.records.first(where: { $0.id == pair.pin }) {
            let repaired = PairedTV(endpoint: pair.endpoint, clientID: saved.clientID, secret: saved.secret,
                                   name: saved.name, profile: saved.profile, pending: saved.pending)
            let existing = try? await verify(repaired, link: link)
            if var verified = existing {
                try Task.checkCancellation()
                if !saved.pending { verified.name = saved.name }
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
        try Task.checkCancellation()
        let started = try await link.request("pair", as: PairResponse.self,
            body: ["token": pair.token, "nonce": nonce, "name": name])
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
            let confirmed = try await verify(grant, link: link)
            try Task.checkCancellation()
            try store.save(confirmed)
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
        let coordinator = context.coordinator
        scanner.delegate = coordinator
        Task { @MainActor in
            coordinator.start(scanner)
        }
        return scanner
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: DataScannerViewController, coordinator: Coordinator) {
        coordinator.dismissed()
        uiViewController.stopScanning()
    }

    @MainActor final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let receive: (String?) -> Void
        private var delivered = false
        init(_ receive: @escaping (String?) -> Void) { self.receive = receive }

        func start(_ scanner: DataScannerViewController) {
            guard !delivered else { return }
            do { try scanner.startScanning() } catch { finish(scanner, result: nil) }
        }

        func dismissed() { delivered = true }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for item in addedItems {
                if case .barcode(let barcode) = item, let code = barcode.payloadStringValue {
                    finish(dataScanner, result: code)
                    return
                }
            }
        }

        func dataScanner(_ dataScanner: DataScannerViewController,
                         becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable) {
            finish(dataScanner, result: nil)
        }

        private func finish(_ scanner: DataScannerViewController, result: String?) {
            guard !delivered else { return }
            delivered = true
            scanner.stopScanning()
            receive(result)
        }
    }
}
