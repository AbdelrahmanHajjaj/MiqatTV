import Foundation

enum AndroidCleanupCheck {
    static func run() async throws {
        let path = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: path) }
        let tv = TV(path: path)
        let service = AndroidTVRecovery(directory: path) { try await tv.shell($0) }
        let rows = [
            "com.example.optional", "com.example.favorite", "com.example.remote",
            "com.example.screen", "com.example.hardware", "com.google.android.tungsten.setupwraith",
            "com.android.systemui", "tv.miqat.home", "com.example;reboot",
        ]
        let catalogue = AndroidTVCleanup.catalog(rows.map { $0 + "\t10\t0\tads\tnote" }.joined(separator: "\n"))
        let offered = try await service.cleanupReview(catalog: catalogue)
        precondition(Set(offered.map(\.id)) == ["com.example.optional", "com.example.hardware", "com.google.android.tungsten.setupwraith"])
        let selected = Set(offered.map(\.id))
        let turnedOff = try await service.clean(selected, catalog: catalogue)
        precondition(turnedOff == 1)
        let disabled = await tv.disabled
        precondition(disabled == ["com.example.optional"])
        let backup = try await service.export()
        let exported = try JSONSerialization.jsonObject(with: backup) as? [String: Any]
        precondition(exported?["disabledBySetup"] as? [String] == ["com.example.optional"])
        try await service.restore()
        let enabled = await tv.enabled
        precondition(enabled.contains("com.example.optional"))
        let after = await tv.disabled
        precondition(after.isEmpty)

        await tv.changeHome()
        do {
            _ = try await service.clean(selected, catalog: catalogue)
            preconditionFailure("Do not clean with another Home active.")
        } catch AndroidTVRecovery.Failure.cleanupNotReady {}
        let unchanged = await tv.disabled
        precondition(unchanged.isEmpty)
        print("Android cleanup fixture protected selected apps and verified journaled disable and restore.")
    }

    private actor TV {
        let path: URL
        var home = "tv.miqat.home/.MainActivity"
        var enabled: Set<String> = [
            "tv.miqat.home", "com.example.optional", "com.example.favorite", "com.example.remote",
            "com.example.screen", "com.example.hardware", "com.google.android.tungsten.setupwraith", "com.android.systemui",
        ]
        var disabled: Set<String> = []

        init(path: URL) { self.path = path }

        func changeHome() { home = "com.vendor.home/.Main" }

        func shell(_ command: String) throws -> String {
            switch command {
            case "getprop ro.serialno": return "fake-cleanup"
            case "pm list packages --user 0": return list(enabled.union(disabled))
            case "pm list packages -e --user 0": return list(enabled)
            case "pm list packages -d --user 0": return list(disabled)
            case "settings get secure screensaver_components": return "com.example.screen/.Dream"
            case "settings get secure enabled_accessibility_services": return "null"
            case "content query --uri content://tv.miqat.home.setup/apps":
                return #"Row: 0 json=[{"p":"com.example.favorite","fav":0}]"#
            case "content query --uri content://tv.miqat.home.setup/keys":
                return #"Row: 0 json=[{"p":"com.example.remote"}]"#
            case "dumpsys wifi | grep -F 'cname='": return "cname=com.google.android.tungsten.setupwraith"
            case "dumpsys package com.example.hardware": return "android.permission.TV_INPUT_HARDWARE"
            case "dumpsys package com.example.optional": return "Package [com.example.optional]"
            case "dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp|topResumedActivity'":
                return "mResumedActivity: tv.miqat.home/.MainActivity"
            case "pm disable-user --user 0 com.example.optional":
                let files = try FileManager.default.contentsOfDirectory(at: path, includingPropertiesForKeys: nil)
                precondition(files.count == 1)
                let data = try Data(contentsOf: files[0])
                let record = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                precondition(record?["disabledBySetup"] as? [String] == ["com.example.optional"])
                enabled.remove("com.example.optional")
                disabled.insert("com.example.optional")
                return "com.example.optional new state: disabled-user"
            case "pm enable --user 0 com.example.optional":
                disabled.remove("com.example.optional")
                enabled.insert("com.example.optional")
                return "com.example.optional new state: enabled"
            default:
                if command.hasPrefix("cmd package resolve-activity ") { return home }
                if command.hasPrefix("am start -W ") { return "Status: ok" }
                preconditionFailure("Unexpected cleanup command.")
            }
        }

        private func list(_ packages: Set<String>) -> String {
            packages.sorted().map { "package:" + $0 }.joined(separator: "\n")
        }
    }
}
