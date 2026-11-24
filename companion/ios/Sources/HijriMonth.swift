import MiqatKit
import SwiftUI

struct HijriObservance: Identifiable, Decodable {
    let id: String
    let month: Int32
    let day: Int32
    let lastDay: Int32?
    let en: String
    let ar: String

    private static let catalog: [Self] = PhoneData.load("hijri-events")
    private static let rules = catalog.map {
        HijriEvent(id: $0.id, month: $0.month, day: $0.day, lastDay: $0.lastDay ?? $0.day)
    }

    func name(day: Int, preferences: PhonePreferences) -> String {
        preferences.displayDigits(String(format: preferences.arabic ? ar : en, day - Int(self.day) + 1))
    }

    static func events(month: Int, day: Int) -> [Self] {
        let ids = Set(HijriEvents.shared.onDay(events: rules, month: Int32(month), day: Int32(day)).map(\.id))
        return catalog.filter { ids.contains($0.id) }
    }

    static func upcoming(from date: Date, preferences: PhonePreferences) -> [String] {
        let calendar = PrayerSchedule.hijriCalendar(preferences)
        for offset in 0...3 {
            guard let corrected = calendar.date(byAdding: .day, value: offset + preferences.hijriOffset, to: date) else {
                continue
            }
            let parts = calendar.dateComponents([.month, .day], from: corrected)
            guard let month = parts.month, let day = parts.day else { continue }
            let names = events(month: month, day: day).map { $0.name(day: day, preferences: preferences) }
            if !names.isEmpty {
                if offset == 0 { return names }
                guard let observed = calendar.date(byAdding: .day, value: offset, to: date) else { return names }
                return names.map { $0 + " · " + preferences.hijriDate(observed) }
            }
        }
        return []
    }
}

struct HijriMonth: View {
    let preferences: PhonePreferences
    let schedule: PrayerSchedule
    @State private var selected = Date.now

    private var calendar: Calendar { PrayerSchedule.hijriCalendar(preferences) }
    private var corrected: Date {
        calendar.date(byAdding: .day, value: preferences.hijriOffset, to: selected) ?? selected
    }
    private var days: [Date] {
        guard let month = calendar.dateInterval(of: .month, for: corrected),
              let range = calendar.range(of: .day, in: .month, for: corrected) else { return [] }
        return range.compactMap { calendar.date(byAdding: .day, value: $0 - 1, to: month.start) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                monthGrid
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        arrow("phone_previous_day", delta: -1, unit: .day)
                        Spacer()
                        Text(preferences.hijriDate(selected)).font(.headline)
                        Spacer()
                        arrow("phone_next_day", delta: 1, unit: .day)
                    }
                    Text(preferences.gregorianDate(selected)).foregroundStyle(.secondary)
                    if let times = schedule.day(at: selected, prefs: preferences) {
                        PrayerTimesList(preferences: preferences, times: times)
                    } else {
                        Text(preferences.text("phone_unavailable"))
                    }
                }
                .simultaneousGesture(swipe(.day))
                monthEvents
            }
            .padding()
        }
        .navigationTitle(preferences.text("phone_calendar"))
        .toolbar {
            Button(preferences.text("phone_today")) { selected = .now }
        }
    }

    private var monthGrid: some View {
        let month = calendar.component(.month, from: corrected)
        let year = calendar.component(.year, from: corrected)
        let months = PhoneData.list("hijriMonths", arabic: preferences.arabic)
        let weekdays = calendar.veryShortStandaloneWeekdaySymbols
        let gap = days.first.map { (calendar.component(.weekday, from: $0) - calendar.firstWeekday + 7) % 7 } ?? 0
        return VStack(spacing: 8) {
            HStack {
                arrow("phone_previous_month", delta: -1, unit: .month)
                Spacer()
                Text(preferences.displayDigits("\(months[month - 1]) \(year)")).font(.headline)
                Spacer()
                arrow("phone_next_month", delta: 1, unit: .month)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 6) {
                ForEach(0..<7, id: \.self) { index in
                    Text(weekdays[(calendar.firstWeekday - 1 + index) % 7])
                        .font(.caption).foregroundStyle(.secondary)
                }
                ForEach(0..<gap, id: \.self) { _ in Color.clear.frame(height: 30) }
                ForEach(days, id: \.self) { date in
                    let day = calendar.component(.day, from: date)
                    let active = calendar.isDate(date, inSameDayAs: corrected)
                    let marked = !HijriObservance.events(month: month, day: day).isEmpty
                    Button {
                        selected = calendar.date(byAdding: .day, value: -preferences.hijriOffset, to: date) ?? date
                    } label: {
                        VStack(spacing: 2) {
                            Text(preferences.displayDigits(String(day))).font(.callout.monospacedDigit())
                            Circle().fill(marked ? Color.accentColor : .clear).frame(width: 4, height: 4)
                        }
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(active ? Color.accentColor.opacity(0.16) : .clear, in: .rect(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(active ? .isSelected : [])
                }
            }
        }
        .simultaneousGesture(swipe(.month))
    }

    private var monthEvents: some View {
        let month = calendar.component(.month, from: corrected)
        let entries = days.flatMap { date in
            let day = calendar.component(.day, from: date)
            return HijriObservance.events(month: month, day: day).map { (day, $0) }
        }
        return VStack(alignment: .leading, spacing: 8) {
            if !entries.isEmpty {
                Text(preferences.text("phone_calendar_events")).font(.headline)
                ForEach(entries.indices, id: \.self) { index in
                    let (day, event) = entries[index]
                    Text(preferences.displayDigits(String(day)) + " · " + event.name(day: day, preferences: preferences))
                        .font(.subheadline)
                }
            }
        }
    }

    private func arrow(_ label: String, delta: Int, unit: Calendar.Component) -> some View {
        Button { move(unit, delta) } label: {
            Image(systemName: delta < 0 ? "chevron.backward" : "chevron.forward").padding(10)
        }
        .accessibilityLabel(preferences.text(label))
    }

    private func swipe(_ unit: Calendar.Component) -> some Gesture {
        DragGesture(minimumDistance: 34).onEnded { value in
            let x = value.translation.width
            guard abs(x) > abs(value.translation.height) else { return }
            move(unit, (x < 0) != preferences.arabic ? 1 : -1)
        }
    }

    private func move(_ unit: Calendar.Component, _ amount: Int) {
        guard let moved = calendar.date(byAdding: unit, value: amount, to: corrected),
              let uncorrected = calendar.date(byAdding: .day, value: -preferences.hijriOffset, to: moved) else { return }
        selected = uncorrected
    }
}
