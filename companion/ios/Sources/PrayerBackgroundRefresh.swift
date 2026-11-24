import BackgroundTasks
import Foundation

@MainActor
enum PrayerBackgroundRefresh {
    private static let identifier = "tv.miqat.ios.prayer-refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { task in
            let work = Task { @MainActor in
                let store = PhoneStore.shared
                guard !Task.isCancelled, store.loadIfNeeded() else {
                    if !Task.isCancelled { schedule(enabled: true) }
                    return false
                }
                let refresh = PrayerNotifications.shared.refresh(preferences: store.preferences,
                                                                  schedule: PrayerSchedule.shared)
                return await withTaskCancellationHandler {
                    await refresh.value
                } onCancel: {
                    refresh.cancel()
                }
            }
            task.expirationHandler = { work.cancel() }
            Task { @MainActor in
                let success = await work.value
                task.setTaskCompleted(success: success && !work.isCancelled)
            }
        }
    }

    static func schedule(enabled: Bool) {
        let scheduler = BGTaskScheduler.shared
        if enabled {
            let request = BGAppRefreshTaskRequest(identifier: identifier)
            request.earliestBeginDate = Date.now.addingTimeInterval(12 * 60 * 60)
            try? scheduler.submit(request)
        } else {
            scheduler.cancel(taskRequestWithIdentifier: identifier)
        }
    }
}
