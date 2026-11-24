import CoreLocation
import Foundation
import MiqatKit

final class TravelLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var monitoring = false
    private var requested = false
    var didLocate: ((CLLocation) -> Void)?
    var authorizationChanged: (() -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func update(_ prefs: PhonePreferences) {
        let shouldMonitor = prefs.locationConfirmed && prefs.automaticLocation
            && manager.authorizationStatus == .authorizedAlways && CLLocationManager.significantLocationChangeMonitoringAvailable()
        guard shouldMonitor != monitoring else { return }
        monitoring = shouldMonitor
        if shouldMonitor { manager.startMonitoringSignificantLocationChanges() }
        else { manager.stopMonitoringSignificantLocationChanges() }
    }

    func refresh(_ prefs: PhonePreferences) {
        guard prefs.locationConfirmed && prefs.travelRefresh && !monitoring && !requested,
              [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus) else { return }
        requested = true
        manager.requestLocation()
    }

    func requestBackground() {
        if [.notDetermined, .authorizedWhenInUse].contains(manager.authorizationStatus) {
            manager.requestAlwaysAuthorization()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { authorizationChanged?() }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard requested || monitoring else { return }
        requested = false
        guard let fix = locations.last,
              LocationRules.shared.usableLocation(latitude: fix.coordinate.latitude, longitude: fix.coordinate.longitude,
                  accuracyMetres: fix.horizontalAccuracy, ageMs: -fix.timestamp.timeIntervalSinceNow * 1000) else { return }
        didLocate?(fix)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { requested = false }
}
