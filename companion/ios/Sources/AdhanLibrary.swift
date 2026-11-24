import AVFoundation
import Combine
import CryptoKit
import Foundation
import MiqatKit

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
    static var defaultVoice: String { PhoneData.defaults["reminders.voice"] ?? "almalki" }
    var selection = AdhanSelection(voice: Self.defaultVoice)
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

    enum ImportError: Error { case unsupported, tooLarge, invalidName, unreadableIndex }
    nonisolated private static let maximumBytes = 20 * 1024 * 1024
    private static let storage = URL.applicationSupportDirectory.appending(path: "Adhan", directoryHint: .isDirectory)
    private let index = AdhanLibrary.storage.appending(path: "library.json")
    private var resetVersion = UUID()
    let removedRecording = PassthroughSubject<String, Never>()
    @Published private(set) var records: [Recording] = []

    init() {
        records = (try? readIndex()) ?? []
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

    static func suggestedName(for source: URL, fallback: String) -> String {
        let draft = source.deletingPathExtension().lastPathComponent.unicodeScalars.map {
            $0.value < 32 || (127...159).contains($0.value) ? " " : String($0)
        }.joined().trimmingCharacters(in: .whitespacesAndNewlines)
        var name = ""
        var size = 0
        for character in draft {
            let length = String(character).utf16.count
            guard size + length <= 80 else { break }
            name.append(character)
            size += length
        }
        return RecordingName.shared.validated(value: name) ?? fallback
    }

    func importFile(_ source: URL, name: String) async throws -> String {
        guard let name = RecordingName.shared.validated(value: name) else { throw ImportError.invalidName }
        let version = resetVersion
        let (data, ext) = try await Self.prepareAudio(source)
        try Task.checkCancellation()
        guard version == resetVersion else { throw CancellationError() }
        let current = try readIndex()
        let id = "custom-" + SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        if let existing = current.firstIndex(where: { $0.id == id }) {
            var next = current
            next[existing].name = name
            try save(next)
            return id
        }
        let file = id + "." + ext
        try prepareDirectory()
        let output = Self.storage.appending(path: file)
        try data.write(to: output, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        do {
            try save(current + [Recording(id: id, filename: file, name: name)])
        } catch {
            try? FileManager.default.removeItem(at: output)
            throw error
        }
        return id
    }

    nonisolated private static func prepareAudio(_ source: URL) async throws -> (Data, String) {
        let accessed = source.startAccessingSecurityScopedResource()
        defer { if accessed { source.stopAccessingSecurityScopedResource() } }
        let values = try source.resourceValues(forKeys: [.isRegularFileKey])
        let ext = source.pathExtension.lowercased()
        guard values.isRegularFile == true, !ext.isEmpty, ext.count <= 10,
              ext.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) }) else {
            throw ImportError.unsupported
        }
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        var data = Data()
        while let chunk = try input.read(upToCount: 65536), !chunk.isEmpty {
            try Task.checkCancellation()
            guard data.count + chunk.count <= maximumBytes else { throw ImportError.tooLarge }
            data.append(chunk)
        }
        guard !data.isEmpty else { throw ImportError.unsupported }
        let temp = URL.temporaryDirectory.appending(path: UUID().uuidString).appendingPathExtension(ext)
        try data.write(to: temp, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        defer { try? FileManager.default.removeItem(at: temp) }
        let asset = AVURLAsset(url: temp)
        let duration = try await asset.load(.duration).seconds
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        let video = try await asset.loadTracks(withMediaType: .video)
        guard duration.isFinite, duration >= 0.001, duration <= 600, !tracks.isEmpty,
              video.isEmpty,
              (try? AVAudioPlayer(contentsOf: temp)) != nil else { throw ImportError.unsupported }
        return (data, ext)
    }

    func rename(_ id: String, name: String) throws {
        guard let value = RecordingName.shared.validated(value: name) else { throw ImportError.invalidName }
        var next = try readIndex()
        guard let record = next.firstIndex(where: { $0.id == id }) else { return }
        next[record].name = value
        try save(next)
    }

    func remove(_ id: String) throws {
        let current = try readIndex()
        guard let record = current.first(where: { $0.id == id }) else { return }
        let original = Self.storage.appending(path: record.filename)
        try save(current.filter { $0.id != id })
        do {
            try FileManager.default.removeItem(at: original)
        } catch {
            try save(current)
            throw error
        }
        removedRecording.send(id)
    }

    func reset() throws {
        if FileManager.default.fileExists(atPath: Self.storage.path) { try FileManager.default.removeItem(at: Self.storage) }
        resetVersion = UUID()
        records = []
    }

    private func readIndex() throws -> [Recording] {
        let data: Data
        do { data = try Data(contentsOf: index) }
        catch let error as CocoaError where [.fileReadNoSuchFile, .fileNoSuchFile].contains(error.code) { return [] }
        guard data.count <= 1_048_576, let saved = try? JSONDecoder().decode([Recording].self, from: data),
              Set(saved.map(\.id)).count == saved.count,
              saved.allSatisfy({
                  $0.id.range(of: #"\Acustom-[0-9a-f]{64}\z"#, options: .regularExpression) != nil
                      && $0.filename == URL(fileURLWithPath: $0.filename).lastPathComponent
                      && $0.filename.hasPrefix($0.id + ".")
              }) else { throw ImportError.unreadableIndex }
        return saved.filter { FileManager.default.fileExists(atPath: Self.storage.appending(path: $0.filename).path) }
    }

    private func prepareDirectory() throws {
        var directory = Self.storage
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
    }

    private func save(_ next: [Recording]) throws {
        let data = try JSONEncoder().encode(next)
        guard data.count <= 1_048_576 else { throw ImportError.unreadableIndex }
        try data.write(to: index, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        records = next
    }
}
