import CoreLocation
import Foundation
import MiqatKit

final class TravelLocation: NSObject, CLLocationManagerDelegate {
    private enum Mode { case off, onOpen, automatic }

    private var manager = CLLocationManager()
    private var mode = Mode.off
    private var requested = false
    var didLocate: ((CLLocation, Bool) -> Void)?
    var authorizationChanged: (() -> Void)?

    override init() {
        super.init()
        configureManager()
    }

    func update(_ prefs: PhonePreferences) {
        let authorization = manager.authorizationStatus
        let next: Mode
        if prefs.locationConfirmed && prefs.automaticLocation && authorization == .authorizedAlways
            && CLLocationManager.significantLocationChangeMonitoringAvailable() {
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
        if [.notDetermined, .authorizedWhenInUse].contains(manager.authorizationStatus) {
            manager.requestAlwaysAuthorization()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager === self.manager { authorizationChanged?() }
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
