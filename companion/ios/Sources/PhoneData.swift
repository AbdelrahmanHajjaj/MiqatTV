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
    private struct SacredText: Decodable { let prayerVerse: [String] }
    private struct PhoneContract: Decodable {
        struct Control: Decodable { let id: String; let `default`: String }
        let defaultCity: String
        let controls: [Control]
    }

    static let cities: [City] = load("cities")
    static let methods: [CalculationMethod] = load("methods")
    static let phoneStrings: [String: Localized<String>] = load("phone-strings")
    static let sharedStrings = try! JSONSerialization.jsonObject(with: resource("strings", "json")) as! [String: Any]
    static let verse = load("sacred-text", as: SacredText.self).prayerVerse.joined(separator: " ")
    static let feedback: [String: URL] = load("feedback")
    private static let contract: PhoneContract = load("phone-ui-contract")
    static let defaultCity = cities.first(where: { $0.id == contract.defaultCity })!
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
        return next
    }
}

final class PhoneStore: ObservableObject {
    private static let key = "prayer.preferences"
    @Published var preferences: PhonePreferences {
        didSet {
            if let data = try? JSONEncoder().encode(preferences) {
                UserDefaults.standard.set(data, forKey: Self.key)
            }
        }
    }

    init() {
        let loaded = UserDefaults.standard.data(forKey: Self.key).flatMap {
            try? JSONDecoder().decode(PhonePreferences.self, from: $0)
        }
        preferences = loaded.flatMap { $0.valid ? $0 : nil } ?? PhonePreferences()
    }
}
