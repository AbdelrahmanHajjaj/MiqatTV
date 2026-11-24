import Foundation

func day(_ timestamp: String) -> Date {
    ISO8601DateFormatter().date(from: timestamp)!
}

func calendars(_ zone: String) -> (civil: Calendar, hijri: Calendar) {
    var civil = Calendar(identifier: .gregorian)
    var hijri = Calendar(identifier: .islamicUmmAlQura)
    civil.timeZone = TimeZone(identifier: zone)!
    hijri.timeZone = civil.timeZone
    return (civil, hijri)
}

let riyadh = calendars("Asia/Riyadh")
let ramadan = day("2025-03-01T09:00:00Z")
var selection = HijriCalendarSelection(opened: ramadan, civil: riyadh.civil, hijri: riyadh.hijri, correction: 0)
precondition(selection.days.count == 29 && selection.days.first == ramadan)
let eid = day("2025-03-30T09:00:00Z")
selection.moveMonth(1)
precondition(selection.selected == ramadan && selection.days.first == eid)
selection.select(eid)
precondition(selection.selected == eid && selection.days.first == eid)
selection.select(selection.nextDay(-1)!, followMonth: true)
precondition(selection.days.first == ramadan)

let lastDay = day("2025-06-25T09:00:00Z")
selection.select(lastDay, followMonth: true)
selection.moveMonth(1)
let year = riyadh.hijri.dateComponents([.year, .month, .day], from: selection.days.first!)
precondition(year.year == 1447 && year.month == 1 && year.day == 1)
precondition(selection.selected == lastDay)
let arafah = riyadh.hijri.dateComponents([.month, .day], from: day("2025-06-05T09:00:00Z"))
precondition(arafah.month == 12 && arafah.day == 9)

let ny = calendars("America/New_York")
let beforeDst = day("2026-03-07T17:00:00Z")
let afterDst = day("2026-03-09T16:00:00Z")
var corrected = HijriCalendarSelection(opened: beforeDst, civil: ny.civil, hijri: ny.hijri, correction: 2)
precondition(corrected.selectedHijri == afterDst && corrected.civilDay(afterDst) == beforeDst)
corrected.select(corrected.nextDay(366)!, followMonth: true)
precondition(corrected.nextDay(1) == nil && corrected.nextDay(-1) != nil)
corrected.select(beforeDst, followMonth: true)
corrected.select(corrected.nextDay(-366)!, followMonth: true)
precondition(corrected.nextDay(-1) == nil && corrected.nextDay(1) != nil)
print("Hijri month browsing, selected prayer day, year rollover, correction and civil limits passed.")
