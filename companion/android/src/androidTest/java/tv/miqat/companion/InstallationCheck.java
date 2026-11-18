package tv.miqat.companion;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.util.Collections;

/** Native host navigation only: no TV connection, installation or package changes. */
final class InstallationCheck {
    static void run(Instrumentation test, String language) throws Exception {
        String host = test.getTargetContext().getPackageName();
        ActivityInfo info = test.getTargetContext().getPackageManager().getActivityInfo(
            new ComponentName(host, SetupActivity.class.getName()), 0);
        CompanionCheck.check(!info.exported, "Embedded installer must not be exported");
        File key = new File(test.getTargetContext().getFilesDir(), "adb.key");
        boolean hadKey = key.exists();
        MainActivity phone = (MainActivity)test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity installer = open(test, phone);
        CompanionCheck.check(language.equals(installer.getResources().getConfiguration().getLocales().get(0).getLanguage()),
            "Installer did not follow companion language");
        CompanionCheck.check(installer.getResources().getConfiguration().getLayoutDirection() ==
            (language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR), "Installer direction");
        CompanionCheck.check(find(installer.getWindow().getDecorView(), installer.getString(tv.miqat.installer.R.string.guide_title)) != null,
            "Native setup guide missing");
        View ready = find(installer.getWindow().getDecorView(), installer.getString(tv.miqat.installer.R.string.guide_ready));
        android.graphics.Rect visible = new android.graphics.Rect();
        CompanionCheck.check(ready != null && ready.getGlobalVisibleRect(visible) && visible.height() == ready.getHeight(),
            "Setup's primary action is clipped");
        if (((CompanionCheck)test).captures) {
            android.os.SystemClock.sleep(1000);
            android.graphics.Bitmap screenshot = test.getUiAutomation().takeScreenshot();
            CompanionCheck.check(screenshot != null, "Missing setup screenshot");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(
                    test.getTargetContext().getExternalFilesDir(null), language + "-setup.png"))) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            } finally { screenshot.recycle(); }
        }
        CompanionCheck.check(key.exists() == hadKey, "Opening setup must not create ADB credentials");
        test.runOnMainSync(installer::onBackPressed);
        test.waitForIdleSync();
        CompanionCheck.check(phone.page.equals("tvs"), "Cancelling setup must return to TVs without pairing");
        installer = open(test, phone);
        showDone(test, installer, false);
        Activity partial = installer;
        test.runOnMainSync(() -> {
            CompanionCheck.check(find(partial.getWindow().getDecorView(), partial.getString(tv.miqat.installer.R.string.pair_tv)) == null,
                "Incomplete setup must not claim success");
            find(partial.getWindow().getDecorView(), partial.getString(tv.miqat.installer.R.string.finish)).performClick();
        });
        test.waitForIdleSync();
        CompanionCheck.check(phone.page.equals("tvs"), "Incomplete setup must not open pairing");
        installer = open(test, phone);
        showDone(test, installer, true);
        Activity completed = installer;
        test.runOnMainSync(() -> {
            View pair = find(completed.getWindow().getDecorView(), completed.getString(tv.miqat.installer.R.string.pair_tv));
            CompanionCheck.check(pair != null, "Successful embedded setup must offer pairing");
            pair.performClick();
        });
        test.waitForIdleSync();
        // ActivityManager delivers results asynchronously after the UI queue becomes idle.
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        boolean[] pairedPage = {false};
        do {
            test.runOnMainSync(() -> pairedPage[0] = phone.page.equals("pair") && phone.hasWindowFocus());
            if (pairedPage[0]) break;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        CompanionCheck.check(pairedPage[0], "Completed setup must open ordinary pairing");
        CompanionCheck.check(phone.pairing == null, "Setup must not automatically approve a phone");
        test.runOnMainSync(phone::finish);
        test.waitForIdleSync();
    }

    private static void showDone(Instrumentation test, Activity installer, boolean success) {
        test.runOnMainSync(() -> {
            try {
                java.lang.reflect.Field ok = tv.miqat.installer.MainActivity.class.getDeclaredField("installedOk");
                ok.setAccessible(true);ok.setBoolean(installer, success);
                java.lang.reflect.Method done = tv.miqat.installer.MainActivity.class.getDeclaredMethod("showDone", java.util.List.class, boolean.class);
                done.setAccessible(true);done.invoke(installer, Collections.emptyList(), false);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    private static Activity open(Instrumentation test, MainActivity phone) {
        Instrumentation.ActivityMonitor monitor = test.addMonitor(SetupActivity.class.getName(), null, false);
        try {
            test.runOnMainSync(() -> {
                View setup = phone.getWindow().getDecorView().findViewWithTag("install.open");
                CompanionCheck.check(setup != null, "Setup entry missing");setup.performClick();
            });
            Activity installer = test.waitForMonitorWithTimeout(monitor, 5000);
            CompanionCheck.check(installer != null, "Setup did not launch inside companion");
            test.waitForIdleSync();return installer;
        } finally { test.removeMonitor(monitor); }
    }

    private static View find(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);if (found != null) return found;
            }
        }
        return null;
    }
}
