import Foundation
import UserNotifications

struct PrayerNotificationRequest {
    let id: String
    let time: Double
    let title: String
    let body: String
    let audible: Bool
    let adhanPrayer: Int?

    static let shortSound = UNNotificationSound(named: UNNotificationSoundName(rawValue: "miqat_short_tone.wav"))

    var date: Date { Date(timeIntervalSince1970: time / 1000) }

    static func localTiming(prayerTime: Double, eventTime: Double, calendar: Calendar) -> (weekday: Int, minute: Int) {
        let prayer = Date(timeIntervalSince1970: prayerTime / 1000)
        let event = Date(timeIntervalSince1970: eventTime / 1000)
        return (calendar.component(.weekday, from: prayer) - 1,
                calendar.component(.hour, from: event) * 60 + calendar.component(.minute, from: event))
    }

    func matches(_ pending: UNNotificationRequest) -> Bool {
        guard pending.identifier == id, let trigger = pending.trigger as? UNCalendarNotificationTrigger,
              !trigger.repeats, trigger.nextTriggerDate() == date else { return false }
        let content = pending.content
        return content.title == title && content.body == body && (content.sound != nil) == audible
            && content.userInfo["adhanPrayer"] as? Int == adhanPrayer
            && content.userInfo["adhanTime"] as? Double == (adhanPrayer == nil ? nil : time)
    }

    func makeRequest() -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = audible ? Self.shortSound : nil
        if let adhanPrayer {
            content.userInfo["adhanPrayer"] = adhanPrayer
            content.userInfo["adhanTime"] = time
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.calendar, .timeZone, .year, .month, .day, .hour, .minute, .second], from: date)
        return UNNotificationRequest(identifier: id, content: content,
                                     trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
    }
}
