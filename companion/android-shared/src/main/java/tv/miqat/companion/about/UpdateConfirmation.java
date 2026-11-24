package tv.miqat.companion.about;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** Opens Android's installer confirmation when the user commits an update. */
public final class UpdateConfirmation extends Activity {
    static final String FAILURE_TEXT = "tv.miqat.update.failure_text";

    @Override @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent result = getIntent();
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = Build.VERSION.SDK_INT >= 33 ? result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                    : result.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    startActivity(confirm);
                    finish();
                    return;
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    // The device has no usable confirmation activity.
                }
            }
            int id = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            if (id != -1) getPackageManager().getPackageInstaller().abandonSession(id);
        }
        String message = result.getStringExtra(FAILURE_TEXT);
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED && message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
