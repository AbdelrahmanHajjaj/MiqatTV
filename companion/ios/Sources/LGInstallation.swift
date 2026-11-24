import CryptoKit
import Foundation

struct LGInstallation {
    static let responseLimit = 1024 * 1024
    private static let folder = "/media/developer/temp"

    enum Failure: Error, Equatable {
        case changedPackage, downgrade, transferMismatch, invalidResponse, refused, unconfirmed
    }

    enum Stage: Sendable {
        case sending(Double), checkingTransfer, installing, verifyingVersion
    }

    struct Command: Sendable {
        let text: String
        let input: URL?
        let streamsInstallStatus: Bool

        fileprivate init(_ text: String, input: URL? = nil, streamsInstallStatus: Bool = false) {
            self.text = text
            self.input = input
            self.streamsInstallStatus = streamsInstallStatus
        }
    }

    // The shell runs after a pinned SSH login and stops streaming on a terminal status.
    protocol Transport: Sendable {
        func run(_ command: Command, progress: @Sendable (Double) async -> Void) async throws -> Data
    }

    let transport: any Transport

    func installedVersion() async throws -> String? {
        let data = try await execute(Self.luna("com.webos.applicationManager/dev/listApps", json: #"{"subscribe":false}"#))
        guard let apps = try Self.reply(data).apps else { throw Failure.invalidResponse }
        let matches = apps.filter { $0.id == LgPackage.packageID }
        guard matches.count <= 1 else { throw Failure.invalidResponse }
        guard let match = matches.first else { return nil }
        guard let version = match.version else { throw Failure.invalidResponse }
        _ = try LgPackage.versionComponents(version)
        return version
    }

    func install(_ file: URL, version: String, report: @Sendable (Stage) async -> Void) async throws {
        guard try LgPackage.version(of: file) == version else { throw Failure.changedPackage }
        if let current = try await installedVersion(),
           try LgPackage.versionComponents(version).lexicographicallyPrecedes(LgPackage.versionComponents(current)) {
            throw Failure.downgrade
        }

        let digest = try Self.transferDigest(file)
        let remote = "\(Self.folder)/miqat-\(UUID().uuidString.lowercased()).ipk"
        _ = try await execute(Command("mkdir -p \(Self.folder)"))
        await report(.sending(0))
        try Task.checkCancellation()
        _ = try await transport.run(Command("cat > \(remote)", input: file)) { progress in
            await report(.sending(min(1, max(0, progress))))
        }
        await report(.sending(1))
        await report(.checkingTransfer)
        let hashReply = try await execute(Command("md5sum \(remote)"))
        let remoteHash = String(data: hashReply, encoding: .ascii)?.split(whereSeparator: \.isWhitespace).first
        guard remoteHash == digest[...] else { throw Failure.transferMismatch }

        await report(.installing)
        let install = Self.luna("com.webos.appInstallService/dev/install",
                                json: #"{"id":"\#(LgPackage.packageID)","ipkUrl":"\#(remote)","subscribe":true}"#,
                                streaming: true)
        let response = try await execute(install)
        guard try Self.installCompleted(response) else { throw Failure.unconfirmed }

        await report(.verifyingVersion)
        guard try await installedVersion() == version else { throw Failure.unconfirmed }
        if !Task.isCancelled { _ = try? await execute(Command("rm -f \(remote)")) }
    }

    func launch() async throws {
        let data = try await execute(Self.luna("com.webos.applicationManager/launch", json: #"{"id":"\#(LgPackage.packageID)"}"#))
        _ = try Self.reply(data)
    }

    private func execute(_ command: Command) async throws -> Data {
        try Task.checkCancellation()
        let response = try await transport.run(command) { _ in }
        try Task.checkCancellation()
        guard response.count <= Self.responseLimit else { throw Failure.invalidResponse }
        return response
    }

    private static func luna(_ endpoint: String, json: String, streaming: Bool = false) -> Command {
        let mode = streaming ? "-i" : "-n 1"
        return Command("/usr/bin/luna-send-pub \(mode) luna://\(endpoint) '\(json)'", streamsInstallStatus: streaming)
    }

    private static func transferDigest(_ file: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: file)
        defer { try? input.close() }
        var size = 0
        var hash = Insecure.MD5()
        while let bytes = try input.read(upToCount: 65536), !bytes.isEmpty {
            try Task.checkCancellation()
            size += bytes.count
            guard size <= LgPackage.maximumSize else { throw Failure.changedPackage }
            hash.update(data: bytes)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private struct Reply: Decodable {
        struct App: Decodable { let id: String; let version: String? }
        struct Details: Decodable { let state: String? }
        let returnValue: Bool?
        let details: Details?
        let apps: [App]?
    }

    private static func reply(_ data: Data) throws -> Reply {
        let parsed = try frames(data)
        guard !parsed.incomplete, parsed.replies.count == 1, let reply = parsed.replies.first else {
            throw Failure.invalidResponse
        }
        guard reply.returnValue == true else { throw Failure.refused }
        return reply
    }

    static func installCompleted(_ data: Data) throws -> Bool {
        let parsed = try frames(data)
        var confirmed = false
        for frame in parsed.replies {
            let state = frame.details?.state?.uppercased() ?? ""
            if frame.returnValue == false || state.contains("FAILED") { throw Failure.refused }
            if frame.returnValue == true, state == "INSTALLED" || state.hasPrefix("SUCCESS") { confirmed = true }
        }
        return confirmed
    }

    private static func frames(_ data: Data) throws -> (replies: [Reply], incomplete: Bool) {
        guard data.count <= responseLimit else { throw Failure.invalidResponse }
        var result: [Reply] = []
        var start: Data.Index?
        var stack: [UInt8] = []
        var quoted = false
        var escape = false
        let decoder = JSONDecoder()
        for index in data.indices {
            let byte = data[index]
            guard let begin = start else {
                if byte == 9 || byte == 10 || byte == 13 || byte == 32 { continue }
                guard byte == 123, result.count < 1024 else { throw Failure.invalidResponse }
                start = index
                stack = [125]
                continue
            }
            if quoted {
                if escape { escape = false }
                else if byte == 92 { escape = true }
                else if byte == 34 { quoted = false }
            } else {
                switch byte {
                case 34: quoted = true
                case 123: stack.append(125)
                case 91: stack.append(93)
                case 125, 93:
                    guard stack.popLast() == byte else { throw Failure.invalidResponse }
                    if stack.isEmpty {
                        result.append(try decoder.decode(Reply.self, from: data[begin...index]))
                        start = nil
                    }
                default: break
                }
                guard stack.count <= 32 else { throw Failure.invalidResponse }
            }
        }
        return (result, start != nil)
    }
}
