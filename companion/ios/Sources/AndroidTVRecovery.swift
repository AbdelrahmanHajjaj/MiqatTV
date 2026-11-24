import CryptoKit
import Foundation

struct AndroidTVRecovery {
    enum Failure: Error {
        case noRecord, unsupportedTV, invalidHome, changedHome, invalidRecord, unconfirmed, cleanupNotReady
    }

    private struct Journal: Codable {
        var version: Int
        let device: String
        let home: String
        var homeChanged = false
        var remoteAdded = false
        var enabledBySetup = false
        var services: String?
        var enabled: String?
        var disabledBySetup: [String]?
        var hasChanges: Bool { homeChanged || remoteAdded || enabledBySetup || !(disabledBySetup ?? []).isEmpty }
    }

    private static let miqat = "tv.miqat.home/.MainActivity"
    private static let remote = "tv.miqat.home/.RemoteKeys"
    private static let servicesName = "enabled_accessibility_services"
    private static let enabledName = "accessibility_enabled"
    private let shell: @Sendable (String) async throws -> String
    private let directory: URL

    init(directory: URL = URL.applicationSupportDirectory.appending(path: "AndroidTVRecovery", directoryHint: .isDirectory),
         shell: @escaping @Sendable (String) async throws -> String) {
        self.directory = directory
        self.shell = shell
    }

    func configure() async throws {
        let packages = try await shell("pm list packages -e --user 0")
        let lines = packages.split(whereSeparator: \.isNewline)
        guard !lines.isEmpty, lines.allSatisfy({ $0.hasPrefix("package:") }),
              !lines.contains(where: { $0.hasPrefix("package:com.cvte.") }) else { throw Failure.unsupportedTV }

        let device = try await deviceID()
        let file = journalURL(device)
        let home = try await currentHome()
        let services = try await setting(Self.servicesName)
        let enabled = try await setting(Self.enabledName)
        let existing = try load(file, device: device)
        var journal = existing.flatMap { $0.hasChanges ? $0 : nil } ?? Journal(version: 1, device: device, home: home)
        guard Self.sameHome(home, journal.home) || Self.sameHome(home, Self.miqat) else { throw Failure.changedHome }
        let addRemote = !Self.services(in: services).contains(where: Self.isMiqatRemote)
        if addRemote && !journal.remoteAdded {
            journal.remoteAdded = true
            journal.services = services
        }
        if enabled != "1" && !journal.enabledBySetup {
            journal.enabledBySetup = true
            journal.enabled = enabled
        }
        let changeHome = !Self.sameHome(home, Self.miqat)
        if changeHome { journal.homeChanged = true }
        try save(journal, to: file)

        try await startHome(Self.miqat)
        if changeHome {
            let current = try await currentHome()
            guard Self.sameHome(current, home) else { throw Failure.changedHome }
            try await selectHome(Self.miqat)
        }
        if addRemote {
            let desired = (Self.services(in: services) + [Self.remote]).joined(separator: ":")
            try await write(Self.servicesName, from: services, to: desired)
        }
        if enabled != "1" { try await write(Self.enabledName, from: enabled, to: "1") }
    }

    func restore() async throws {
        let device = try await deviceID()
        let file = journalURL(device)
        guard var journal = try load(file, device: device), journal.hasChanges else { throw Failure.noRecord }
        if let disabled = journal.disabledBySetup, !disabled.isEmpty {
            let stillDisabled = try await packages("-d")
            for package in disabled where stillDisabled.contains(package) {
                _ = try await shell("pm enable --user 0 \(package)")
                guard try await packages("-e").contains(package) else { throw Failure.unconfirmed }
            }
        }
        if journal.homeChanged {
            let live = try await currentHome()
            if Self.sameHome(live, Self.miqat) {
                try await startHome(journal.home)
                let current = try await currentHome()
                guard Self.sameHome(current, live) else { throw Failure.changedHome }
                try await selectHome(journal.home)
            }
        }

        let live = try await setting(Self.servicesName)
        let services = Self.services(in: live)
        let kept = services.filter { !Self.isMiqatRemote($0) }
        if journal.remoteAdded && kept != services {
            let desired = kept == Self.services(in: journal.services) ? journal.services : kept.joined(separator: ":")
            try await write(Self.servicesName, from: live, to: desired)
        }
        if journal.enabledBySetup {
            let enabled = try await setting(Self.enabledName)
            let activeServices = try await setting(Self.servicesName)
            if enabled == "1" && Self.services(in: activeServices).isEmpty {
                try await write(Self.enabledName, from: "1", to: journal.enabled)
            }
        }
        journal.homeChanged = false
        journal.remoteAdded = false
        journal.enabledBySetup = false
        journal.disabledBySetup = nil
        try save(journal, to: file)
    }

    func cleanupReview(catalog: [AndroidTVCleanup.Candidate]) async throws -> [AndroidTVCleanup.Candidate] {
        let home = try await currentHome()
        let installed = try await packages("")
        guard Self.sameHome(home, Self.miqat), !installed.contains(where: { $0.hasPrefix("com.cvte.") }) else {
            throw Failure.cleanupNotReady
        }
        let enabled = try await packages("-e")
        guard enabled.contains(AndroidTVPackage.packageID) else { throw Failure.cleanupNotReady }

        struct App: Decodable { let p: String; let fav: Int }
        struct Key: Decodable { let p: String }
        let apps = try await provider([App].self, path: "apps")
        let keys = try await provider([Key].self, path: "keys")
        let favorites = Set(apps.filter { $0.fav >= 0 }.map(\.p))
        let redirects = Set(keys.map(\.p))
        let screensaver = try await setting("screensaver_components") ?? ""
        return catalog.filter {
            enabled.contains($0.id) && !favorites.contains($0.id) && !redirects.contains($0.id)
                && !screensaver.contains($0.id + "/")
        }
    }

    func clean(_ selection: Set<String>, catalog: [AndroidTVCleanup.Candidate]) async throws -> Int {
        guard !selection.isEmpty, selection.count <= 128 else { throw Failure.cleanupNotReady }
        let review = try await cleanupReview(catalog: catalog)
        let available = Set(review.map(\.id))
        guard selection.isSubset(of: available), selection.allSatisfy(AndroidTVCleanup.safePackage) else {
            throw Failure.cleanupNotReady
        }
        let device = try await deviceID()
        let file = journalURL(device)
        var journal = try load(file, device: device) ?? Journal(version: 1, device: device, home: Self.miqat)
        try await startHome(Self.miqat)
        let active = try await currentHome()
        let visible = try await shell("dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp|topResumedActivity'")
        guard Self.sameHome(active, Self.miqat), visible.contains(AndroidTVPackage.packageID + "/") else {
            throw Failure.cleanupNotReady
        }

        var count = 0
        for name in selection.sorted() {
            let current = try await currentHome()
            guard Self.sameHome(current, Self.miqat), try await packages("-e").contains(name) else {
                throw Failure.cleanupNotReady
            }
            let currentScreensaver = try await setting("screensaver_components") ?? ""
            if currentScreensaver.contains(name + "/") { continue }
            if name == "com.google.android.tungsten.setupwraith" {
                let wifi = try await shell("dumpsys wifi | grep -F 'cname='")
                if wifi.isEmpty || wifi.contains("cname=" + name) { continue }
            }
            let details = try await shell("dumpsys package \(name)")
            guard !details.isEmpty, !details.contains("android.permission.TV_INPUT_HARDWARE") else { continue }
            let disabled = Set(journal.disabledBySetup ?? []).union([name])
            guard disabled.count <= 128 else { throw Failure.cleanupNotReady }
            journal.disabledBySetup = disabled.sorted()
            try save(journal, to: file)
            let result = try await shell("pm disable-user --user 0 \(name)")
            guard result.contains("disabled-user"), try await packages("-d").contains(name) else {
                throw Failure.unconfirmed
            }
            count += 1
        }
        return count
    }

    private func packages(_ flags: String) async throws -> Set<String> {
        let option = flags.isEmpty ? "" : flags + " "
        let response = try await shell("pm list packages \(option)--user 0")
        let rows = response.split(whereSeparator: \.isNewline)
        guard rows.allSatisfy({ $0.hasPrefix("package:") }) else { throw Failure.unconfirmed }
        return Set(rows.map { String($0.dropFirst("package:".count)) })
    }

    private func provider<Value: Decodable>(_ type: Value.Type, path: String) async throws -> Value {
        let text = try await shell("content query --uri content://tv.miqat.home.setup/\(path)")
        guard text.hasPrefix("Row: 0 json=") else { throw Failure.cleanupNotReady }
        return try JSONDecoder().decode(type, from: Data(text.dropFirst("Row: 0 json=".count).utf8))
    }

    func export() async throws -> Data {
        let device = try await deviceID()
        guard var journal = try load(journalURL(device), device: device), journal.hasChanges else {
            throw Failure.noRecord
        }
        // Version 2 identifies the iPhone's scoped Home and accessibility restore.
        journal.version = 2
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(journal)
    }

    private func deviceID() async throws -> String {
        let serial = try await shell("getprop ro.serialno")
        guard (1...128).contains(serial.utf8.count), !serial.contains(where: \.isWhitespace),
              serial.lowercased() != "unknown", !serial.allSatisfy({ $0 == "0" }) else { throw Failure.unsupportedTV }
        return SHA256.hash(data: Data(serial.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func currentHome() async throws -> String {
        let output = try await shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        guard let component = output.split(whereSeparator: \.isNewline).last,
              Self.validHome(String(component)) else { throw Failure.invalidHome }
        return String(component)
    }

    private func startHome(_ component: String) async throws {
        let result = try await shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n \(Self.quote(component))")
        guard result.split(whereSeparator: \.isNewline).contains("Status: ok"), !result.contains("Error:") else {
            throw Failure.unconfirmed
        }
    }

    private func selectHome(_ component: String) async throws {
        _ = try await shell("cmd package set-home-activity --user 0 \(Self.quote(component))")
        let current = try await currentHome()
        guard Self.sameHome(current, component) else { throw Failure.unconfirmed }
    }

    private func setting(_ name: String) async throws -> String? {
        let value = try await shell("settings get secure \(name)")
        guard value.utf8.count <= 16384, !value.contains(where: \.isNewline), !value.utf8.contains(0) else {
            throw Failure.unconfirmed
        }
        return value == "null" ? nil : value
    }

    private func write(_ name: String, from expected: String?, to value: String?) async throws {
        let command = value.map { "settings put secure \(name) \(Self.quote($0))" } ?? "settings delete secure \(name)"
        _ = try await shell("[ \"$(settings get secure \(name))\" = \(Self.quote(expected ?? "null")) ] && \(command)")
        guard try await setting(name) == value else { throw Failure.unconfirmed }
    }

    private func journalURL(_ device: String) -> URL {
        directory.appending(path: device + ".json", directoryHint: .notDirectory)
    }

    private func load(_ file: URL, device: String) throws -> Journal? {
        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard (1...65536).contains(size) else { throw Failure.invalidRecord }
        let journal = try JSONDecoder().decode(Journal.self, from: Data(contentsOf: file))
        let disabled = journal.disabledBySetup ?? []
        guard journal.version == 1, journal.device == device, Self.validHome(journal.home),
              disabled.count <= 128, disabled.allSatisfy(AndroidTVCleanup.safePackage) else { throw Failure.invalidRecord }
        return journal
    }

    private func save(_ journal: Journal, to file: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        #if os(iOS)
        var storage = directory
        var metadata = URLResourceValues()
        metadata.isExcludedFromBackup = true
        try storage.setResourceValues(metadata)
        try JSONEncoder().encode(journal).write(to: file, options: [.atomic, .completeFileProtection])
        #else
        try JSONEncoder().encode(journal).write(to: file, options: [.atomic])
        #endif
    }

    private static func validHome(_ value: String) -> Bool {
        guard value.utf8.count <= 256,
              value.range(of: #"\A[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+\z"#, options: .regularExpression) != nil else {
            return false
        }
        return !["factory", "hkcfac", "fallbackhome", "resolveractivity", "setupwraith", "provision"]
            .contains(where: { value.lowercased().contains($0) })
    }

    private static func sameHome(_ left: String, _ right: String) -> Bool {
        func normalized(_ value: String) -> String {
            guard let separator = value.firstIndex(of: "/"), value[value.index(after: separator)...].hasPrefix(".") else {
                return value
            }
            return String(value[...separator]) + String(value[..<separator]) + String(value[value.index(after: separator)...])
        }
        return normalized(left) == normalized(right)
    }

    private static func services(in settings: String?) -> [String] {
        (settings ?? "").split(separator: ":").map(String.init)
    }

    private static func isMiqatRemote(_ service: String) -> Bool {
        sameHome(service, remote)
    }

    private static func quote(_ value: String) -> String {
        "'" + value.replacingOccurrences(of: "'", with: "'\\''") + "'"
    }
}
