import CryptoKit
import Foundation

enum LGInstallationCheck {
    static func run(_ package: URL) async throws {
        let bytes = try Data(contentsOf: package)
        let digest = Insecure.MD5.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
        let valid = TV(digest: digest, before: "0.2.28.0", after: "0.2.28")
        try await LGInstallation(transport: valid).install(package, version: "0.2.28") { _ in }
        try await LGInstallation(transport: valid).launch()
        let journey = await valid.commands
        precondition(journey == ["list", "prepare", "upload", "digest", "install", "list", "clean", "launch"])

        let newer = TV(digest: digest, before: "0.2.100", after: "0.2.28")
        try await rejects(package, on: newer, with: .downgrade, after: ["list"])

        let changed = TV(digest: digest, after: "0.2.28")
        try await rejects(package, on: changed, with: .changedPackage, after: [], selection: "0.2.27")

        let corrupt = TV(digest: "mismatch", after: "0.2.28")
        try await rejects(package, on: corrupt, with: .transferMismatch,
                          after: ["list", "prepare", "upload", "digest"])

        let refused = TV(digest: digest, after: "0.2.28",
                         terminal: #"{"returnValue":true,"details":{"state":"FAILED"}} "#)
        try await rejects(package, on: refused, with: .refused,
                          after: ["list", "prepare", "upload", "digest", "install"])

        let stalled = TV(digest: digest, after: "0.2.28", terminal: "")
        try await rejects(package, on: stalled, with: .unconfirmed,
                          after: ["list", "prepare", "upload", "digest", "install"])

        let stale = TV(digest: digest, after: "0.2.27")
        try await rejects(package, on: stale, with: .unconfirmed,
                          after: ["list", "prepare", "upload", "digest", "install", "list"])

        let partial = try LGInstallation.installCompleted(Data(#"{"returnValue":true,"details":{"state":"INSTALLED"}"#.utf8))
        precondition(!partial)
        print("LG installation fixture verified command order, downgrade protection, transfer integrity and post-install version.")
    }

    private static func rejects(_ url: URL, on tv: TV, with expected: LGInstallation.Failure,
                                after sequence: [String], selection: String = "0.2.28") async throws {
        do {
            try await LGInstallation(transport: tv).install(url, version: selection) { _ in }
            preconditionFailure("Expected \(expected).")
        } catch let actual as LGInstallation.Failure {
            precondition(actual == expected)
            let performed = await tv.commands
            precondition(performed == sequence)
        }
    }

    private actor TV: LGInstallation.Transport {
        let digest: String
        let before: String?
        let after: String
        let terminal: String
        var commands: [String] = []
        var uploadedPath: String?

        init(digest: String, before: String? = nil, after: String,
             terminal: String = #"{"returnValue":true,"details":{"state":"INSTALLED"}}"#) {
            self.digest = digest
            self.before = before
            self.after = after
            self.terminal = terminal
        }

        func run(_ command: LGInstallation.Command, progress: @Sendable (Double) async -> Void) async throws -> Data {
            let text = command.text
            if text.contains("/dev/listApps ") {
                let version = commands.contains("install") ? after : before
                commands.append("list")
                let app = version.map { #"{"id":"tv.miqat.webos","version":"\#($0)"}"# } ?? ""
                return Data(#"{"returnValue":true,"apps":[\#(app)]}"#.utf8)
            }
            if text == "mkdir -p /media/developer/temp" {
                commands.append("prepare")
                return Data()
            }
            if text.hasPrefix("cat > /media/developer/temp/miqat-"), let input = command.input {
                let path = String(text.dropFirst("cat > ".count))
                precondition(path.hasSuffix(".ipk") && FileManager.default.fileExists(atPath: input.path))
                uploadedPath = path
                commands.append("upload")
                await progress(0.5)
                return Data()
            }
            if let path = uploadedPath, text == "md5sum \(path)" {
                commands.append("digest")
                return Data("\(digest)  \(path)\n".utf8)
            }
            if let path = uploadedPath, text.contains("/dev/install "), text.contains("\"ipkUrl\":\"\(path)\""),
               text.contains("\"id\":\"tv.miqat.webos\""), command.streamsInstallStatus {
                commands.append("install")
                let started = #"{"returnValue":true,"details":{"state":"{} still working \"TV\""},"extra":[{"nested":true}]}"#
                return Data((started + "\n" + terminal).utf8)
            }
            if let path = uploadedPath, text == "rm -f \(path)" {
                commands.append("clean")
                return Data()
            }
            if text.contains("applicationManager/launch "), text.contains("\"id\":\"tv.miqat.webos\"") {
                commands.append("launch")
                return Data(#"{"returnValue":true}"#.utf8)
            }
            preconditionFailure("Unexpected command.")
        }
    }
}
