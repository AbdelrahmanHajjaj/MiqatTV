import CoreLocation
import MiqatKit
import SwiftUI

private struct BackgroundLocationRequestKey: EnvironmentKey {
    static let defaultValue: () -> Void = {}
}

extension EnvironmentValues {
    var requestBackgroundLocation: () -> Void {
        get { self[BackgroundLocationRequestKey.self] }
        set { self[BackgroundLocationRequestKey.self] = newValue }
    }
}

final class PhoneLocation: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var pending = false
    @Published private(set) var failed = false
    @Published private(set) var fix: CLLocation?
    @Published private(set) var heading: CLHeading?
    @Published private(set) var authorizationStatus = CLAuthorizationStatus.notDetermined
    private var manager = CLLocationManager()
    private var timeout: Task<Void, Never>?
    private var requested = false
    private var compass = false

    var canLocate: Bool { [.authorizedAlways, .authorizedWhenInUse].contains(authorizationStatus) }

    init(compass: Bool = false) {
        self.compass = compass
        super.init()
        configureManager()
    }

    func find() {
        guard !pending else { return }
        cancel()
        manager.delegate = nil
        manager = CLLocationManager()
        configureManager()
        pending = true
        failed = false
        fix = nil
        heading = nil
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
        manager.stopUpdatingHeading()
        pending = false
        requested = false
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager === self.manager else { return }
        authorizationStatus = manager.authorizationStatus
        if pending { startIfAuthorized() }
        else if compass && requested &&
                    (manager.accuracyAuthorization != .fullAccuracy ||
                     ![.authorizedAlways, .authorizedWhenInUse].contains(manager.authorizationStatus)) {
            fix = nil
            heading = nil
            fail()
        }
    }

    private func startIfAuthorized() {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            guard !requested else { return }
            if compass && manager.accuracyAuthorization != .fullAccuracy { fail(); return }
            requested = true
            manager.startUpdatingLocation()
            if compass && CLLocationManager.headingAvailable() { manager.startUpdatingHeading() }
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
        guard manager === self.manager, (pending || (compass && requested)), let latest = locations.last,
              !compass || (manager.accuracyAuthorization == .fullAccuracy && latest.horizontalAccuracy <= 1000),
              LocationRules.shared.usableLocation(latitude: latest.coordinate.latitude,
                 longitude: latest.coordinate.longitude, accuracyMetres: latest.horizontalAccuracy,
                 ageMs: -latest.timestamp.timeIntervalSinceNow * 1000) else { return }
        if compass {
            timeout?.cancel()
            pending = false
            if fix != nil { return }
        } else {
            cancel()
        }
        fix = latest
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading heading: CLHeading) {
        if manager === self.manager && compass && requested { self.heading = heading }
    }

    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool { compass }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard manager === self.manager, pending, (error as? CLError)?.code != .locationUnknown else { return }
        fail()
    }

    private func fail() {
        cancel()
        failed = true
    }

    private func configureManager() {
        manager.desiredAccuracy = compass ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
        authorizationStatus = manager.authorizationStatus
        manager.delegate = self
    }
}

struct LocationSettings: View {
    @Environment(\.requestBackgroundLocation) private var requestBackgroundLocation
    @Environment(\.openURL) private var openURL
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    var setup = false
    @StateObject private var location = PhoneLocation()
    @State private var showCoordinates = false
    @State private var allowBackground = false

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
                LabeledContent(preferences.text("phone_zone"), value: preferences.zone.identifier)
                Button(preferences.text("phone_confirm_location")) { preferences.locationConfirmed = true }
                    .disabled(preferences.locationConfirmed || location.pending)
            }
            if setup {
                Section {
                    Toggle(preferences.text("phone_alerts"), isOn: Binding(
                        get: { preferences.setupAlerts ?? true },
                        set: { preferences.setupAlerts = $0 }
                    ))
                }
            }
            Section {
                Toggle(preferences.text("phone_travel_refresh"), isOn: $preferences.travelRefresh)
                    .disabled(!preferences.locationConfirmed)
                Toggle(preferences.text("phone_automatic_location"), isOn: Binding(
                    get: { preferences.automaticLocation },
                    set: { enabled in
                        if enabled { allowBackground = true }
                        else { preferences.automaticLocation = false }
                    }
                ))
                .disabled(!preferences.locationConfirmed)
                if preferences.automaticLocation && location.authorizationStatus != .authorizedAlways {
                    Button(preferences.text("phone_location_settings")) {
                        if let settings = URL(string: UIApplication.openSettingsURLString) { openURL(settings) }
                    }
                }
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
        .navigationTitle(preferences.text(setup ? "setup_essentials" : "phone_location_title"))
        .sheet(isPresented: $showCoordinates) {
            NavigationStack { AdvancedLocation(preferences: $preferences) }
        }
        .alert(preferences.text("phone_background_location_title"), isPresented: $allowBackground) {
            Button(preferences.text("done")) {
                preferences.automaticLocation = true
                requestBackgroundLocation()
            }
            Button(preferences.text("cancel"), role: .cancel) {}
        } message: { Text(preferences.text("phone_background_location_body")) }
        .onChange(of: location.fix) { _, fix in
            guard let fix else { return }
            preferences = preferences.useDeviceLocation(.init(latitude: fix.coordinate.latitude,
                                                               longitude: fix.coordinate.longitude), confirmed: false)
        }
        .onAppear {
            if setup && !preferences.triedSetupLocation && !preferences.locationConfirmed {
                preferences.triedSetupLocation = true
                location.find()
            }
        }
        .onDisappear { location.cancel() }
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
        guard let lat = PhonePrayerRules.shared.parseCoordinate(value: latitude)?.doubleValue,
              let lng = PhonePrayerRules.shared.parseCoordinate(value: longitude)?.doubleValue else { return nil }
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
                Button(preferences.text("apply")) {
                    guard let coordinates else { return }
                    var next = preferences
                    if coordinates != next.coordinates { next.custom = coordinates }
                    if next.coordinates != preferences.coordinates || zone != preferences.zoneID {
                        next.locationConfirmed = false
                        next.automaticLocation = false
                        next.travelRefresh = false
                    }
                    next.zoneID = zone
                    preferences = next
                    if preferences == next { dismiss() }
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
