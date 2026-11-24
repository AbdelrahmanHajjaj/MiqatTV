import AVFoundation
import Combine
import CoreLocation
import SwiftUI
import UserNotifications

@main
@MainActor
struct MiqatApp: App {
    @UIApplicationDelegateAdaptor(PhoneApplicationDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store = PhoneStore.shared
    @StateObject private var notifications = PrayerNotifications.shared
    @StateObject private var library = AdhanLibrary()
    @StateObject private var adhan = PhoneAdhan()
    @StateObject private var radio = QuranRadio()
    @StateObject private var tvs = PairedTVs()
    @State private var tab = "prayer"
    @State private var setup = false
    @State private var showAdhan = false
    @State private var playingAdhan: String?
    private let schedule = PrayerSchedule.shared

    private var settings: Binding<PhonePreferences> {
        Binding(get: { store.preferences }, set: store.save)
    }

    var body: some Scene {
        WindowGroup {
            TabView(selection: $tab) {
                NavigationStack {
                    PrayerHome(preferences: settings, schedule: schedule, resumeSetup: { setup = true })
                }
                .tabItem { Label(store.preferences.text("phone_home"), systemImage: "sun.horizon") }
                .tag("prayer")
                NavigationStack { YourTVs(preferences: store.preferences) }
                    .tabItem { Label(store.preferences.text("tvs"), systemImage: "tv") }
                    .tag("tvs")
                NavigationStack {
                    PhoneSettings(preferences: settings, schedule: schedule, notifications: notifications,
                                  startSetup: { setup = true })
                }
                .tabItem { Label(store.preferences.text("settings"), systemImage: "gearshape") }
                .tag("settings")
            }
            .environment(\.layoutDirection, store.preferences.arabic ? .rightToLeft : .leftToRight)
            .environment(\.locale, Locale(identifier: store.preferences.arabic ? "ar" : "en"))
            .modifier(PhoneDisplayScale(preferences: store.preferences))
            .sheet(isPresented: $setup) {
                NavigationStack {
                    PhoneSetup(preferences: settings, schedule: schedule, notifications: notifications)
                }
                .interactiveDismissDisabled()
            }
            .onAppear {
                if store.loadIfNeeded(), !store.preferences.setupStarted { setup = true }
                delegate.presentAdhan = presentAdhan
                adhan.onPlayback = { radio.pause(forAdhan: true) }
                if scenePhase == .active { delegate.presentPendingAdhan() }
            }
            .fullScreenCover(isPresented: $showAdhan) { AdhanPlayback(preferences: store.preferences) }
            .onChange(of: adhan.prayer) { _, prayer in
                if prayer == nil { showAdhan = false }
                else if store.preferences.adhan.showScreen && !setup { showAdhan = true }
            }
            .onChange(of: store.preferences, initial: true) { _, prefs in
                refreshAlerts()
                radio.refreshMetadata(prefs)
            }
            .onChange(of: notifications.permission) { _, _ in refreshAlerts() }
            .onReceive(library.removedRecording, perform: removeVoiceReferences)
            .onOpenURL { url in if url.scheme == "miqat" && url.host == "prayer" { tab = "prayer" } }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active {
                    delegate.presentPendingAdhan()
                    delegate.travel.update(store.preferences)
                    delegate.travel.refresh()
                    refreshAlerts()
                }
                if phase == .background { adhan.stop(previewOnly: true) }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                refreshAlerts()
            }
            .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)
                .receive(on: RunLoop.main)) { event in
                if (event.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue {
                    adhan.stop()
                    if radio.wantsPlay { radio.pause() }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)
                .receive(on: RunLoop.main)) { event in
                if (event.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt)
                    == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                    adhan.pauseForRouteChange()
                    if radio.wantsPlay { radio.pause() }
                }
            }
            .environmentObject(adhan)
            .environmentObject(library)
            .environmentObject(radio)
            .environmentObject(tvs)
            .environment(\.requestBackgroundLocation, { delegate.travel.requestBackground() })
            .alert(store.preferences.text("phone_save_failed"), isPresented: $store.saveFailed) {
                Button(store.preferences.text("done"), role: .cancel) {}
            }
        }
    }

    private func refreshAlerts() {
        guard store.loadIfNeeded() else { return }
        schedule.publishWidget(preferences: store.preferences)
        notifications.refresh(preferences: store.preferences, schedule: schedule)
    }

    private func removeVoiceReferences(_ id: String) {
        var prefs = store.preferences
        if prefs.adhan.selection.voice == id { prefs.adhan.selection.voice = AdhanPreferences.defaultVoice }
        if prefs.adhan.fajrVoice == id { prefs.adhan.fajrVoice = PhoneData.defaultFajrVoice }
        for index in prefs.prayerAlerts.indices where prefs.prayerAlerts[index].audio.voice == id {
            prefs.prayerAlerts[index].audio.voice = "inherit"
        }
        if prefs != store.preferences { store.save(prefs) }
    }

    private func presentAdhan(_ request: AdhanNotification, tapped: Bool) -> Bool {
        if tapped { tab = "prayer" }
        if playingAdhan == request.claimID && adhan.prayer != nil {
            if tapped && !setup { showAdhan = true }
            return true
        }
        let prefs = store.preferences
        guard notifications.claimAdhan(request, preferences: prefs, schedule: schedule) else { return false }
        let index = request.prayer
        let settings = prefs.prayerAlerts[index]
        let selection = prefs.adhan.forPrayer(index, settings: settings)
        let fallback = index == 0 ? PhoneData.defaultFajrVoice : AdhanPreferences.defaultVoice
        let voice = library.url(selection.voice) != nil ? selection.voice : fallback
        guard let url = library.url(voice) else { return false }
        let prayerTime = Date(timeIntervalSince1970: request.time / 1000)
        let title = request.isTest ? prefs.text("phone_test_adhan")
            : prefs.prayerNames()[index] + " · " + prefs.time(prayerTime)
        guard adhan.play(url: url, name: library.name(voice, preferences: prefs), selection: selection,
                         prayer: index, mediaTitle: title, artwork: PhoneRemotePlayback.artwork(prefs)) else {
            return false
        }
        playingAdhan = request.claimID
        if tapped && !setup { showAdhan = true }
        return true
    }
}

@MainActor
final class PhoneApplicationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var presentAdhan: ((AdhanNotification, Bool) -> Bool)?
    private var pendingAdhan: AdhanNotification?
    private var travelPreferences: AnyCancellable?
    let travel = TravelLocation()
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        PrayerBackgroundRefresh.register()
        travel.authorizationChanged = { [weak self] in self?.travel.update(PhoneStore.shared.preferences) }
        travel.didLocate = { fix, useSystemZone in
            let store = PhoneStore.shared
            var prefs = store.preferences
            guard prefs.locationConfirmed, prefs.automaticLocation || prefs.travelRefresh else { return }
            let point = fix.coordinate
            let city = PhoneData.closestCity(latitude: point.latitude, longitude: point.longitude).id
            let coordinates = PhonePreferences.Coordinates(latitude: point.latitude, longitude: point.longitude)
            let zone = useSystemZone ? "" : prefs.zoneID
            guard prefs.cityID != city || prefs.custom != coordinates || prefs.zoneID != zone else { return }
            prefs.cityID = city
            prefs.custom = coordinates
            prefs.zoneID = zone
            store.save(prefs)
            PrayerNotifications.shared.refresh(preferences: store.preferences, schedule: PrayerSchedule.shared)
            PrayerSchedule.shared.publishWidget(preferences: store.preferences)
        }
        travelPreferences = PhoneStore.shared.$preferences.sink { [weak self] prefs in
            self?.travel.update(prefs)
        }
        return true
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        guard let request = AdhanNotification(notification.request),
              await center.notificationSettings().soundSetting == .enabled else {
            return [.banner, .list, .sound]
        }
        let played = await MainActor.run { self.presentAdhan?(request, false) ?? false }
        return played ? [.banner, .list] : [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let request = AdhanNotification(response.notification.request),
              await center.notificationSettings().soundSetting == .enabled else { return }
        await MainActor.run {
            self.pendingAdhan = request
            if UIApplication.shared.applicationState == .active { self.presentPendingAdhan() }
        }
    }

    func presentPendingAdhan() {
        guard let presentAdhan, let pendingAdhan else { return }
        self.pendingAdhan = nil
        _ = presentAdhan(pendingAdhan, true)
    }
}

struct PrayerHome: View {
    @EnvironmentObject private var audio: PhoneAdhan
    @EnvironmentObject private var radio: QuranRadio
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    var preview = false
    var resumeSetup: (() -> Void)?
    @StateObject private var location = PhoneLocation()
    @State private var showPlayback = false
    @State private var showRadio = false

    private var ground: Color { PhoneAppearance.color("lapis-900", preferences: preferences) }
    private var ink: Color { PhoneAppearance.color("plaster-100", preferences: preferences) }
    private var accent: Color { PhoneAppearance.color("gold-400", preferences: preferences) }
    private var scene: String { preferences.showArtwork ? preferences.background : "plain" }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let day = schedule.current(at: context.date, prefs: preferences)
            let next = day.next(at: context.date)
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let resumeSetup, !preferences.setupFinished {
                        Button(action: resumeSetup) {
                            Label(preferences.text("setup_resume"), systemImage: "gearshape")
                        }
                        .buttonStyle(.bordered)
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        Text(preferences.text("phone_greeting")).font(.title2.weight(.semibold))
                        Text(preferences.arabic ? "ميقات" : "Miqat").font(.largeTitle.bold())
                        Text(PhoneData.verse)
                            .font(.title3)
                            .environment(\.layoutDirection, .rightToLeft)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)
                    .background {
                        PhoneBackdrop(ground: ground, scene: scene, shade: 0.74, portrait: false)
                    }
                    .clipShape(.rect(cornerRadius: 24))
                    VStack(alignment: .leading, spacing: 10) {
                        if preview {
                            dateLabels(context.date)
                            Label(preferences.city.name(preferences.arabic), systemImage: "location")
                        } else {
                            NavigationLink {
                                HijriMonth(preferences: preferences, schedule: schedule)
                            } label: { dateLabels(context.date) }
                            cityAction
                        }
                        let events = HijriObservance.upcoming(from: context.date, preferences: preferences)
                        ForEach(events, id: \.self) { event in
                            Label(event, systemImage: "calendar.badge.clock").font(.subheadline)
                        }
                    }
                    if let next, preferences.prayerNames().indices.contains(Int(next.next)) {
                        let nextDate = Date(timeIntervalSince1970: next.nextTime / 1000)
                        HStack {
                            VStack(alignment: .leading) {
                                Text(preferences.text("phone_next")).font(.subheadline)
                                Text(preferences.prayerNames()[Int(next.next)]).font(.title2.bold())
                                    .foregroundStyle(accent)
                                Text(preferences.time(nextDate))
                            }
                            Spacer()
                            let seconds = max(0, Int(ceil(nextDate.timeIntervalSince(context.date))))
                            Text(preferences.displayDigits(String(format: "%d:%02d:%02d",
                                 seconds / 3600, seconds / 60 % 60, seconds % 60)))
                                .font(.title2.monospacedDigit())
                                .foregroundStyle(accent)
                        }
                        .padding()
                        .background(PhoneAppearance.color("lapis-800", preferences: preferences), in: .rect(cornerRadius: 16))
                    }
                    if var times = day.today {
                        if let next, next.next == 0, let fajr = day.tomorrow?.first, times[0] <= context.date {
                            times[0] = fajr
                        }
                        PrayerTimesList(preferences: preferences, times: times, next: next.map { Int($0.next) })
                    } else {
                        Text(preferences.text("phone_unavailable")).foregroundStyle(.secondary)
                    }
                    if !preview && audio.prayer != nil {
                        Button { showPlayback = true } label: {
                            Label(preferences.text("phone_adhan_controls"), systemImage: "waveform")
                                .foregroundStyle(ground)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    if !preview && radio.hasSession {
                        Button {
                            if radio.wantsPlay { radio.pause() }
                            else if let station = RadioStation.catalog.first(where: { $0.id == preferences.radioStation }) {
                                audio.stop(previewOnly: true)
                                radio.play(station: station, preferences: preferences)
                            }
                        } label: {
                            Label(preferences.text(radio.wantsPlay ? "phone_pause" : "phone_resume"),
                                  systemImage: radio.wantsPlay ? "pause.fill" : "play.fill")
                        }
                        .disabled(audio.prayer != nil)
                    }
                }
                .padding()
                .foregroundStyle(ink)
                .tint(accent)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
            .background { PhoneBackdrop(ground: ground, scene: scene, shade: 0.82).ignoresSafeArea() }
        }
        .navigationTitle(preferences.text(preview ? "appearancePreview" : "phone_home"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(ground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onChange(of: location.fix) { _, fix in
            guard let fix, preferences.locationConfirmed else { return }
            preferences = preferences.useDeviceLocation(.init(latitude: fix.coordinate.latitude,
                                                               longitude: fix.coordinate.longitude), confirmed: true)
        }
        .onChange(of: preferences.locationConfirmed) { _, confirmed in
            if !confirmed { location.cancel() }
        }
        .onDisappear { location.cancel() }
        .toolbar {
            if !preview {
                NavigationLink {
                    QiblaView(preferences: preferences)
                } label: {
                    Image(systemName: "location.north.line").accessibilityLabel(preferences.text("phone_qibla"))
                }
                Button { showRadio = true } label: {
                    Image(systemName: "radio").accessibilityLabel(preferences.text("phone_radio"))
                }
            }
        }
        .sheet(isPresented: $showPlayback) { AdhanPlayback(preferences: preferences) }
        .sheet(isPresented: $showRadio) {
            NavigationStack {
                RadioSettings(preferences: $preferences).toolbar {
                    Button(preferences.text("done")) { showRadio = false }
                }
            }
        }
    }

    private func dateLabels(_ date: Date) -> some View {
        VStack(alignment: .leading) {
            Text(preferences.hijriDate(date)).font(.headline)
            Text(preferences.gregorianDate(date)).font(.subheadline)
        }
    }

    private var cityAction: some View {
        let title = preferences.locationConfirmed ? preferences.city.name(preferences.arabic)
            : preferences.text("phone_confirm_location")
        return Group {
            if location.pending {
                ProgressView(preferences.text("phone_location_working"))
                Button(preferences.text("phone_location_cancel")) { location.cancel() }
            } else if preferences.locationConfirmed && location.canLocate {
                Button { location.find() } label: { Label(title, systemImage: "location") }
            } else {
                NavigationLink {
                    LocationSettings(preferences: $preferences, schedule: schedule)
                } label: { Label(title, systemImage: "location") }
            }
            if location.failed {
                Text(preferences.text(location.permissionBlocked ? "phone_location_denied" : "phone_location_timeout"))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct PrayerTimesList: View {
    let preferences: PhonePreferences
    let times: [Date]
    var next: Int?
    private let icons = ["sunrise", "sun.horizon", "sun.max", "sun.min", "sunset", "moon.stars"]

    var body: some View {
        let names = preferences.prayerNames()
        VStack(spacing: 0) {
            ForEach(times.indices, id: \.self) { index in
                HStack {
                    Label(names[index], systemImage: icons[index])
                    Spacer()
                    Text(preferences.time(times[index])).monospacedDigit()
                }
                .fontWeight(index == next ? .semibold : .regular)
                .foregroundStyle(PhoneAppearance.color(index == next ? "gold-400" : "plaster-100",
                                                        preferences: preferences))
                .padding(12)
                .background(index == next ? PhoneAppearance.color("lapis-700", preferences: preferences) : .clear)
                if index < times.count - 1 { Divider() }
            }
        }
        .background(PhoneAppearance.color("lapis-800", preferences: preferences), in: .rect(cornerRadius: 16))
        .clipShape(.rect(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(PhoneAppearance.color("mist-400", preferences: preferences).opacity(0.2))
                .allowsHitTesting(false)
        }
    }
}

struct PhoneSetup: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    @ObservedObject var notifications: PrayerNotifications
    @State private var continuing = false

    var body: some View {
        Group {
            if preferences.setupLocationContinued == true {
                PhoneAppearanceEditor(preferences: $preferences, schedule: schedule) {
                    preferences.setupFinished = true
                    if preferences.setupFinished { dismiss() }
                }
            } else if preferences.setupStarted {
                LocationSettings(preferences: $preferences, schedule: schedule, setup: true)
                    .safeAreaInset(edge: .bottom) {
                        VStack {
                            Button(preferences.text("setup_next"), action: continueSetup)
                                .buttonStyle(.borderedProminent)
                                .disabled(!preferences.locationConfirmed)
                            if !preferences.locationConfirmed {
                                Button(preferences.text("setup_later")) {
                                    preferences.setupLocationContinued = true
                                }
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(.regularMaterial)
                    }
            } else {
                VStack(spacing: 20) {
                    Image(systemName: "sun.horizon").font(.system(size: 64)).accessibilityHidden(true)
                    Text(preferences.text("setup_welcome")).font(.largeTitle.bold())
                    Button(preferences.text("setup_start")) { preferences.setupStarted = true }
                        .buttonStyle(.borderedProminent)
                }
                .multilineTextAlignment(.center)
                .padding()
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(preferences.text("setup_skip")) {
                    preferences.setupStarted = true
                    dismiss()
                }
                .disabled(continuing)
            }
        }
        .disabled(continuing)
    }

    private func continueSetup() {
        guard preferences.locationConfirmed, !continuing else { return }
        continuing = true
        let wantsAlerts = preferences.setupAlerts ?? true
        Task {
            defer { continuing = false }
            var allowed = false
            if wantsAlerts { allowed = await notifications.authorize() }
            guard preferences.locationConfirmed else { return }
            var next = preferences
            next.alertsEnabled = allowed
            next.setupLocationContinued = true
            preferences = next
        }
    }
}
