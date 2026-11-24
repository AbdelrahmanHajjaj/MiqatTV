import AppIntents
import SwiftUI
import WidgetKit

enum PrayerWidgetStyle: String, AppEnum {
    case adaptive, next, strip, timetable, circle

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Prayer layout")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .adaptive: "Adaptive", .next: "Next prayer", .strip: "Prayer strip", .timetable: "Daily timetable", .circle: "Circle",
    ]
}

struct PrayerWidgetOptions: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Prayer widget"

    @Parameter(title: "Layout", default: .adaptive) var layout: PrayerWidgetStyle
    @Parameter(title: "Hijri date", default: true) var hijri: Bool
    @Parameter(title: "Gregorian date", default: false) var gregorian: Bool
    @Parameter(title: "120% text size", default: false) var large: Bool
    @Parameter(title: "Theme") var palette: PrayerWidgetPalette?
    @Parameter(title: "Background scene") var scene: PrayerWidgetScene?
    @Parameter(title: "Background opacity (%)", default: 100, inclusiveRange: (0, 100)) var opacityPercent: Int
}

struct PrayerWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let options: PrayerWidgetOptions

    var current: WidgetSnapshot.Day? { snapshot?.days.last { $0.start <= date } }

    var next: (index: Int, time: Date)? {
        guard let snapshot else { return nil }
        return snapshot.days.flatMap { day in
            [0, 2, 3, 4, 5].map { ($0, day.times[$0]) }
        }.filter { $0.1 > date }.min { $0.1 < $1.1 }
    }

    var previous: Date? {
        snapshot?.days.flatMap { day in [0, 2, 3, 4, 5].map { day.times[$0] } }.filter { $0 <= date }.max()
    }
}

struct PrayerWidgetProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> PrayerWidgetEntry {
        .init(date: .now, snapshot: nil, options: PrayerWidgetOptions())
    }

    func snapshot(for configuration: PrayerWidgetOptions, in context: Context) async -> PrayerWidgetEntry {
        .init(date: .now, snapshot: WidgetSnapshot.load(), options: configuration)
    }

    func timeline(for configuration: PrayerWidgetOptions, in context: Context) async -> Timeline<PrayerWidgetEntry> {
        let now = Date.now
        let snapshot = WidgetSnapshot.load()
        let horizon = now.addingTimeInterval(36 * 3600)
        var updates = Set([now])
        for day in snapshot?.days ?? [] {
            for time in [day.start] + day.times where time > now && time <= horizon { updates.insert(time) }
        }
        if configuration.layout == .circle {
            for halfHour in 1...72 { updates.insert(now.addingTimeInterval(Double(halfHour) * 1800)) }
        }
        let entries = updates.sorted().map { PrayerWidgetEntry(date: $0, snapshot: snapshot, options: configuration) }
        return Timeline(entries: entries, policy: .after(horizon))
    }
}

struct PrayerWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @ScaledMetric(relativeTo: .body) private var bodyPoints: CGFloat = 15
    let entry: PrayerWidgetEntry
    private let icons = ["sunrise", "sun.horizon", "sun.max", "sun.min", "sunset", "moon.stars"]

    var body: some View {
        Group {
            if let snapshot = entry.snapshot, let next = entry.next {
                switch entry.options.layout {
                case .circle: circle(snapshot, next: next)
                case .next: summary(snapshot, next: next)
                case .strip where family != .systemSmall: strip(snapshot, next: next)
                case .timetable where family != .systemSmall: timetable(snapshot, next: next)
                case .adaptive where family == .systemLarge: timetable(snapshot, next: next)
                case .adaptive where family == .systemMedium: strip(snapshot, next: next)
                default: summary(snapshot, next: next)
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Image(systemName: "sun.horizon")
                    Text(entry.snapshot?.arabic == true ? "افتح ميقات" : "Open Miqat").font(textFont(1))
                }
            }
        }
        .font(textFont(1))
        .foregroundStyle(color("plaster-100", fallback: .white))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .environment(\.layoutDirection, entry.snapshot?.arabic == true ? .rightToLeft : .leftToRight)
        .containerBackground(for: .widget) {
            PrayerWidgetBackdrop(sceneID: entry.snapshot?.sceneID(for: entry.options.scene?.id),
                ground: color("lapis-900", fallback: Color(red: 0.04, green: 0.18, blue: 0.20)),
                opacity: WidgetSnapshot.opacity(percent: entry.options.opacityPercent))
        }
        .widgetURL(URL(string: "miqat://prayer"))
    }

    private func summary(_ snapshot: WidgetSnapshot, next: (index: Int, time: Date), inCircle: Bool = false) -> some View {
        VStack(spacing: 4) {
            Label(snapshot.names[next.index], systemImage: icons[next.index])
                .font(textFont(inCircle ? 1.2 : 1).weight(.semibold))
                .lineLimit(1)
            Text(clock(next.time, snapshot)).font(textFont(inCircle ? 1 : 1.25).monospacedDigit())
                .lineLimit(1)
            Text(timerInterval: entry.date...next.time, countsDown: true)
                .font(textFont(inCircle ? 0.8 : 1).monospacedDigit())
                .multilineTextAlignment(.center)
            dates
        }
        .minimumScaleFactor(0.75)
    }

    private func circle(_ snapshot: WidgetSnapshot, next: (index: Int, time: Date)) -> some View {
        let ratio = entry.previous.map { next.time.timeIntervalSince(entry.date) / next.time.timeIntervalSince($0) } ?? 0
        return ZStack {
            Circle().strokeBorder(.white.opacity(0.20), lineWidth: 3)
            Circle().trim(from: 0, to: min(1, max(0, ratio)))
                .stroke(color("gold-400", fallback: .yellow), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90)).padding(2)
            summary(snapshot, next: next, inCircle: true).padding(10)
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func strip(_ snapshot: WidgetSnapshot, next: (index: Int, time: Date)) -> some View {
        VStack(spacing: 8) {
            dates
            HStack(spacing: 4) {
                ForEach(0..<6, id: \.self) { index in
                    VStack(spacing: 3) {
                        Image(systemName: icons[index]).accessibilityHidden(true)
                        Text(snapshot.names[index]).font(textFont(0.75)).lineLimit(1).minimumScaleFactor(0.6)
                        Text(clock(prayerTime(index, next: next), snapshot))
                            .font(textFont(0.75).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.6)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .background(index == next.index ? .white.opacity(0.17) : .clear, in: .rect(cornerRadius: 6))
                }
            }
        }
    }

    private func timetable(_ snapshot: WidgetSnapshot, next: (index: Int, time: Date)) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            summary(snapshot, next: next)
            ForEach(0..<6, id: \.self) { index in
                HStack {
                    Label(snapshot.names[index], systemImage: icons[index])
                    Spacer()
                    Text(clock(prayerTime(index, next: next), snapshot))
                }
                .fontWeight(index == next.index ? .semibold : .regular)
            }
        }
    }

    private var dates: some View {
        VStack(spacing: 1) {
            if entry.options.hijri, let date = entry.current?.hijri { Text(date).lineLimit(1) }
            if entry.options.gregorian, let date = entry.current?.gregorian { Text(date).lineLimit(1) }
        }
        .font(textFont(0.75))
        .minimumScaleFactor(0.7)
    }

    private func textFont(_ relativeSize: CGFloat) -> Font {
        .system(size: bodyPoints * relativeSize * (entry.options.large ? 1.2 : 1))
    }

    private func prayerTime(_ index: Int, next: (index: Int, time: Date)) -> Date {
        if index == next.index { return next.time }
        return entry.current?.times[index] ?? next.time
    }

    private func clock(_ date: Date, _ snapshot: WidgetSnapshot) -> String {
        let formatter = DateFormatter()
        let language = snapshot.arabic ? "ar-EG" : "en-US"
        formatter.locale = Locale(identifier: language + (snapshot.eastern ? "-u-nu-arab" : "-u-nu-latn"))
        formatter.timeZone = TimeZone(identifier: snapshot.timezone)
        formatter.dateFormat = snapshot.clock == "24" ? "HH:mm" : "h:mm a"
        return formatter.string(from: date)
    }

    private func color(_ key: String, fallback: Color) -> Color {
        guard let hex = entry.snapshot?.colors(for: entry.options.palette?.id)[key],
              let value = UInt32(hex.dropFirst(), radix: 16) else { return fallback }
        return Color(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255,
                     blue: Double(value & 255) / 255)
    }
}

@main
struct PrayerWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "miqat.prayer", intent: PrayerWidgetOptions.self, provider: PrayerWidgetProvider()) {
            PrayerWidgetView(entry: $0)
        }
        .configurationDisplayName("Miqat Prayer")
        .description("Next prayer and the daily timetable.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
