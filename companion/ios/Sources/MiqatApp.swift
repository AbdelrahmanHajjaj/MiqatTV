import SwiftUI
import UserNotifications

@main
@MainActor
struct MiqatApp: App {
    @UIApplicationDelegateAdaptor(PhoneApplicationDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store = PhoneStore()
    @StateObject private var notifications = PrayerNotifications()
    @State private var tab = "prayer"
    @State private var setup = false
    @State private var schedule = PrayerSchedule()

    var body: some Scene {
        WindowGroup {
            TabView(selection: $tab) {
                NavigationStack {
                    PrayerHome(preferences: $store.preferences, schedule: schedule)
                }
                .tabItem { Label(store.preferences.text("phone_home"), systemImage: "sun.horizon") }
                .tag("prayer")
                NavigationStack {
                    PhoneSettings(preferences: $store.preferences, schedule: schedule, notifications: notifications,
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
                    PhoneSetup(preferences: $store.preferences, schedule: schedule, notifications: notifications)
                }
                .interactiveDismissDisabled()
            }
            .onAppear {
                if !store.preferences.setupStarted { setup = true }
            }
            .onChange(of: store.preferences, initial: true) { _, _ in refreshAlerts() }
            .onChange(of: notifications.permission) { _, _ in refreshAlerts() }
            .onChange(of: scenePhase) { _, phase in if phase == .active { refreshAlerts() } }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                refreshAlerts()
            }
        }
    }

    private func refreshAlerts() {
        notifications.refresh(preferences: store.preferences, schedule: schedule)
    }
}

final class PhoneApplicationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}

struct PrayerHome: View {
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let day = schedule.current(at: context.date, prefs: preferences)
            let next = day.next(at: context.date)
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(preferences.text("phone_greeting")).font(.title2.weight(.semibold))
                        Text(preferences.arabic ? "ميقات" : "Miqat").font(.largeTitle.bold())
                        Text(PhoneData.verse)
                            .font(.title3)
                            .environment(\.layoutDirection, .rightToLeft)
                    }
                    .foregroundStyle(PhoneAppearance.color("plaster-100", preferences: preferences))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)
                    .background {
                        ZStack {
                            PhoneAppearance.color("lapis-900", preferences: preferences)
                            if preferences.showArtwork, let url = Bundle.main.url(forResource: preferences.background,
                                withExtension: preferences.background == "miqat_signature" ? "png" : "jpg"),
                               let image = UIImage(contentsOfFile: url.path) {
                                GeometryReader { area in
                                    Image(uiImage: image).resizable().scaledToFill()
                                        .frame(width: area.size.width, height: area.size.height)
                                        .clipped().accessibilityHidden(true)
                                }
                                Color.black.opacity(0.38)
                            }
                        }
                    }
                    .clipShape(.rect(cornerRadius: 24))
                    VStack(alignment: .leading, spacing: 10) {
                        NavigationLink {
                            HijriMonth(preferences: preferences, schedule: schedule)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(preferences.hijriDate(context.date)).font(.headline)
                                Text(preferences.gregorianDate(context.date)).font(.subheadline)
                            }
                        }
                        NavigationLink {
                            LocationSettings(preferences: $preferences, schedule: schedule)
                        } label: {
                            Label(preferences.locationConfirmed ? preferences.city.name(preferences.arabic)
                                  : preferences.text("phone_confirm_location"), systemImage: "location")
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
                                Text(preferences.time(nextDate))
                            }
                            Spacer()
                            let seconds = max(0, Int(ceil(nextDate.timeIntervalSince(context.date))))
                            Text(preferences.displayDigits(String(format: "%d:%02d:%02d",
                                 seconds / 3600, seconds / 60 % 60, seconds % 60)))
                                .font(.title2.monospacedDigit())
                        }
                        .padding()
                        .foregroundStyle(PhoneAppearance.color("plaster-100", preferences: preferences))
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
                }
                .padding()
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(preferences.text("phone_home"))
        .navigationBarTitleDisplayMode(.inline)
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
                .padding(12)
                .background(index == next ? Color.accentColor.opacity(0.12) : .clear)
                if index < times.count - 1 { Divider() }
            }
        }
        .background(.background, in: .rect(cornerRadius: 16))
        .clipShape(.rect(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16).strokeBorder(.quaternary).allowsHitTesting(false)
        }
    }
}

struct PhoneSetup: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var preferences: PhonePreferences
    let schedule: PrayerSchedule
    @ObservedObject var notifications: PrayerNotifications
    @State private var stage = 0

    var body: some View {
        Group {
            switch stage {
            case 1:
                LocationSettings(preferences: $preferences, schedule: schedule, setup: true)
                    .safeAreaInset(edge: .bottom) {
                        Button(preferences.text("done")) {
                            stage = 2
                            Task {
                                if notifications.permission == .notDetermined {
                                    _ = await notifications.authorize()
                                }
                            }
                        }
                            .buttonStyle(.borderedProminent)
                            .disabled(!preferences.locationConfirmed)
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(.regularMaterial)
                    }
            case 2:
                Form {
                    DisplaySettings(preferences: $preferences)
                    Button(preferences.text("done")) {
                        preferences.setupFinished = true
                        dismiss()
                    }
                }
                .navigationTitle(preferences.text("phone_appearance"))
            default:
                VStack(spacing: 20) {
                    Image(systemName: "sun.horizon").font(.system(size: 64)).accessibilityHidden(true)
                    Text(preferences.text("setup_welcome")).font(.largeTitle.bold())
                    Button(preferences.text("setup_start")) {
                        preferences.setupStarted = true
                        stage = preferences.locationConfirmed ? 2 : 1
                    }
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
            }
        }
        .onAppear {
            if preferences.setupStarted { stage = preferences.locationConfirmed ? 2 : 1 }
        }
    }
}
