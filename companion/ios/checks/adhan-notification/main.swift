import UserNotifications

let content = UNMutableNotificationContent()
content.userInfo = ["adhanPrayer": 2, "adhanTime": 1600000000000.0]
let request = UNNotificationRequest(identifier: "miqat.test", content: content, trigger: nil)
let first = AdhanNotification(request)
precondition(first?.prayer == 2 && first?.time == 1600000000000)
precondition(first?.claimID == AdhanNotification(request)?.claimID)

content.userInfo["adhanTime"] = 1600000030000.0
let later = UNNotificationRequest(identifier: request.identifier, content: content, trigger: nil)
precondition(first?.claimID != AdhanNotification(later)?.claimID)

content.userInfo["adhanPrayer"] = 1
precondition(AdhanNotification(UNNotificationRequest(identifier: request.identifier, content: content, trigger: nil)) == nil)
content.userInfo = ["adhanPrayer": 2]
precondition(AdhanNotification(UNNotificationRequest(identifier: request.identifier, content: content, trigger: nil)) == nil)
print("Adhan notifications have stable claims, separate test attempts, and validated payloads.")
