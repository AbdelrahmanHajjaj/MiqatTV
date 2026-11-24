import Foundation

enum AndroidRecoveryCheck {
    static func run() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let journal = directory.appending(path: "journal")
        let tv = TV(journal: journal)
        let recovery = AndroidTVRecovery(directory: journal) { try await tv.execute($0) }

        try await recovery.configure()
        let configured = await tv.snapshot
        precondition(configured == ["tv.miqat.home/.MainActivity", "tv.miqat.home/.RemoteKeys", "1"])
        try await recovery.restore()
        let restored = await tv.snapshot
        precondition(restored == ["com.vendor.home/.Main", "null", "0"])
        do {
            try await recovery.restore()
            preconditionFailure("Restored changes must be retired.")
        } catch AndroidTVRecovery.Failure.noRecord {}

        try await recovery.configure()
        await tv.changeHomeAndServices()
        try await recovery.restore()
        let independent = await tv.snapshot
        precondition(independent == ["com.other.launcher/.Home", "com.other.reader/.Read", "1"])

        try await recovery.configure()
        await tv.rejectNextRestore()
        do {
            try await recovery.restore()
            preconditionFailure("Cannot restore settings while the original Home fails.")
        } catch AndroidTVRecovery.Failure.unconfirmed {}
        let interrupted = await tv.snapshot
        precondition(interrupted[0] == "tv.miqat.home/.MainActivity" && interrupted[1].contains("RemoteKeys"))
        try await recovery.restore()
        let retried = await tv.snapshot
        precondition(retried == independent)

        let emptyJournal = directory.appending(path: "empty")
        let cvte = TV(journal: emptyJournal, cvte: true)
        do {
            try await AndroidTVRecovery(directory: emptyJournal) { try await cvte.execute($0) }.configure()
            preconditionFailure("CVTE should be referred to the desktop setup.")
        } catch AndroidTVRecovery.Failure.unsupportedTV {}
        precondition(!FileManager.default.fileExists(atPath: emptyJournal.path))

        let unavailable = directory.appending(path: "file")
        try Data().write(to: unavailable)
        let failedStorage = TV(journal: unavailable)
        do {
            try await AndroidTVRecovery(directory: unavailable) { try await failedStorage.execute($0) }.configure()
            preconditionFailure("Storage failure must stop the mutation.")
        } catch {}
        let unchanged = await failedStorage.snapshot
        precondition(unchanged == ["com.vendor.home/.Main", "null", "0"])

        print("Android recovery fixture verified journaling before configuration, other user choices, retry and failure gates.")
    }

    private actor TV {
        let journal: URL
        let cvte: Bool
        var home = "com.vendor.home/.Main"
        var services = "null"
        var enabled = "0"
        var startFails = false
        var snapshot: [String] { [home, services, enabled] }

        init(journal: URL, cvte: Bool = false) {
            self.journal = journal
            self.cvte = cvte
        }

        func changeHomeAndServices() {
            home = "com.other.launcher/.Home"
            services += ":com.other.reader/.Read"
        }

        func rejectNextRestore() { startFails = true }

        func execute(_ command: String) throws -> String {
            if command == "pm list packages -e --user 0" {
                return cvte ? "package:com.cvte.home" : "package:com.vendor.home"
            }
            if command == "getprop ro.serialno" { return "synthetic-miqat-fixture" }
            if command.hasPrefix("cmd package resolve-activity ") { return "priority=0\n" + home }
            if command == "settings get secure enabled_accessibility_services" { return services }
            if command == "settings get secure accessibility_enabled" { return enabled }
            let snapshots = try FileManager.default.contentsOfDirectory(at: journal, includingPropertiesForKeys: nil)
            precondition(snapshots.contains { $0.pathExtension == "json" })
            if command.hasPrefix("am start -W ") {
                if startFails {
                    startFails = false
                    return "Status: timeout"
                }
                return "Status: ok"
            }
            if command.hasPrefix("cmd package set-home-activity "), let value = command.split(separator: "'").last {
                home = String(value)
                return "Success"
            }
            if command.hasSuffix(" && settings delete secure enabled_accessibility_services") {
                services = "null"
                return ""
            }
            if command.contains(" && settings put secure enabled_accessibility_services "),
               let value = command.split(separator: "'").last {
                services = String(value)
                return ""
            }
            if command.contains(" && settings put secure accessibility_enabled "),
               let value = command.split(separator: "'").last {
                enabled = String(value)
                return ""
            }
            preconditionFailure("Unexpected recovery command.")
        }
    }
}
