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

    static func upcoming(from date: Date, preferences: PhonePreferences) -> (date: Date, label: String)? {
        let civil = PrayerSchedule.civilCalendar(preferences)
        let hijri = PrayerSchedule.hijriCalendar(preferences)
        for offset in 0...3 {
            guard let observed = civil.prayerDayStart(adding: offset, to: date),
                  let noon = civil.date(bySettingHour: 12, minute: 0, second: 0, of: observed),
                  let corrected = civil.date(byAdding: .day, value: preferences.hijriOffset, to: noon) else { continue }
            let parts = hijri.dateComponents([.month, .day], from: corrected)
            guard let month = parts.month, let day = parts.day else { continue }
            let names = events(month: month, day: day).map { $0.name(day: day, preferences: preferences) }
            guard !names.isEmpty else { continue }
            let timing: String
            switch offset {
            case 0: timing = preferences.text("phone_today")
            case 1: timing = preferences.text("phone_tomorrow")
            default:
                timing = preferences.text("phone_event_in_days")
                    .replacingOccurrences(of: "%s", with: preferences.displayDigits(String(offset)))
            }
            return (observed, (names + [timing]).joined(separator: " · "))
        }
        return nil
    }
}

struct HijriMonth: View {
    let preferences: PhonePreferences
    let schedule: PrayerSchedule
    @Environment(\.dismiss) private var dismiss
    @State private var selection: HijriCalendarSelection

    init(preferences: PhonePreferences, schedule: PrayerSchedule, selectedDay: Date? = nil) {
        self.preferences = preferences
        self.schedule = schedule
        _selection = State(initialValue: HijriCalendarSelection(opened: .now, civil: PrayerSchedule.civilCalendar(preferences),
                           hijri: PrayerSchedule.hijriCalendar(preferences), correction: preferences.hijriOffset,
                           selectedDay: selectedDay))
    }

    var body: some View {
        let days = selection.days
        let selected = selection.selected
        let calendar = selection.hijri
        let month = calendar.component(.month, from: selection.month)
        let parts = calendar.dateComponents([.month, .day], from: selection.selectedHijri)
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                monthGrid(days, month: month)
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        arrow("phone_previous_day", delta: -1, unit: .day)
                        Spacer()
                        Text(preferences.hijriDate(selected)).font(.headline)
                        Spacer()
                        arrow("phone_next_day", delta: 1, unit: .day)
                    }
                    Text(preferences.gregorianDate(selected)).foregroundStyle(.secondary)
                    if let month = parts.month, let day = parts.day {
                        ForEach(HijriObservance.events(month: month, day: day)) { event in
                            Text(event.name(day: day, preferences: preferences))
                                .font(.subheadline).foregroundStyle(Color.accentColor)
                        }
                    }
                    if let times = schedule.day(at: selected, prefs: preferences) {
                        PrayerTimesList(preferences: preferences, times: times)
                    } else {
                        Text(preferences.text("phone_unavailable"))
                    }
                }
                .simultaneousGesture(swipe(.day))
                monthEvents(days, month: month)
            }
            .padding()
        }
        .navigationTitle(preferences.text("phone_calendar"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button(preferences.text("phone_today")) { selection.select(.now, followMonth: true) }
                Button(preferences.text("done")) { dismiss() }
            }
        }
    }

    private func monthGrid(_ days: [Date], month: Int) -> some View {
        let calendar = selection.hijri
        let year = calendar.component(.year, from: selection.month)
        let months = PhoneData.list("hijriMonths", arabic: preferences.arabic)
        let weekdays = calendar.shortStandaloneWeekdaySymbols
        let weekStart = preferences.arabic ? 7 : 1
        let gap = days.first.map { (calendar.component(.weekday, from: $0) - weekStart + 7) % 7 } ?? 0
        return VStack(spacing: 8) {
            HStack {
                arrow("phone_previous_month", delta: -1, unit: .month)
                Spacer()
                if months.indices.contains(month - 1) {
                    Text(preferences.displayDigits("\(months[month - 1]) \(year)")).font(.headline)
                }
                Spacer()
                arrow("phone_next_month", delta: 1, unit: .month)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 6) {
                ForEach(0..<7, id: \.self) { index in
                    Text(weekdays[(weekStart - 1 + index) % 7])
                        .font(.caption).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.6)
                }
                ForEach((-gap)..<0, id: \.self) { _ in Color.clear.frame(height: 30) }
                ForEach(days, id: \.self) { date in
                    let day = calendar.component(.day, from: date)
                    let civilDay = selection.civilDay(date)
                    let active = selection.civil.isDate(civilDay, inSameDayAs: selection.selected)
                    let events = HijriObservance.events(month: month, day: day)
                    Button {
                        selection.select(civilDay)
                    } label: {
                        VStack(spacing: 2) {
                            Text(preferences.displayDigits(String(day))).font(.callout.monospacedDigit())
                            Circle().fill(events.isEmpty ? .clear : Color.accentColor).frame(width: 4, height: 4)
                        }
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .background(active ? Color.accentColor.opacity(0.16) : .clear, in: .rect(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .disabled(!selection.canSelect(civilDay))
                    .accessibilityLabel(([preferences.hijriDate(civilDay)] +
                        events.map { $0.name(day: day, preferences: preferences) }).joined(separator: ", "))
                    .accessibilityAddTraits(active ? .isSelected : [])
                }
            }
        }
        .simultaneousGesture(swipe(.month))
    }

    private func monthEvents(_ days: [Date], month: Int) -> some View {
        let entries = days.compactMap { date -> (date: Date, day: Int, labels: [String])? in
            let civilDay = selection.civilDay(date)
            guard selection.canSelect(civilDay) else { return nil }
            let day = selection.hijri.component(.day, from: date)
            let labels = HijriObservance.events(month: month, day: day).map { $0.name(day: day, preferences: preferences) }
            return labels.isEmpty ? nil : (civilDay, day, labels)
        }
        return VStack(alignment: .leading, spacing: 8) {
            if !entries.isEmpty {
                Text(preferences.text("phone_calendar_events")).font(.headline)
                ForEach(entries.indices, id: \.self) { index in
                    let entry = entries[index]
                    Button {
                        selection.select(entry.date)
                    } label: {
                        Text(preferences.displayDigits(String(entry.day)) + " · " + entry.labels.joined(separator: ", "))
                            .font(.subheadline)
                    }
                }
            }
        }
    }

    private func arrow(_ label: String, delta: Int, unit: Calendar.Component) -> some View {
        Button { move(unit, delta) } label: {
            Image(systemName: delta < 0 ? "chevron.backward" : "chevron.forward").padding(10)
        }
        .accessibilityLabel(preferences.text(label))
        .disabled(unit == .month ? selection.nextMonth(delta) == nil : selection.nextDay(delta) == nil)
    }

    private func swipe(_ unit: Calendar.Component) -> some Gesture {
        DragGesture(minimumDistance: 34).onEnded { value in
            let x = value.translation.width
            guard abs(x) > 1.5 * abs(value.translation.height) else { return }
            move(unit, (x < 0) != preferences.arabic ? 1 : -1)
        }
    }

    private func move(_ unit: Calendar.Component, _ amount: Int) {
        if unit == .month { selection.moveMonth(amount) }
        else if let day = selection.nextDay(amount) { selection.select(day, followMonth: true) }
    }
}
