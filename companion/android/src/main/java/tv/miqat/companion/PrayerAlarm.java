package tv.miqat.companion;

import android.content.*;

import tv.miqat.core.ReminderEvent;

/**
 * Private, direct-boot-aware entry point. Boot/time changes only reschedule; they never play audio.
 */
public final class PrayerAlarm extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (PhoneAlertTest.FIRE.equals(intent.getAction())) {
            PhoneAlertTest.fire(context, intent.getStringExtra("event"));
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) PhoneAlertTest.cancel(context);
        if (PhoneAlarms.FIRE.equals(intent.getAction())) {
            ReminderEvent event = null;
            try {
                event =
                        PhoneAlarms.claim(
                                context,
                                intent.getStringExtra("event"),
                                System.currentTimeMillis());
                if (event != null) {
                    PhoneStore settings = PhoneStore.read(context);
                    if (event.getKind().equals("call")
                            && settings.eventMode(event) == 2
                            && PhoneAlarms.channel(
                                    context,
                                    PhoneAlarms.playbackChannel(
                                            context,
                                            settings.value("reminders.full-screen")
                                                    .equals("true"))))
                        context.startForegroundService(
                                PhonePlayback.intent(
                                                context,
                                                event.getPrayer(),
                                                settings.voice(event.getPrayer()),
                                                false,
                                                event.getId())
                                        .putExtra("prayerTime", event.getPrayerTime()));
                    else PhoneAlarms.notify(context, event, false);
                }
            } catch (Exception failure) {
                if (event != null) PhoneAlarms.notify(context, event, true);
            }
        }
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(
                        () -> {
                            try {
                                if (!PhoneAlarms.FIRE.equals(intent.getAction()))
                                    PhoneBackgroundLocation.sync(app);
                                PhoneAlarms.rebuild(app);
                            } finally {
                                pending.finish();
                            }
                        },
                        "miqat-phone-alarms")
                .start();
    }
}
