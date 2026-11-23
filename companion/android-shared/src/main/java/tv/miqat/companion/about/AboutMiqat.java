package tv.miqat.companion.about;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

public final class AboutMiqat {
    public static final String FEEDBACK_URL =
            "https://github.com/AbdelrahmanHajjaj/MiqatTV/issues/new?template=feedback.yml";

    private AboutMiqat() {}

    public static String version(Context context) {
        return packageInfo(context).versionName;
    }

    @SuppressWarnings("deprecation")
    static PackageInfo packageInfo(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException("Miqat package metadata is missing", impossible);
        }
    }
}
