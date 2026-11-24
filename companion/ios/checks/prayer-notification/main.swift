import Foundation
import UserNotifications

let time = (Date.now.timeIntervalSince1970.rounded(.up) + 3600) * 1000
let id = "miqat.schedule.phone:call:2:\(Int64(time))"
let adhan = PrayerNotificationRequest(id: id, time: time, title: "Dhuhr", body: "12:00", audible: true, adhanPrayer: 2)
let pending = adhan.makeRequest()
precondition(adhan.matches(pending))
let trigger = pending.trigger as! UNCalendarNotificationTrigger
precondition(trigger.dateComponents.timeZone?.secondsFromGMT() == 0)
precondition(trigger.nextTriggerDate() == adhan.date)

let changed = PrayerNotificationRequest(id: id, time: time, title: "الظهر", body: "12:00", audible: false, adhanPrayer: nil)
precondition(!changed.matches(pending))
precondition(changed.matches(changed.makeRequest()))
let shifted = PrayerNotificationRequest(id: id, time: time + 60000, title: "Dhuhr", body: "12:00", audible: true, adhanPrayer: 2)
precondition(!shifted.matches(pending))
let old = UNNotificationRequest(identifier: id, content: pending.content,
                                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3600, repeats: false))
precondition(!adhan.matches(old))
print("Identical absolute alerts are retained, and changed or older relative requests are replaced.")
