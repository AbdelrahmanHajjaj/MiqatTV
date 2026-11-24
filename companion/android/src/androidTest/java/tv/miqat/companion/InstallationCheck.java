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
        android.content.SharedPreferences prefs = PhoneStore.preferences(test.getTargetContext());
        String original = prefs.getString("settings", null);
        try {
            PhoneStore.read(test.getTargetContext())
                    .changed(Collections.singletonMap("appearance.theme", "dusk"))
                    .save(test.getTargetContext());
            String host = test.getTargetContext().getPackageName();
            ActivityInfo info =
                    test.getTargetContext()
                            .getPackageManager()
                            .getActivityInfo(
                                    new ComponentName(host, SetupActivity.class.getName()), 0);
            CompanionCheck.check(!info.exported, "Embedded installer must not be exported");
            File key = new File(test.getTargetContext().getFilesDir(), "adb.key");
            boolean hadKey = key.exists();
            MainActivity phone = LiveUiCheck.launch(test);
            test.runOnMainSync(() -> phone.tab("tvs"));
            test.waitForIdleSync();
            Activity installer = open(test, phone);
            CompanionCheck.check(
                    language.equals(
                            installer
                                    .getResources()
                                    .getConfiguration()
                                    .getLocales()
                                    .get(0)
                                    .getLanguage()),
                    "Installer did not follow companion language");
            CompanionCheck.check(
                    installer.getResources().getConfiguration().getLayoutDirection()
                            == (language.equals("ar")
                                    ? View.LAYOUT_DIRECTION_RTL
                                    : View.LAYOUT_DIRECTION_LTR),
                    "Installer direction");
            CompanionCheck.check(
                    find(
                                            installer.getWindow().getDecorView(),
                                            installer.getString(
                                                    tv.miqat.installer.R.string.find_title))
                                    != null
                            || find(
                                            installer.getWindow().getDecorView(),
                                            installer.getString(
                                                    tv.miqat.installer.R.string.not_wifi_title))
                                    != null,
                    "Prepared TV must continue into native connection setup");
            if (((CompanionCheck) test).captures) {
                android.os.SystemClock.sleep(1000);
                android.graphics.Bitmap screenshot = test.getUiAutomation().takeScreenshot();
                CompanionCheck.check(screenshot != null, "Missing setup screenshot");
                try (java.io.FileOutputStream out =
                        new java.io.FileOutputStream(
                                new File(
                                        test.getTargetContext().getExternalFilesDir(null),
                                        language + "-setup.png"))) {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                } finally {
                    screenshot.recycle();
                }
            }
            int ground =
                    android.graphics.Color.parseColor(
                            phone.catalog
                                    .data
                                    .getJSONObject("themes")
                                    .getJSONObject("dusk")
                                    .getJSONObject("colors")
                                    .getString("lapis-900"));
            ViewGroup installedContent = installer.findViewById(android.R.id.content);
            CompanionCheck.check(
                    ((android.graphics.drawable.ColorDrawable)
                                            installedContent.getChildAt(0).getBackground())
                                    .getColor()
                            == ground,
                    "Embedded installer must keep the phone's selected palette");
            CompanionCheck.check(
                    key.exists() == hadKey, "Opening setup must not create ADB credentials");
            test.runOnMainSync(installer::onBackPressed);
            Activity cancelled = installer;
            await(
                    test,
                    () -> cancelled.isDestroyed() && phone.hasWindowFocus(),
                    "Cancelled setup did not return to its caller");
            CompanionCheck.check(
                    phone.page.equals("tv-setup"),
                    "Cancelling setup must return to source choice without pairing");
            // Cancelling Advanced's picker must return to source selection, never a public
            // download.
            Instrumentation.ActivityMonitor picker =
                    test.addMonitor(
                            new android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT),
                            new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
                            true);
            try {
                test.runOnMainSync(() ->
                        phone.startActivityForResult(
                                new Intent(phone, SetupActivity.class)
                                        .putExtra("prepared", true)
                                        .putExtra("choose-local", true),
                                81));
                await(
                        test,
                        () -> picker.getHits() > 0 && phone.hasWindowFocus(),
                        "Cancelled local package picker did not return to source selection");
                CompanionCheck.check(phone.page.equals("tv-setup"), "Local Cancel started pairing");
            } finally {
                test.removeMonitor(picker);
            }
            installer = open(test, phone);
            showDone(test, installer, false);
            Activity partial = installer;
            test.runOnMainSync(() -> {
                CompanionCheck.check(
                        find(
                                        partial.getWindow().getDecorView(),
                                        partial.getString(tv.miqat.installer.R.string.pair_tv))
                                == null,
                        "Incomplete setup must not claim success");
                find(
                                partial.getWindow().getDecorView(),
                                partial.getString(tv.miqat.installer.R.string.finish))
                        .performClick();
            });
            await(
                    test,
                    () -> partial.isDestroyed() && phone.hasWindowFocus(),
                    "Incomplete setup did not return to its caller");
            CompanionCheck.check(
                    phone.page.equals("tv-setup"), "Incomplete setup must not open pairing");
            installer = open(test, phone);
            showDone(test, installer, true);
            Activity completed = installer;
            test.runOnMainSync(() -> {
                View pair =
                        find(
                                completed.getWindow().getDecorView(),
                                completed.getString(tv.miqat.installer.R.string.pair_tv));
                CompanionCheck.check(pair != null, "Successful embedded setup must offer pairing");
                pair.performClick();
            });
            await(
                    test,
                    () ->
                            completed.isDestroyed()
                                    && phone.page.equals("pair")
                                    && phone.hasWindowFocus(),
                    "Completed setup must open ordinary pairing");
            CompanionCheck.check(
                    phone.pairingUi.pairing == null,
                    "Setup must not automatically approve a phone");
            test.runOnMainSync(phone::finish);
            test.waitForIdleSync();
        } finally {
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (original == null) restore.remove("settings");
            else restore.putString("settings", original);
            CompanionCheck.check(restore.commit(), "Cannot restore installer palette fixture");
        }
    }

    private static void showDone(Instrumentation test, Activity installer, boolean success) {
        test.runOnMainSync(() -> {
            try {
                java.lang.reflect.Field ok =
                        tv.miqat.installer.MainActivity.class.getDeclaredField("installedOk");
                ok.setAccessible(true);
                ok.setBoolean(installer, success);
                java.lang.reflect.Method done =
                        tv.miqat.installer.MainActivity.class.getDeclaredMethod(
                                "showDone", java.util.List.class, boolean.class);
                done.setAccessible(true);
                done.invoke(installer, Collections.emptyList(), false);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
    }

    private static Activity open(Instrumentation test, MainActivity phone) {
        await(test, phone::hasWindowFocus, "Caller not resumed before opening setup");
        Instrumentation.ActivityMonitor monitor =
                test.addMonitor(SetupActivity.class.getName(), null, false);
        try {
            test.runOnMainSync(() -> {
                phone.tvSetup.open();
                phone.body.findViewWithTag("setup.platform.android").performClick();
                CompanionCheck.check(
                        phone.body.findViewWithTag("setup.channel.production").isSelected(),
                        "Production must be the default");
                phone.getWindow().getDecorView().findViewWithTag("setup.continue").performClick();
            });
            Activity installer = test.waitForMonitorWithTimeout(monitor, 5000);
            CompanionCheck.check(installer != null, "Setup did not launch inside companion");
            test.waitForIdleSync();
            return installer;
        } finally {
            test.removeMonitor(monitor);
        }
    }

    private static void await(
            Instrumentation test, java.util.function.BooleanSupplier condition, String message) {
        // An idle UI queue can precede ActivityManager's finish/result/resume callbacks.
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        boolean[] ready = {false};
        do {
            test.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        CompanionCheck.check(false, message);
    }

    private static View find(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
