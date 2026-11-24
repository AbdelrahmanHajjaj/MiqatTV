import CoreLocation
import MiqatKit
import SwiftUI

final class PhoneLocation: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var pending = false
    @Published private(set) var failed = false
    @Published private(set) var fix: CLLocation?
    private let manager = CLLocationManager()
    private var timeout: Task<Void, Never>?
    private var requested = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func find() {
        guard !pending else { return }
        pending = true
        failed = false
        fix = nil
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else {
            startIfAuthorized()
        }
    }

    func cancel() {
        timeout?.cancel()
        timeout = nil
        manager.stopUpdatingLocation()
        pending = false
        requested = false
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if pending { startIfAuthorized() }
    }

    private func startIfAuthorized() {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            guard !requested else { return }
            requested = true
            manager.startUpdatingLocation()
            timeout = Task { @MainActor [weak self] in
                try? await Task.sleep(for: .seconds(20))
                guard !Task.isCancelled else { return }
                self?.fail()
            }
        case .notDetermined:
            break
        default:
            fail()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard pending, let latest = locations.last,
              LocationRules.shared.usableLocation(latitude: latest.coordinate.latitude,
                 longitude: latest.coordinate.longitude, accuracyMetres: latest.horizontalAccuracy,
                 ageMs: -latest.timestamp.timeIntervalSinceNow * 1000) else { return }
        cancel()
        fix = latest
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard pending, (error as? CLError)?.code != .locationUnknown else { return }
        fail()
    }

    private func fail() {
        cancel()
        failed = true
    }
}

struct LocationSettings: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    var setup = false
    @StateObject private var location = PhoneLocation()
    @State private var showCoordinates = false

    var body: some View {
        Form {
            Section {
                NavigationLink {
                    CityPicker(preferences: $preferences, choose: location.cancel)
                } label: {
                    LabeledContent(preferences.text("phone_city"), value: preferences.city.name(preferences.arabic))
                }
                if location.pending {
                    ProgressView(preferences.text("phone_location_working"))
                    Button(preferences.text("phone_location_cancel")) { location.cancel() }
                } else {
                    Button {
                        location.find()
                    } label: {
                        Label(preferences.text("phone_location_title"), systemImage: "location")
                    }
                }
                if location.failed { Text(preferences.text("phone_location_timeout")).foregroundStyle(.secondary) }
                Button(preferences.text("phone_custom_location")) {
                    location.cancel()
                    showCoordinates = true
                }
                Button(preferences.text("phone_confirm_location")) { preferences.locationConfirmed = true }
                    .disabled(preferences.locationConfirmed)
            }
            Section {
                Picker(preferences.text("phone_method"), selection: $preferences.method) {
                    ForEach(PhoneData.methods) { method in
                        Text(preferences.arabic ? method.ar : method.en).tag(method.id)
                    }
                }
                Picker(preferences.text("phone_asr"), selection: $preferences.hanafi) {
                    Text(preferences.arabic ? "الجمهور (شافعي)" : "Standard (Shafi‘i)").tag(false)
                    Text(preferences.arabic ? "حنفي" : "Hanafi").tag(true)
                }
                Picker(preferences.text("phone_high_latitude"), selection: $preferences.highLatitude) {
                    ForEach(["middle", "seventh", "angle"], id: \.self) { option in
                        Text(preferences.text("phone_" + option)).tag(option)
                    }
                }
                let sunrise = schedule.day(at: .now, prefs: preferences).map { preferences.time($0[1]) }
                Stepper(value: $preferences.offsets[1], in: -30...30) {
                    VStack(alignment: .leading) {
                        Text(preferences.text("phone_sunrise_offset"))
                        Text(preferences.displayDigits(String(preferences.offsets[1])) + (sunrise.map { " · " + $0 } ?? ""))
                            .foregroundStyle(.secondary)
                    }
                }
                Stepper(value: $preferences.hijriOffset, in: -2...2) {
                    Text(preferences.text("phone_hijri_offset") + " · " + preferences.displayDigits(String(preferences.hijriOffset)))
                }
            }
        }
        .navigationTitle(preferences.text("phone_location_title"))
        .sheet(isPresented: $showCoordinates) {
            NavigationStack { AdvancedLocation(preferences: $preferences) }
        }
        .onChange(of: location.fix) { _, fix in
            guard let fix else { return }
            let coordinates = fix.coordinate
            let closest = PhoneData.cities.min { a, b in
                distance(a, to: coordinates) < distance(b, to: coordinates)
            } ?? PhoneData.defaultCity
            var next = preferences.select(city: closest)
            next.custom = .init(latitude: coordinates.latitude, longitude: coordinates.longitude)
            preferences = next
        }
        .onAppear {
            if setup && !preferences.triedSetupLocation && !preferences.locationConfirmed {
                preferences.triedSetupLocation = true
                location.find()
            }
        }
        .onDisappear { location.cancel() }
    }

    private func distance(_ city: City, to location: CLLocationCoordinate2D) -> Double {
        LocationRules.shared.distanceKm(latitude: location.latitude, longitude: location.longitude,
                                       otherLatitude: city.lat, otherLongitude: city.lng)
    }
}

struct CityPicker: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var preferences: PhonePreferences
    let choose: () -> Void
    @State private var query = ""

    var body: some View {
        let countries = Dictionary(grouping: PhoneData.cities.filter(matches)) { $0.country(preferences.arabic) }
        List {
            ForEach(countries.keys.sorted(), id: \.self) { country in
                Section(country) {
                    ForEach(countries[country, default: []]) { city in
                        Button(city.name(preferences.arabic)) {
                            choose()
                            preferences = preferences.select(city: city)
                            dismiss()
                        }
                    }
                }
            }
        }
        .searchable(text: $query, prompt: preferences.text("phone_search"))
        .navigationTitle(preferences.text("phone_choose_city"))
    }

    private func matches(_ city: City) -> Bool {
        query.isEmpty || [city.en, city.ar, city.countryEn, city.countryAr].contains { $0.localizedStandardContains(query) }
    }
}

struct AdvancedLocation: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var preferences: PhonePreferences
    @State private var latitude = ""
    @State private var longitude = ""
    @State private var zone = ""

    private var coordinates: PhonePreferences.Coordinates? {
        let parser = NumberFormatter()
        parser.locale = Locale(identifier: preferences.arabic ? "ar-EG" : "en-US")
        parser.numberStyle = .decimal
        guard let lat = Double(latitude) ?? parser.number(from: latitude)?.doubleValue,
              let lng = Double(longitude) ?? parser.number(from: longitude)?.doubleValue else { return nil }
        let point = PhonePreferences.Coordinates(latitude: lat, longitude: lng)
        return point.valid ? point : nil
    }

    var body: some View {
        Form {
            TextField(preferences.text("phone_latitude"), text: $latitude).keyboardType(.numbersAndPunctuation)
            TextField(preferences.text("phone_longitude"), text: $longitude).keyboardType(.numbersAndPunctuation)
            Picker(preferences.text("phone_zone"), selection: $zone) {
                Text(preferences.text("phone_system")).tag("")
                ForEach(TimeZone.knownTimeZoneIdentifiers, id: \.self) { Text($0).tag($0) }
            }
        }
        .navigationTitle(preferences.text("phone_custom_location"))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(preferences.text("cancel")) { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(preferences.text("done")) {
                    guard let coordinates else { return }
                    var next = preferences
                    if coordinates != next.coordinates { next.custom = coordinates }
                    if next.coordinates != preferences.coordinates || zone != preferences.zoneID {
                        next.locationConfirmed = false
                    }
                    next.zoneID = zone
                    preferences = next
                    dismiss()
                }
                .disabled(coordinates == nil)
            }
        }
        .onAppear {
            latitude = String(preferences.coordinates.latitude)
            longitude = String(preferences.coordinates.longitude)
            zone = preferences.zoneID
        }
    }
}
