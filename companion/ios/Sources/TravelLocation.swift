import CoreLocation
import Foundation
import MiqatKit

final class TravelLocation: NSObject, CLLocationManagerDelegate {
    private enum Mode { case off, onOpen, automatic }

    private var manager = CLLocationManager()
    private var mode = Mode.off
    private var requested = false
    private var awaitingForegroundConsent = false
    var didLocate: ((CLLocation, Bool) -> Void)?
    var authorizationChanged: (() -> Void)?

    static var supportsBackground: Bool { CLLocationManager.significantLocationChangeMonitoringAvailable() }

    override init() {
        super.init()
        configureManager()
    }

    func update(_ prefs: PhonePreferences) {
        if !prefs.locationConfirmed || !prefs.automaticLocation { awaitingForegroundConsent = false }
        let authorization = manager.authorizationStatus
        let next: Mode
        if prefs.locationConfirmed && prefs.automaticLocation && authorization == .authorizedAlways
            && Self.supportsBackground {
            next = .automatic
        } else if prefs.locationConfirmed && prefs.travelRefresh
            && [.authorizedWhenInUse, .authorizedAlways].contains(authorization) {
            next = .onOpen
        } else {
            next = .off
        }
        guard next != mode else { return }
        // A queued callback from the old manager must not satisfy a later request.
        manager.delegate = nil
        if mode == .automatic { manager.stopMonitoringSignificantLocationChanges() }
        if requested { manager.stopUpdatingLocation() }
        requested = false
        mode = next
        manager = CLLocationManager()
        configureManager()
        if mode == .automatic { manager.startMonitoringSignificantLocationChanges() }
    }

    func refresh() {
        guard mode == .onOpen, !requested else { return }
        requested = true
        manager.requestLocation()
    }

    func requestBackground() {
        guard Self.supportsBackground else { return }
        if manager.authorizationStatus == .notDetermined {
            awaitingForegroundConsent = true
            manager.requestWhenInUseAuthorization()
        } else if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager === self.manager else { return }
        if awaitingForegroundConsent && manager.authorizationStatus != .notDetermined {
            awaitingForegroundConsent = false
            if manager.authorizationStatus == .authorizedWhenInUse { manager.requestAlwaysAuthorization() }
        }
        authorizationChanged?()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard manager === self.manager else { return }
        let authorization = manager.authorizationStatus
        let canDeliver = mode == .automatic ? authorization == .authorizedAlways
            : requested && [.authorizedWhenInUse, .authorizedAlways].contains(authorization)
        guard canDeliver else { return }
        requested = false
        guard let fix = locations.last,
              LocationRules.shared.usableLocation(latitude: fix.coordinate.latitude, longitude: fix.coordinate.longitude,
                  accuracyMetres: fix.horizontalAccuracy, ageMs: -fix.timestamp.timeIntervalSinceNow * 1000) else { return }
        didLocate?(fix, mode == .automatic)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if manager === self.manager { requested = false }
    }

    private func configureManager() {
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.delegate = self
    }
}
