package tv.miqat.companion;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

/** A persisted periodic job owns one bounded location request, even without an Activity. */
public final class PhoneLocationJob extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private PhoneLocation locator;
    private JobParameters current;
    private long requestGeneration;

    @Override public boolean onStartJob(JobParameters parameters) {
        if (!PhoneBackgroundLocation.enabled(this)) {
            PhoneBackgroundLocation.sync(this);
            return false;
        }
        current = parameters;
        requestGeneration = PhoneBackgroundLocation.generation(this);
        PhoneBackgroundLocation.started(this);
        // Defer cached-fix callbacks until onStartJob has returned its asynchronous result.
        main.post(() -> request(parameters));
        return true;
    }

    private void request(JobParameters parameters) {
        if (current != parameters) return;
        try {
            PhoneStore requested = PhoneStore.read(this);
            long generation = requestGeneration;
            locator = new PhoneLocation(getApplicationContext());
            locator.request(fix -> {
                if (current != parameters) return;
                try {
                    PhoneBackgroundLocation.accept(this, requested, generation, fix);
                } catch (Exception failure) {
                    PhoneStore.preferences(this).edit().putString("background-location-error", "phone_save_failed").apply();
                }
                finishRequest();
            }, failure -> {
                if (current != parameters) return;
                PhoneStore.preferences(this).edit().putString("background-location-error", failure).apply();
                finishRequest();
            });
        } catch (Exception failure) {
            PhoneStore.preferences(this).edit().putString("background-location-error", "phone_settings_failed").apply();
            finishRequest();
        }
    }

    private void finishRequest() {
        JobParameters completed = current;
        stopRequest();
        if (completed != null) jobFinished(completed, false);
    }

    void cancelCurrent(long invalidated) {
        if (requestGeneration == invalidated) finishRequest();
    }

    private void stopRequest() {
        current = null;
        main.removeCallbacksAndMessages(null);
        if (locator != null) locator.close();
        locator = null;
        PhoneBackgroundLocation.stopped(this);
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        stopRequest();
        return false;
    }

    @Override public void onDestroy() {
        stopRequest();
        super.onDestroy();
    }
}
