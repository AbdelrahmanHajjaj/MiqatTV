package tv.miqat.companion.about;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.view.View;

/** One automatic presentation per installed upgrade. The About action is always available. */
public final class ReleaseNotes {
    private ReleaseNotes() {}

    public static AlertDialog showAfterUpdate(AlertDialog.Builder builder, boolean rtl, String title,
                                             String notes, String close) {
        Context context = builder.getContext();
        SharedPreferences prefs = context.getSharedPreferences("miqat-release-notes", Context.MODE_PRIVATE);
        return claim(prefs, AboutMiqat.packageInfo(context)) ? show(builder, rtl, title, notes, close) : null;
    }

    static synchronized boolean claim(SharedPreferences prefs, PackageInfo installed) {
        long current = AboutUpdates.code(installed);
        long previous = prefs.getLong("version", -1);
        if (current <= previous || !prefs.edit().putLong("version", current).commit()) return false;
        return previous >= 0 || installed.lastUpdateTime > installed.firstInstallTime;
    }

    public static AlertDialog show(AlertDialog.Builder builder, boolean rtl, String title,
                                  String notes, String close) {
        String installed = "\u2066Miqat " + AboutMiqat.version(builder.getContext()) + "\u2069";
        AlertDialog dialog = builder.setTitle(title).setMessage(installed + "\n\n" + notes)
                .setPositiveButton(close, null).show();
        dialog.getWindow().getDecorView().setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        return dialog;
    }
}
