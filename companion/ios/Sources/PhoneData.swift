import Combine
import Foundation
import MiqatKit

struct Localized<Value: Decodable>: Decodable {
    let en: Value
    let ar: Value
    func value(_ arabic: Bool) -> Value { arabic ? ar : en }
}

struct City: Identifiable, Decodable {
    let id: String
    let en: String
    let ar: String
    let countryEn: String
    let countryAr: String
    let lat: Double
    let lng: Double
    let method: String
    let hanafi: Bool

    func name(_ arabic: Bool) -> String { arabic ? ar : en }
    func country(_ arabic: Bool) -> String { arabic ? countryAr : countryEn }
}

struct CalculationMethod: Identifiable, Codable {
    struct Preset: Codable {
        let fajr: Double
        let isha: Double
        let interval: Int
    }

    let id: String
    let en: String
    let ar: String
    let preset: Preset?
    let methodAdjustments: [Int]?
}

enum PhoneData {
    private struct SacredText: Decodable { let prayerVerse: [String]; let adhanCall: String }
    private struct PhoneContract: Decodable {
        struct Control: Decodable { let id: String; let `default`: String }
        let defaultCity: String
        let defaultFajrVoice: String
        let controls: [Control]
    }

    static let cities: [City] = load("cities")
    static let methods: [CalculationMethod] = load("methods")
    static let voices: [AdhanVoice] = load("voices")
    static let phoneStrings: [String: Localized<String>] = load("phone-strings")
    static let sharedStrings = try! JSONSerialization.jsonObject(with: resource("strings", "json")) as! [String: Any]
    private static let sacredText = load("sacred-text", as: SacredText.self)
    static let verse = sacredText.prayerVerse.joined(separator: " ")
    static let adhanCall = sacredText.adhanCall
    static let feedback: [String: URL] = load("feedback")
    private static let contract: PhoneContract = load("phone-ui-contract")
    static let defaultCity = cities.first(where: { $0.id == contract.defaultCity })!
    static let defaultFajrVoice = contract.defaultFajrVoice
    static let defaults = Dictionary(uniqueKeysWithValues: contract.controls.map { ($0.id, $0.default) })

    static func load<Value: Decodable>(_ name: String, as type: Value.Type = Value.self) -> Value {
        try! JSONDecoder().decode(type, from: resource(name, "json"))
    }

    static func resource(_ name: String, _ extensionName: String) -> Data {
        try! Data(contentsOf: Bundle.main.url(forResource: name, withExtension: extensionName)!)
    }

    static func text(_ key: String, arabic: Bool) -> String {
        phoneStrings[key]?.value(arabic)
            ?? (sharedStrings[key] as? [String: String])?[arabic ? "ar" : "en"]
            ?? key
    }

    static func list(_ key: String, arabic: Bool) -> [String] {
        (sharedStrings[key] as? [String: [String]])?[arabic ? "ar" : "en"] ?? []
    }

    static func closestCity(latitude: Double, longitude: Double) -> City {
        func distance(to city: City) -> Double {
            LocationRules.shared.distanceKm(latitude: latitude, longitude: longitude,
                                           otherLatitude: city.lat, otherLongitude: city.lng)
        }
        return cities.min { distance(to: $0) < distance(to: $1) } ?? defaultCity
    }
}

struct PhonePreferences: Codable, Equatable {
    var cityID = PhoneData.defaultCity.id
    var custom: Coordinates?
    var locationConfirmed = false
    var method = PhoneData.defaults["prayer.method"] ?? PhoneData.defaultCity.method
    var hanafi = PhoneData.defaultCity.hanafi
    var highLatitude = PhoneData.defaults["prayer.high-latitude"] ?? "middle"
    var zoneID = ""
    var offsets = [0, 0, 0, 0, 0, 0]
    var hijriOffset = 0
    var language = "system"
    var digits = PhoneData.defaults["display.digits"] ?? "auto"
    var clock = PhoneData.defaults["display.clock"] ?? "12"
    var theme = PhoneData.defaults["appearance.theme"] ?? "mihrab"
    var background = PhoneData.defaults["appearance.background"] ?? "miqat_signature"
    var displaySize = 100
    var reduceMotion = false
    var showArtwork = true
    var setupStarted = false
    var setupFinished = false
    var triedSetupLocation = false
    var alertsEnabled = false
    var prayerAlerts = Array(repeating: PrayerAlert(), count: 6)
    var quietHours = QuietHours()
    var fastingAlerts = FastingAlerts()
    var adhan = AdhanPreferences()
    var radioStation = "cairo"
    var automaticLocation = false
    var travelRefresh = false

    struct Coordinates: Codable, Equatable {
        let latitude: Double
        let longitude: Double
        var valid: Bool { (-90...90).contains(latitude) && (-180...180).contains(longitude) }
    }

    var city: City { PhoneData.cities.first { $0.id == cityID } ?? PhoneData.defaultCity }
    var coordinates: Coordinates { custom ?? Coordinates(latitude: city.lat, longitude: city.lng) }
    var zone: TimeZone { TimeZone(identifier: zoneID) ?? .autoupdatingCurrent }
    var arabic: Bool {
        language == "ar" || (language == "system" && Locale.preferredLanguages.first?.hasPrefix("ar") == true)
    }

    var valid: Bool {
        coordinates.valid && offsets.count == 6 && offsets.allSatisfy { (-30...30).contains($0) }
            && (-2...2).contains(hijriOffset) && ["middle", "seventh", "angle"].contains(highLatitude)
            && prayerAlerts.count == 6 && prayerAlerts.allSatisfy(\.valid)
            && prayerAlerts[1].before == 0 && prayerAlerts[1].after == 0
            && quietHours.valid && fastingAlerts.valid
            && adhan.selection.valid && prayerAlerts.allSatisfy { $0.audio.valid }
    }

    func text(_ key: String) -> String { PhoneData.text(key, arabic: arabic) }
    func prayerNames() -> [String] { PhoneData.list("prayerNames", arabic: arabic) }
    func displayDigits(_ value: String) -> String {
        DisplayText.shared.digits(value: value, eastern: digits == "eastern" || (digits == "auto" && arabic))
    }

    func select(city: City) -> Self {
        var next = self
        next.cityID = city.id
        next.custom = nil
        next.method = city.method
        next.hanafi = city.hanafi
        next.locationConfirmed = false
        next.automaticLocation = false
        next.travelRefresh = false
        return next
    }

    func useDeviceLocation(_ point: Coordinates, confirmed: Bool) -> Self {
        let nearest = PhoneData.closestCity(latitude: point.latitude, longitude: point.longitude)
        var next = self
        next.cityID = nearest.id
        next.custom = point
        next.zoneID = ""
        if !locationConfirmed {
            next.method = nearest.method
            next.hanafi = nearest.hanafi
        }
        next.locationConfirmed = confirmed
        return next
    }
}

@MainActor
final class PhoneStore: ObservableObject {
    static let shared = PhoneStore()
    private static let directory = URL.applicationSupportDirectory.appending(path: "Prayer", directoryHint: .isDirectory)
    private static let file = directory.appending(path: "preferences.json")
    private static let claimsFile = directory.appending(path: "adhan-claims.json")
    @Published private(set) var preferences: PhonePreferences
    @Published var saveFailed = false
    private var loaded = false

    private init() {
        preferences = PhonePreferences()
        _ = loadIfNeeded()
    }

    func loadIfNeeded() -> Bool {
        if loaded { return true }
        do {
            let data = try Data(contentsOf: Self.file)
            let saved = try? JSONDecoder().decode(PhonePreferences.self, from: data)
            loaded = true
            if let saved, saved.valid { preferences = saved }
        } catch let error as CocoaError where [.fileReadNoSuchFile, .fileNoSuchFile].contains(error.code) {
            loaded = true
        } catch {
            return false
        }
        return true
    }

    func save(_ value: PhonePreferences) {
        guard loaded else { saveFailed = true; return }
        do {
            try Self.write(value, to: Self.file)
            preferences = value
        } catch {
            saveFailed = true
        }
    }

    func claimAdhan(_ id: String) -> Bool {
        guard id.utf8.count <= 256 else { return false }
        do {
            let old: [String]
            if FileManager.default.fileExists(atPath: Self.claimsFile.path) {
                old = try JSONDecoder().decode([String].self, from: Data(contentsOf: Self.claimsFile))
            } else {
                old = []
            }
            guard !old.contains(id) else { return false }
            try Self.write(Array((old + [id]).suffix(64)), to: Self.claimsFile)
            return true
        } catch {
            return false
        }
    }

    func clearAdhanClaims() throws {
        if FileManager.default.fileExists(atPath: Self.claimsFile.path) {
            try FileManager.default.removeItem(at: Self.claimsFile)
        }
    }

    private static func write<Value: Encodable>(_ value: Value, to file: URL) throws {
        var directory = Self.directory
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var attributes = URLResourceValues()
        attributes.isExcludedFromBackup = true
        try directory.setResourceValues(attributes)
        try JSONEncoder().encode(value).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
