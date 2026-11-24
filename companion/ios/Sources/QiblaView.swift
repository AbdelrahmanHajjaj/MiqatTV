import CoreLocation
import MapKit
import MiqatKit
import SwiftUI

struct QiblaView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @StateObject private var location = PhoneLocation(compass: true)
    let preferences: PhonePreferences
    @State private var map = false
    @State private var camera: MapCameraPosition = .automatic
    @State private var visibleRegion: MKCoordinateRegion?
    private static let kaaba = CLLocationCoordinate2D(latitude: 21.4225241, longitude: 39.8261818)

    var body: some View {
        let origin = location.fix?.coordinate
        let bearing = origin.flatMap {
            QiblaRules.shared.bearing(latitude: $0.latitude, longitude: $0.longitude)?.doubleValue
        }
        return Group {
            if let origin {
                if let bearing {
                    if map { qiblaMap(origin, bearing: bearing) }
                    else { compass(bearing) }
                } else {
                    Text(preferences.text("qibla_at_kaaba"))
                }
            } else {
                VStack(spacing: 16) {
                    if location.pending { ProgressView(preferences.text("phone_location_working")) }
                    else {
                        Text(preferences.text(location.failed ? "qibla_location_unavailable" : "qibla_location_required"))
                        Button(preferences.text("qibla_retry_location")) { location.find() }.buttonStyle(.borderedProminent)
                        if location.failed {
                            Button(preferences.text("phone_location_settings")) {
                                if let settings = URL(string: UIApplication.openSettingsURLString) { openURL(settings) }
                            }
                        }
                    }
                }
                .multilineTextAlignment(.center)
                .padding()
            }
        }
        .navigationTitle(preferences.text("phone_qibla"))
        .toolbar {
            if let origin, bearing != nil {
                Button(preferences.text(map ? "phone_compass" : "phone_map")) {
                    if !map { recenter(origin) }
                    map.toggle()
                }
            }
        }
        .onAppear { location.find() }
        .onDisappear { location.cancel() }
        .onChange(of: location.fix) { _, fix in
            if map, let fix { recenter(fix.coordinate) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { location.cancel() }
            else if phase == .active { location.find() }
        }
    }

    private func compass(_ bearing: Double) -> some View {
        let reliable = (location.heading?.headingAccuracy ?? -1) >= 0
            && (location.heading?.headingAccuracy ?? 90) <= 25
            && (location.heading?.trueHeading ?? -1) >= 0
        let heading = reliable ? location.heading?.trueHeading ?? 0 : 0
        let relative = (bearing - heading + 360).truncatingRemainder(dividingBy: 360)
        return ScrollView {
            VStack(spacing: 22) {
                if reliable {
                    ZStack {
                        Circle().strokeBorder(.secondary.opacity(0.5), lineWidth: 2)
                        Image(systemName: "location.north.fill")
                            .font(.system(size: 82, weight: .medium))
                            .foregroundStyle(.tint)
                            .rotationEffect(.degrees(relative))
                    }
                    .frame(width: 190, height: 190)
                    .accessibilityHidden(true)
                }
                bearingLabel(bearing)
                if reliable {
                    Text(preferences.text("qibla_direction").replacingOccurrences(of: "%s",
                        with: preferences.displayDigits(String(Int(relative.rounded()) % 360))))
                } else {
                    let key = !CLLocationManager.headingAvailable() ? "qibla_no_sensor"
                        : location.heading == nil ? "qibla_wait" : "qibla_accuracy"
                    Text(preferences.text(key)).foregroundStyle(.secondary)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
        }
    }

    private func bearingLabel(_ bearing: Double) -> some View {
        VStack(spacing: 4) {
            Text(preferences.text("qibla_bearing")).font(.headline)
            Text(preferences.displayDigits(String(format: "%.1f°", bearing)))
                .font(.title.monospacedDigit())
        }
    }

    private func qiblaMap(_ origin: CLLocationCoordinate2D, bearing: Double) -> some View {
        Map(position: $camera, interactionModes: [.pan, .zoom]) {
            Marker(preferences.text("phone_location_title"), systemImage: "location", coordinate: origin)
            Marker(preferences.text("phone_kaaba"), coordinate: Self.kaaba)
            MapPolyline(coordinates: [origin, Self.kaaba], contourStyle: .geodesic).stroke(.teal, lineWidth: 4)
        }
        .mapControls { MapScaleView() }
        .onMapCameraChange(frequency: .onEnd) { context in visibleRegion = context.region }
        .safeAreaInset(edge: .bottom) {
            VStack {
                bearingLabel(bearing)
                HStack {
                    Button { adjustZoom(0.5) } label: { Image(systemName: "plus.magnifyingglass") }
                        .accessibilityLabel(preferences.text("phone_map_zoom_in"))
                    Button { recenter(origin) } label: {
                        Label(preferences.text("phone_location_title"), systemImage: "location.fill")
                    }
                    Button { adjustZoom(2) } label: { Image(systemName: "minus.magnifyingglass") }
                        .accessibilityLabel(preferences.text("phone_map_zoom_out"))
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(.regularMaterial)
        }
    }

    private func recenter(_ origin: CLLocationCoordinate2D) {
        let region = MKCoordinateRegion(center: origin, latitudinalMeters: 4000, longitudinalMeters: 4000)
        visibleRegion = region
        camera = .region(region)
    }

    private func adjustZoom(_ factor: Double) {
        guard var region = visibleRegion else { return }
        region.span.latitudeDelta = min(170, max(0.0001, region.span.latitudeDelta * factor))
        region.span.longitudeDelta = min(350, max(0.0001, region.span.longitudeDelta * factor))
        visibleRegion = region
        camera = .region(region)
    }
}
