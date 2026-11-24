import Foundation

struct AndroidTVInstallation {
    enum Failure: Error, Equatable {
        case unreadableStatus, changedPackage, downgrade, rejected, unconfirmed
    }

    enum Stage: Sendable {
        case sending(Double), verifying
    }

    typealias Exchange = @Sendable (String, URL?, @escaping @Sendable (Double) async -> Void) async throws -> Data

    private static let id = AndroidTVPackage.packageID
    private let exchange: Exchange

    init(exchange: @escaping Exchange) {
        self.exchange = exchange
    }

    func installedVersion() async throws -> UInt64? {
        let packages = try await shell("cmd package list packages --user 0 \(Self.id)")
        let lines = packages.split(whereSeparator: \.isNewline)
        guard lines.allSatisfy({ $0.hasPrefix("package:") }) else { throw Failure.unreadableStatus }
        let count = lines.filter { $0 == "package:" + Self.id }.count
        guard count <= 1 else { throw Failure.unreadableStatus }
        guard count == 1 else { return nil }

        let value = try await shell("dumpsys package \(Self.id) | grep -m1 'versionCode='")
        guard let field = value.split(whereSeparator: \.isWhitespace).first,
              field.hasPrefix("versionCode="),
              let code = UInt64(field.dropFirst("versionCode=".count)), code > 0 else {
            throw Failure.unreadableStatus
        }
        return code
    }

    func install(_ file: URL, version: String, report: @escaping @Sendable (Stage) async -> Void) async throws {
        guard let code = UInt64(version), try AndroidTVPackage.version(of: file) == version else {
            throw Failure.changedPackage
        }
        if let installed = try await installedVersion(), code < installed { throw Failure.downgrade }

        let bytes = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard (1...AndroidTVPackage.maximumSize).contains(bytes) else { throw Failure.changedPackage }
        await report(.sending(0))
        try Task.checkCancellation()
        let result = try await exchange("exec:cmd package install -r --user 0 -S \(bytes)", file) {
            await report(.sending($0))
        }
        guard Self.text(result) == "Success" else { throw Failure.rejected }
        await report(.verifying)
        guard try await installedVersion() == code else { throw Failure.unconfirmed }
    }

    func launch() async throws {
        let response = try await shell("am start -W -n \(Self.id)/.MainActivity")
        guard response.split(whereSeparator: \.isNewline).contains("Status: ok") else { throw Failure.unconfirmed }
    }

    private func shell(_ command: String) async throws -> String {
        try Task.checkCancellation()
        let stream = "shell:\(command); printf '\\nMIQAT_RC=%s\\n' \"$?\""
        let response = Self.text(try await exchange(stream, nil) { _ in })
        let trailer = "\nMIQAT_RC=0"
        if response == "MIQAT_RC=0" { return "" }
        guard response.hasSuffix(trailer) else { throw Failure.unreadableStatus }
        return String(response.dropLast(trailer.count)).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func text(_ bytes: Data) -> String {
        String(decoding: bytes, as: UTF8.self).replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
