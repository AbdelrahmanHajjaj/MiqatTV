import Foundation

enum AndroidInstallationCheck {
    static func run(_ file: URL) async throws {
        let tv = TV(before: 41, after: 42)
        let service = installer(tv)
        try await service.install(file, version: "42") { _ in }
        try await service.launch()
        let calls = await tv.calls
        precondition(calls == ["list", "version", "upload", "list", "version", "launch"])

        let fresh = TV(before: nil, after: 42)
        try await installer(fresh).install(file, version: "42") { _ in }
        let setup = await fresh.calls
        precondition(setup == ["list", "upload", "list", "version"])

        try await rejects(file, tv: TV(before: 43, after: 42), expected: .downgrade, calls: ["list", "version"])
        try await rejects(file, tv: TV(before: nil, after: 42), expected: .changedPackage, calls: [], version: "41")
        try await rejects(file, tv: TV(before: nil, after: 42, result: "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"),
                          expected: .rejected, calls: ["list", "upload"])
        try await rejects(file, tv: TV(before: 41, after: 41), expected: .unconfirmed,
                          calls: ["list", "version", "upload", "list", "version"])
        try await rejects(file, tv: TV(before: nil, after: 42, listStatus: 1),
                          expected: .unreadableStatus, calls: ["list"])
        print("Android fixture verified install order, downgrade protection, signature rejection and post-install version.")
    }

    private static func rejects(_ file: URL, tv: TV, expected: AndroidTVInstallation.Failure,
                                calls: [String], version: String = "42") async throws {
        do {
            try await installer(tv).install(file, version: version) { _ in }
            preconditionFailure("Expected \(expected).")
        } catch let error as AndroidTVInstallation.Failure {
            precondition(error == expected)
            let performed = await tv.calls
            precondition(performed == calls)
        }
    }

    private static func installer(_ tv: TV) -> AndroidTVInstallation {
        AndroidTVInstallation { command, input, progress in
            try await tv.exchange(command, input: input, progress: progress)
        }
    }

    private actor TV {
        let before: Int?
        let after: Int
        let result: String
        let listStatus: Int
        var calls: [String] = []

        init(before: Int?, after: Int, result: String = "Success", listStatus: Int = 0) {
            self.before = before
            self.after = after
            self.result = result
            self.listStatus = listStatus
        }

        func exchange(_ destination: String, input: URL?, progress: @Sendable (Double) async -> Void) async throws -> Data {
            let version = calls.contains("upload") ? after : before
            if destination.hasPrefix("shell:cmd package list packages --user 0 tv.miqat.home;") {
                calls.append("list")
                let packages = version == nil ? "package:tv.miqat.home.demo\r\n" : "package:tv.miqat.home\r\n"
                return Data((packages + "MIQAT_RC=\(listStatus)\r\n").utf8)
            }
            if destination.hasPrefix("shell:dumpsys package tv.miqat.home |") {
                calls.append("version")
                return Data("  versionCode=\(version!) minSdk=26 targetSdk=35\r\nMIQAT_RC=0\r\n".utf8)
            }
            if let input, destination.hasPrefix("exec:cmd package install -r --user 0 -S ") {
                let count = try Data(contentsOf: input).count
                precondition(destination.hasSuffix(" -S \(count)"))
                calls.append("upload")
                await progress(1)
                return Data((result + "\r\n").utf8)
            }
            if destination.hasPrefix("shell:am start -W -n tv.miqat.home/.MainActivity;") {
                calls.append("launch")
                return Data("Status: ok\r\nMIQAT_RC=0\r\n".utf8)
            }
            preconditionFailure("Unexpected Android command.")
        }
    }
}
