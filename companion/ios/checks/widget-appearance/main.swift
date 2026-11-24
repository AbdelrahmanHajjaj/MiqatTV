import Foundation

let oldFile = """
{"days":[],"names":["Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"],"arabic":false,
"eastern":false,"clock":"12","timezone":"UTC","colors":{"lapis-900":"#112233","gold-400":"#778899"}}
"""
var snapshot = try JSONDecoder().decode(WidgetSnapshot.self, from: Data(oldFile.utf8))
precondition(snapshot.palettes == nil)
precondition(snapshot.colors(for: "app") == snapshot.colors)
snapshot.palettes = [.init(id: "sand", name: "Sand", colors: ["lapis-900": "#FFEEDD"])]
let restored = try JSONDecoder().decode(WidgetSnapshot.self, from: JSONEncoder().encode(snapshot))
precondition(restored.colors(for: "sand") == ["lapis-900": "#FFEEDD", "gold-400": "#778899"])
precondition(restored.colors(for: "app") == snapshot.colors)
precondition(restored.colors(for: "retired") == snapshot.colors)
precondition(WidgetSnapshot.opacity(percent: -1) == 0)
precondition(WidgetSnapshot.opacity(percent: 41) == 0.41)
precondition(WidgetSnapshot.opacity(percent: 200) == 1)
precondition(restored.sceneID(for: "app") == nil)
snapshot.scenes = [.init(id: "tea_hills", name: "Tea hills"), .init(id: "miqat_quiet", name: "Miqat Quiet")]
snapshot.phoneScene = "tea_hills"
let withScenes = try JSONDecoder().decode(WidgetSnapshot.self, from: JSONEncoder().encode(snapshot))
precondition(withScenes.sceneID(for: nil) == nil)
precondition(withScenes.sceneID(for: "plain") == nil)
precondition(withScenes.sceneID(for: "app") == "tea_hills")
precondition(withScenes.sceneID(for: "miqat_quiet") == "miqat_quiet")
precondition(withScenes.sceneID(for: "retired_scene") == nil)
snapshot.phoneScene = "plain"
precondition(snapshot.sceneID(for: "app") == nil)
print("Widget palette and scene selection, old snapshot compatibility and opacity bounds passed.")

let first = ISO8601DateFormatter().date(from: "2026-03-07T00:00:00Z")!
let tomorrow = first.addingTimeInterval(86400)
let unavailable = tomorrow.addingTimeInterval(86400)
let hours: [TimeInterval] = [5, 6, 12, 15, 18, 20]
func prayerDay(_ start: Date, nextFajr: Date? = nil) -> WidgetSnapshot.Day {
    let times = hours.map { start.addingTimeInterval($0 * 3600) }
    var transitions = [
        WidgetSnapshot.Transition(start: start, nextIndex: 0, nextTime: times[0]),
        .init(start: times[0], nextIndex: 2, nextTime: times[2]),
    ]
    if let nextFajr { transitions.append(.init(start: times[5], nextIndex: 0, nextTime: nextFajr)) }
    return .init(start: start, times: times, hijri: "", gregorian: "", transitions: transitions)
}
let firstTimes = prayerDay(first).times!
let tomorrowTimes = prayerDay(tomorrow).times!
var widget = WidgetSnapshot(days: [prayerDay(first, nextFajr: tomorrowTimes[0]), prayerDay(tomorrow),
    .init(start: unavailable, times: nil, hijri: "", gregorian: ""), prayerDay(unavailable.addingTimeInterval(86400))],
    names: snapshot.names, arabic: false, eastern: true, clock: "24", timezone: "UTC", colors: snapshot.colors)
precondition(widget.nextPrayer(at: first) == nil)
widget.locationConfirmed = true
let afterFajr = firstTimes[0]
let dhuhr = widget.nextPrayer(at: afterFajr)!
precondition(dhuhr.index == 2 && dhuhr.time == firstTimes[2])
precondition(widget.previousPrayer(at: afterFajr, next: dhuhr) == firstTimes[0])
let isha = firstTimes[5]
let fajr = widget.nextPrayer(at: isha)!
precondition(fajr.index == 0 && fajr.time == tomorrowTimes[0])
precondition(widget.previousPrayer(at: isha, next: fajr) == isha)
precondition(widget.previousPrayer(at: tomorrow, next: fajr) == isha)
precondition(widget.nextPrayer(at: tomorrowTimes[5]) == nil)
precondition(widget.day(at: unavailable)?.times == nil && widget.nextPrayer(at: unavailable) == nil)
precondition(widget.day(at: first.addingTimeInterval(5 * 86400)) == nil)
print("Widget rejects unconfirmed, unavailable and stale days and rolls from Isha to the next day's Fajr.")
