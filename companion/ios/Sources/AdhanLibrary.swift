import AVFoundation
import Combine
import CryptoKit
import Foundation

struct AdhanVoice: Identifiable, Decodable {
    let id: String
    let en: String
    let ar: String
}

struct AdhanSelection: Codable, Equatable {
    var voice: String
    var level = 70
    var length = 100

    var valid: Bool { (0...100).contains(level) && (5...100).contains(length) && length % 5 == 0 }
}

struct AdhanPreferences: Codable, Equatable {
    var selection = AdhanSelection(voice: PhoneData.defaults["reminders.voice"] ?? "almalki")
    var fajrVoice = PhoneData.defaultFajrVoice
    var showScreen = false

    func forPrayer(_ index: Int, settings: PrayerAlert) -> AdhanSelection {
        let defaultVoice = index == 0 ? fajrVoice : selection.voice
        var result = settings.useDefaultAudio ? selection : settings.audio
        result.voice = settings.audio.voice == "inherit" ? defaultVoice : settings.audio.voice
        return result
    }
}

@MainActor
final class AdhanLibrary: ObservableObject {
    struct Recording: Codable, Identifiable {
        let id: String
        let filename: String
        var name: String
    }

    enum ImportError: Error { case unsupported, tooLarge }
    private static let maximumBytes = 20 * 1024 * 1024
    private static let storage = URL.applicationSupportDirectory.appending(path: "Adhan", directoryHint: .isDirectory)
    private let index = AdhanLibrary.storage.appending(path: "library.json")
    @Published private(set) var records: [Recording] = []

    init() {
        if let data = try? Data(contentsOf: index),
           let saved = try? JSONDecoder().decode([Recording].self, from: data) {
            records = saved.filter {
                $0.filename == URL(fileURLWithPath: $0.filename).lastPathComponent
                    && FileManager.default.fileExists(atPath: Self.storage.appending(path: $0.filename).path)
            }
        }
    }

    func url(_ voice: String) -> URL? {
        if let recording = records.first(where: { $0.id == voice }) {
            return Self.storage.appending(path: recording.filename)
        }
        guard PhoneData.voices.contains(where: { $0.id == voice }) else { return nil }
        return Bundle.main.url(forResource: voice, withExtension: "mp3", subdirectory: "Adhan")
    }

    func name(_ voice: String, preferences: PhonePreferences) -> String {
        if let recording = records.first(where: { $0.id == voice }) { return recording.name }
        if let builtIn = PhoneData.voices.first(where: { $0.id == voice }) { return preferences.arabic ? builtIn.ar : builtIn.en }
        return preferences.text("phone_inherit")
    }

    func importFile(_ source: URL) async throws -> String {
        let accessed = source.startAccessingSecurityScopedResource()
        defer { if accessed { source.stopAccessingSecurityScopedResource() } }
        let values = try source.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
        let ext = source.pathExtension.lowercased()
        guard values.isRegularFile == true, let size = values.fileSize, size > 0,
              size <= Self.maximumBytes else { throw ImportError.tooLarge }
        guard !ext.isEmpty, ext.count <= 10, ext.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) }) else {
            throw ImportError.unsupported
        }
        let temp = URL.temporaryDirectory.appending(path: UUID().uuidString).appendingPathExtension(ext)
        try FileManager.default.copyItem(at: source, to: temp)
        defer { try? FileManager.default.removeItem(at: temp) }
        let asset = AVURLAsset(url: temp)
        let duration = try await asset.load(.duration).seconds
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        guard duration.isFinite, duration >= 0.001, duration <= 600, !tracks.isEmpty,
              (try? AVAudioPlayer(contentsOf: temp)) != nil else { throw ImportError.unsupported }
        let data = try Data(contentsOf: temp, options: .mappedIfSafe)
        guard data.count <= Self.maximumBytes else { throw ImportError.tooLarge }
        try Task.checkCancellation()
        let id = "custom-" + SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        if records.contains(where: { $0.id == id }) { return id }
        let file = id + "." + ext
        try prepareDirectory()
        let output = Self.storage.appending(path: file)
        try data.write(to: output, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        do {
            let name = String(source.deletingPathExtension().lastPathComponent.prefix(96))
            try save(records + [Recording(id: id, filename: file, name: name)])
        } catch {
            try? FileManager.default.removeItem(at: output)
            throw error
        }
        return id
    }

    func rename(_ id: String, name: String) throws {
        let value = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(96))
        guard !value.isEmpty, let record = records.firstIndex(where: { $0.id == id }) else { return }
        var next = records
        next[record].name = value
        try save(next)
    }

    func remove(_ id: String) throws {
        guard let record = records.first(where: { $0.id == id }) else { return }
        let original = Self.storage.appending(path: record.filename)
        let staged = Self.storage.appending(path: UUID().uuidString)
        try FileManager.default.moveItem(at: original, to: staged)
        do {
            try save(records.filter { $0.id != id })
        } catch {
            try FileManager.default.moveItem(at: staged, to: original)
            throw error
        }
        try FileManager.default.removeItem(at: staged)
    }

    func reset() throws {
        if FileManager.default.fileExists(atPath: Self.storage.path) { try FileManager.default.removeItem(at: Self.storage) }
        records = []
    }

    private func prepareDirectory() throws {
        var directory = Self.storage
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
    }

    private func save(_ next: [Recording]) throws {
        try JSONEncoder().encode(next).write(to: index, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        records = next
    }
}
