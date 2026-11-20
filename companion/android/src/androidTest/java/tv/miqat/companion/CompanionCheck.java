package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.miqat.companion.core.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/** Run only on an explicitly selected owned phone emulator; no TV/device integration. */
public final class CompanionCheck extends Instrumentation {
    String expectedOrientation = "";
    boolean journey,
            customCheck,
            releaseCheck,
            lan,
            live,
            camera,
            lg,
            captures,
            lgInstall,
            phoneRuntime,
            phoneUi,
            phoneDenied,
            phoneSize,
            phoneLocation,
            locationDenied,
            locationApprox;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        expectedOrientation = arguments == null ? "" : arguments.getString("orientation", "");
        captures = arguments != null && "true".equals(arguments.getString("capture", "false"));
        String suite = arguments == null ? "" : arguments.getString("suite", "");
        journey = suite.equals("phone-journey");
        customCheck = suite.equals("phone-custom");
        releaseCheck = suite.equals("public-release");
        locationApprox = suite.equals("phone-location-approx");
        phoneLocation = suite.equals("phone-location");
        locationDenied = suite.equals("phone-location-denied");
        phoneSize = suite.equals("phone-size");
        phoneDenied = suite.equals("phone-denied");
        phoneUi = suite.equals("phone-ui");
        phoneRuntime = suite.equals("phone-runtime");
        lgInstall = suite.equals("lg-install");
        lan = suite.equals("lan");
        lg = suite.equals("live-lg");
        live = suite.equals("live") || lg;
        camera = suite.equals("camera");
        start();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int count = 0;
        try {
            if (journey) {
                PhoneJourneyCheck.run(this, captures);
                result.putString(
                        "stream",
                        "PASS: bilingual fresh-use, resumable setup, draft preview, widget and TV"
                                + " platform/source journeys\n");
                finish(-1, result);
                return;
            }
            if (customCheck) {
                PhoneCustomCheck.run(this);
                result.putString(
                        "stream",
                        "PASS: v1 migration, per-prayer audio, weekday/quiet scheduling, private"
                                + " custom import and failed-copy preservation\n");
                finish(-1, result);
                return;
            }
            if (releaseCheck) {
                tv.miqat.installer.PublicReleaseCheck.run(getTargetContext());
                result.putString(
                        "stream",
                        "PASS: public release selection, asset source/checksum bounds and atomic"
                                + " download preservation\n");
                finish(-1, result);
                return;
            }
            if (locationDenied) {
                PhoneLocationCheck.denied(this);
                result.putString(
                        "stream", "PASS: denied foreground location leaves detection off\n");
                finish(-1, result);
                return;
            }
            if (phoneLocation || locationApprox) {
                PhoneLocationCheck.run(this, captures, locationApprox);
                result.putString(
                        "stream",
                        "PASS: bilingual one-shot location detection, approximate/stale quality,"
                                + " confirmation/persistence and Advanced coordinate fallback\n");
                finish(-1, result);
                return;
            }
            if (phoneSize) {
                PhoneSizeCheck.run(this, captures, expectedOrientation);
                result.putString(
                        "stream",
                        "PASS: bilingual phone sizing 80-150, navigation geometry and digit"
                                + " selection\n");
                finish(-1, result);
                return;
            }
            if (phoneDenied) {
                PhoneRuntimeCheck.denied(this);
                result.putString(
                        "stream",
                        "PASS: denied prayer permissions keep alarms and playback off; components"
                                + " private and direct-boot aware\n");
                finish(-1, result);
                return;
            }
            if (phoneUi) {
                PhoneUiCheck.run(this, captures);
                result.putString(
                        "stream",
                        "PASS: independent bilingual phone prayer UI, location confirmation,"
                                + " numeric Save/Cancel, per-prayer persistence and themes\n");
                finish(-1, result);
                return;
            }
            if (phoneRuntime) {
                PhoneRuntimeCheck.run(this);
                result.putString(
                        "stream",
                        "PASS: phone actual alarm delivery, private custom 30-second cutoff,"
                            + " silent/vibration channels, foreground audio/notification,"
                            + " duplicate/early/expired rejection, Stop, audio focus interruption"
                            + " and disabled-alarm cleanup\n");
                finish(-1, result);
                return;
            }
            if (lgInstall) {
                LgInstallCheck.run(this);
                result.putString(
                        "stream",
                        "PASS: LG SSH identity rejection, passphrase, IPK upload/hash, failed"
                            + " install, explicit retry, version verification and launch against"
                            + " local fixture\n");
                finish(-1, result);
                return;
            }
            if (camera) {
                CameraCheck.run(this);
                result.putString(
                        "stream",
                        "PASS: scanner opens and releases the owned emulator virtual camera; no"
                                + " physical-camera acceptance\n");
                finish(-1, result);
                return;
            }
            if (live) {
                LiveUiCheck.run(this, lg ? "lg" : "android");
                result.putString(
                        "stream",
                        lg
                                ? "PASS: phone UI paired with the native LG TLS/actual settings"
                                      + " fixture; capabilities, 5% volume, preview/Apply/Cancel,"
                                      + " recreation, reconnect without upload, transport loss and"
                                      + " Arabic stale cache\n"
                                : "PASS: phone UI paired with the actual TV app in a second owned"
                                        + " emulator; real settings, preview/Apply/Cancel,"
                                        + " recreation, reconnect without upload, actual transport"
                                        + " loss and Arabic stale cache\n");
                finish(-1, result);
                return;
            }
            if (lan) {
                LanCheck.run(this);
                result.putString(
                        "stream",
                        "PASS: pinned TLS, approval/revocation, encrypted credential"
                                + " recovery/tamper rejection, QR camera-frame decoding and"
                                + " uncertain-write refresh without replay\n");
                finish(-1, result);
                return;
            }
            PhoneStoreCheck.run(this);
            for (String lang : new String[] {"en", "ar"}) {
                getTargetContext()
                        .getSharedPreferences("companion", 0)
                        .edit()
                        .putString("language", lang)
                        .commit();
                Intent intent =
                        new Intent(getTargetContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                MainActivity activity = (MainActivity) startActivitySync(intent);
                Throwable[] error = {null};
                runOnMainSync(() -> {
                    try {
                        verify(activity);
                    } catch (Throwable failure) {
                        error[0] = failure;
                    }
                });
                if (error[0] != null) throw new AssertionError(lang, error[0]);
                android.os.SystemClock.sleep(4000);
                runOnMainSync(() -> {
                    activity.page = "tvs";
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-tvs.png");
                centeredIcon(activity, "pair.open");
                runOnMainSync(() -> {
                    activity.getWindow()
                            .getDecorView()
                            .findViewWithTag("install.open")
                            .performClick();
                    check(activity.page.equals("tv-setup"), "Unified setup entry");
                    activity.body.findViewWithTag("setup.platform.lg").performClick();
                    check(activity.page.equals("lg-setup"), "LG setup entry");
                    check(activity.body.findViewWithTag("setup.instruction.5") != null
                                    && activity.body.findViewWithTag("setup.channel.latest") != null,
                            "LG guide and selected release must share the connection page");
                    EditText address = activity.body.findViewWithTag("lg.host");
                    EditText phrase = activity.body.findViewWithTag("lg.passphrase");
                    check(phrase != null && !phrase.isSaveEnabled(), "LG passphrase must remain private");
                    address.setText("127.0.0.1");
                    phrase.setText("fixture-private");
                    activity.getWindow().getDecorView().findViewWithTag("lg.connect").performClick();
                    check(!activity.networkBusy && activity.body.findViewWithTag("lg.host") != null,
                            "Invalid LG address must be rejected before networking");
                    check(((EditText) activity.body.findViewWithTag("lg.passphrase")).getText().toString().equals("fixture-private"),
                            "Correcting an invalid host must preserve the session-only passphrase");
                });
                waitForIdleSync();
                capture(activity, lang + "-lg-setup.png");
                runOnMainSync(() -> {
                    activity.selected = 0;
                    activity.settingsUi.readSettings();
                    activity.page = "settings";
                    activity.section = "appearance";
                    activity.settingsUi.appearance = null;
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-appearance.png");
                runOnMainSync(() -> {
                    activity.settingsUi.appearance = null;
                    activity.page = "overview";
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-remote.png");
                runOnMainSync(() -> {
                    activity.page = "pair";
                    activity.pairingUi.pairState = "";
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-pair-start.png");
                centeredIcon(activity, "pair.scan");
                runOnMainSync(() -> {
                    activity.page = "pair";
                    activity.pairingUi.pairState = "requesting";
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-pair-connecting.png");
                centeredIcon(activity, "pair.cancel");
                runOnMainSync(() -> {
                    activity.pairingUi.pairState = "waiting";
                    activity.pairingUi.pairCode = "123456";
                    activity.render();
                });
                waitForIdleSync();
                capture(activity, lang + "-pair-approval.png");
                runOnMainSync(activity::finish);
                LiveUiCheck.untilUi(this, activity::isDestroyed);
                InstallationCheck.run(this, lang);
                count++;
            }
            result.putString(
                    "stream",
                    "PASS: bilingual native bindings, capabilities, drafts, native number editor,"
                            + " saves, offline and specialized validation ("
                            + count
                            + " languages)\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }

    void verify(MainActivity a) {
        String launcherLabel =
                getTargetContext()
                        .getApplicationInfo()
                        .loadLabel(getTargetContext().getPackageManager())
                        .toString();
        check(
                launcherLabel.equals("Miqat") || launcherLabel.equals("ميقات"),
                "Phone launcher label must be Miqat / ميقات");
        for (String destination : new String[] {"prayers", "tvs", "phone-settings"}) {
            a.page = destination;
            a.section = "";
            a.render();
            check(
                    a.getWindow().getDecorView().findViewWithTag("navigation") != null,
                    "Floating navigation missing on main destination " + destination);
            for (String root : new String[] {"prayers", "tvs", "phone-settings"})
                check(
                        a.getWindow().getDecorView().findViewWithTag("nav." + root) != null,
                        "Main destination unavailable");
            check(
                    a.getWindow().getDecorView().findViewWithTag("nav." + destination).isSelected(),
                    "Wrong active main destination");
        }
        for (String destination : new String[] {"overview", "settings", "preferences", "pair"}) {
            a.page = destination;
            a.section = "";
            a.render();
            check(
                    a.getWindow().getDecorView().findViewWithTag("navigation") == null,
                    "Setup/detail must not stack main navigation");
            check(
                    a.getWindow().getDecorView().findViewWithTag("navigation.back") != null,
                    "Detail screen has no visible Back");
            if (destination.equals("overview") || destination.equals("settings"))
                check(
                        a.getWindow()
                                .getDecorView()
                                .findViewWithTag("tv-nav." + destination)
                                .isSelected(),
                        "TV tab selection");
        }
        a.page = "overview";
        a.networkBusy = true;
        a.render();
        View home = a.body.findViewWithTag("device.home");
        check(
                home != null && !home.isEnabled(),
                "Sending must retain and disable the existing controls");
        a.networkBusy = false;
        a.render();

        for (int selected = 0; selected < 2; selected++) {
            a.selected = selected;
            a.tv().setOnline(true);
            a.page = "overview";
            a.render();
            int remoteWrites = a.tv().getWriteCount();
            for (String id :
                    new String[] {
                        "device.home",
                        "device.inputs",
                        "radio.play",
                        "radio.stop",
                        "tests.adhan-short",
                        "tests.cancel"
                    }) {
                View command = a.getWindow().getDecorView().findViewWithTag(id);
                check(command != null && command.isEnabled(), "Missing Remote control " + id);
                check(
                        command.getContentDescription() != null
                                && !command.getContentDescription().toString().isEmpty(),
                        "Unlabelled icon " + id);
                if (!id.equals("device.inputs")) command.performClick();
            }
            check(
                    a.tv().getWriteCount() == remoteWrites,
                    "Remote commands must not write settings");
            a.tv().setOnline(false);
            a.render();
            check(
                    !a.getWindow().getDecorView().findViewWithTag("device.home").isEnabled(),
                    "Offline Home must be disabled");
            check(
                    a.getWindow().getDecorView().findViewWithTag("tests.cancel").isEnabled(),
                    "Stop remains available for recovery");
            a.selected = selected;
            a.tv().setOnline(true);
            a.settingsUi.snapshot = null;
            a.settingsUi.appearance = null;
            a.section = "";
            a.query = "";
            int writes = a.tv().getWriteCount();
            a.tv().connect();
            a.settingsUi.readSettings();
            check(writes == a.tv().getWriteCount(), "Opening Settings wrote values");
            JSONArray controls = a.catalog.controls(a.profile());
            for (int n = 0; n < controls.length(); n++) {
                JSONObject c = controls.optJSONObject(n);
                String id = c.optString("id");
                a.page = "settings";
                a.section = c.optString("section");
                a.settingsUi.appearance = null;
                a.query = "";
                a.render();
                if (id.equals("appearance.home")) continue; // Native Back supplies this navigation.
                View bound = a.getWindow().getDecorView().findViewWithTag(id);
                check(bound != null, "Missing native binding " + a.profile() + ":" + id);
                if (!c.optBoolean("available"))
                    check(
                            bound.findViewWithTag(id + ".edit") == null,
                            "Unsupported editable control " + id);
            }
            a.page = "settings";
            a.section = "appearance";
            a.settingsUi.appearance = null;
            a.render();
            check(
                    a.body.getLayoutDirection()
                            == (a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR),
                    "Incorrect native layout direction");
            check(
                    "apply-appearance"
                            .equals(
                                    a.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("appearance.apply")
                                            .getTag(tv.miqat.companion.R.id.companion_action)),
                    "Appearance action not bound to core contract");
            Map<String, String> before = new LinkedHashMap<>(a.tv().read().getValues());
            View nextTheme = a.getWindow().getDecorView().findViewWithTag("appearance.theme.next");
            check(nextTheme != null, "Theme carousel missing");
            if (nextTheme.isEnabled()) nextTheme.performClick();
            else
                a.getWindow()
                        .getDecorView()
                        .findViewWithTag("appearance.theme.previous")
                        .performClick();
            String stagedTheme = a.settingsUi.appearance.getValues().get("appearance.theme");
            a.getWindow()
                    .getDecorView()
                    .findViewWithTag("appearance.countdown.next")
                    .performClick();
            a.getWindow()
                    .getDecorView()
                    .findViewWithTag("appearance.reset.activate")
                    .performClick();
            check(
                    a.settingsUi.appearance.getValues().get("appearance.theme").equals(stagedTheme),
                    "Layout reset discarded the selected theme");
            check(
                    a.settingsUi
                            .appearance
                            .getValues()
                            .get("appearance.countdown")
                            .equals(a.catalog.defaults(a.profile()).get("appearance.countdown")),
                    "Layout reset did not reset layout");
            check(a.tv().read().getValues().equals(before), "Preview wrote values");
            a.getWindow().getDecorView().findViewWithTag("appearance.cancel").performClick();
            check(
                    a.settingsUi.appearance == null && a.tv().read().getValues().equals(before),
                    "Cancel changed saved appearance");
            if (selected == 0) {
                a.settingsUi.openSection("appearance");
                a.settingsUi.appearance.set(a.settingsUi.spec("appearance.custom-theme"), "");
                a.settingsUi.appearance.set(a.settingsUi.spec("appearance.theme"), "custom");
                a.settingsUi.appearance.set(
                        a.settingsUi.spec("appearance.lighting"),
                        a.settingsUi.appearance.getValues().get("appearance.lighting").equals("0")
                                ? "5"
                                : "0");
                a.settingsUi.applyAppearance();
                check(
                        a.settingsUi.editor != null && a.tv().read().getValues().equals(before),
                        "New custom theme opens colours before applying");
                a.settingsUi.editor.getButton(-1).performClick();
                check(
                        !a.settingsUi
                                .appearance
                                .getValues()
                                .get("appearance.custom-theme")
                                .isEmpty(),
                        "Unedited default colours form a valid custom theme");
                a.settingsUi.appearance.set(
                        a.settingsUi.spec("appearance.custom-theme"),
                        "{\"version\":1,\"colors\":{},\"surface\":\"outline\",\"background\":\"solid\",\"lighting\":45}");
                a.settingsUi.edit("appearance.custom-theme");
                a.settingsUi.editor.getButton(-1).performClick();
                try {
                    JSONObject custom =
                            new JSONObject(
                                    a.settingsUi
                                            .appearance
                                            .getValues()
                                            .get("appearance.custom-theme"));
                    check(
                            custom.getString("surface").equals("outline")
                                    && custom.getString("background").equals("solid")
                                    && custom.getInt("lighting") == 45,
                            "Colour editing preserves other custom-theme fields");
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                a.getWindow().getDecorView().findViewWithTag("appearance.cancel").performClick();
                check(
                        a.tv().read().getValues().equals(before),
                        "Custom colour editing remained a local draft");
            }
            a.settingsUi.appearance = null;
            a.section = "display";
            a.render();
            SettingSpec setting = a.settingsUi.spec("display.size");
            String old = before.get("display.size"), next = old.equals("150") ? "140" : "150";
            a.settingsUi.save(before, Collections.singletonMap(setting.getId(), next), false);
            check(
                    a.settingsUi.snapshot.getValues().get(setting.getId()).equals(next),
                    "Save not reflected");
            check(
                    a.tv().read().getValues().get(setting.getId()).equals(next),
                    "Save not persisted");
            a.settingsUi.edit("display.size");
            View inline =
                    a.getWindow().getDecorView().findViewWithTag("display.size.number-editor");
            check(inline != null, "Native inline number editor absent");
            View done = a.getWindow().getDecorView().findViewWithTag("display.size.save");
            check(
                    "save-edit".equals(done.getTag(tv.miqat.companion.R.id.companion_action)),
                    "Editor save not bound to core contract");
            EditText number = findInput(inline);
            check(number != null, "Missing number input");
            number.setText("999");
            done.performClick();
            check(
                    a.getWindow().getDecorView().findViewWithTag("display.size.number-editor")
                            != null,
                    "Invalid value closed editor");
            check(
                    a.tv().read().getValues().get("display.size").equals(next),
                    "Invalid number persisted");
            number.setText("100");
            done.performClick();
            check(
                    a.tv().read().getValues().get("display.size").equals("100"),
                    "Native editor did not save: actual="
                            + a.tv().read().getValues().get("display.size")
                            + ", input="
                            + number.getText());
            a.settingsUi.edit("display.size");
            findInput(a.getWindow().getDecorView().findViewWithTag("display.size.number-editor"))
                    .setText("120");
            a.getWindow().getDecorView().findViewWithTag("display.size.cancel").performClick();
            check(
                    a.tv().read().getValues().get("display.size").equals("100"),
                    "Inline Cancel persisted a draft");
            a.getWindow().getDecorView().findViewWithTag("display.size.plus").performClick();
            check(
                    a.tv().read().getValues().get("display.size").equals("100"),
                    "Stepper saved before Done");
            check(
                    findInput(
                                    a.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("display.size.number-editor"))
                            .getText()
                            .toString()
                            .equals("110"),
                    "Stepper did not use the shared increment");
            a.getWindow().getDecorView().findViewWithTag("display.size.cancel").performClick();
            a.tv().setOnline(false);
            a.settingsUi.readSettings();
            a.section = "display";
            a.render();
            check(!a.settingsUi.editable(), "Offline settings editable");
            check(
                    !a.getWindow().getDecorView().findViewWithTag("display.size.edit").isEnabled(),
                    "Offline view enabled");
            a.tv().setOnline(true);
            a.settingsUi.readSettings();
            a.section = "display";
            a.render();
            String previous = a.tv().read().getValues().get("display.quick-labels");
            View toggle = a.getWindow().getDecorView().findViewWithTag("display.quick-labels.edit");
            check(toggle instanceof android.widget.Switch, "Toggle is not a native switch");
            toggle.performClick();
            check(
                    !previous.equals(a.tv().read().getValues().get("display.quick-labels")),
                    "Switch did not save completed edit");
            a.section = "prayer";
            a.render();
            check(
                    a.getWindow().getDecorView().findViewWithTag("prayer.asr.segments")
                            instanceof android.widget.RadioGroup,
                    "Short choices are not segmented");
            a.section = "reminders";
            a.render();
            android.widget.AutoCompleteTextView choices =
                    a.getWindow().getDecorView().findViewWithTag("reminders.voice.dropdown");
            List<String> allowed = a.settingsUi.spec("reminders.voice").getOptions();
            check(
                    choices != null && choices.getAdapter().getCount() == allowed.size(),
                    "Voice choice bindings incomplete");
            for (int i = 0; i < allowed.size(); i++)
                check(
                        choices.getAdapter()
                                .getItem(i)
                                .toString()
                                .equals(
                                        a.catalog.value(
                                                a.profile(),
                                                "reminders.voice",
                                                allowed.get(i),
                                                a.ar)),
                        "Wrong native voice choice label");
            String oldVoice = a.tv().read().getValues().get("reminders.voice");
            int pick = (allowed.indexOf(oldVoice) + 1) % allowed.size();
            pickChoice(a, "reminders.voice", pick);
            check(
                    a.tv().read().getValues().get("reminders.voice").equals(oldVoice),
                    "Preview voice selection saved before confirmation");
            check(a.settingsUi.isPending("reminders.voice"), "Voice draft lost");
            a.getWindow().getDecorView().findViewWithTag("reminders.voice.cancel").performClick();
            check(
                    a.tv().read().getValues().get("reminders.voice").equals(oldVoice),
                    "Voice Cancel changed settings");
            pickChoice(a, "reminders.voice", pick);
            a.getWindow().getDecorView().findViewWithTag("reminders.voice.save").performClick();
            check(
                    a.tv().read().getValues().get("reminders.voice").equals(allowed.get(pick)),
                    "Voice confirmation did not save selection");
        }
        a.selected = 0;
        try {
            a.settingsUi.validateSpecial(a.settingsUi.spec("device.redirects"), "quran=video");
            boolean rejected = false;
            try {
                a.settingsUi.validateSpecial(
                        a.settingsUi.spec("device.redirects"), "quran=video,video=quran");
            } catch (Exception expected) {
                rejected = true;
            }
            check(rejected, "Redirect cycle accepted");
            a.settingsUi.validateSpecial(
                    a.settingsUi.spec("appearance.custom-theme"),
                    "{\"version\":1,\"colors\":{\"gold-400\":\"#123456\"}}");
            rejected = false;
            try {
                a.settingsUi.validateSpecial(
                        a.settingsUi.spec("appearance.custom-theme"),
                        "{\"version\":1,\"colors\":{\"gold-400\":\"bad\"}}");
            } catch (Exception expected) {
                rejected = true;
            }
            check(rejected, "Malformed theme accepted");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        check(a.getWindow().getDecorView().getRootView() != null, "No native window");
    }

    EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText input = findInput(group.getChildAt(i));
                if (input != null) return input;
            }
        }
        return null;
    }

    void pickChoice(MainActivity a, String id, int index) {
        android.widget.AutoCompleteTextView field =
                a.getWindow().getDecorView().findViewWithTag(id + ".dropdown");
        android.widget.ListView parent = new android.widget.ListView(a);
        parent.setAdapter(field.getAdapter());
        field.getOnItemClickListener().onItemClick(parent, null, index, index);
    }

    void centeredIcon(MainActivity activity, String tag) {
        runOnMainSync(() -> {
            android.widget.Button button = activity.getWindow().getDecorView().findViewWithTag(tag);
            android.graphics.drawable.Drawable icon = button.getCompoundDrawablesRelative()[0];
            android.text.Layout label = button.getLayout();
            check(icon != null && label != null, "Missing inline icon/label: " + tag);
            float left =
                    activity.ar
                            ? button.getCompoundPaddingLeft()
                                    + label.getLineLeft(0)
                                    - button.getScrollX()
                            : button.getPaddingLeft();
            float right =
                    activity.ar
                            ? button.getWidth() - button.getPaddingRight()
                            : button.getCompoundPaddingLeft()
                                    + label.getLineRight(0)
                                    - button.getScrollX();
            check(
                    Math.abs((left + right) / 2 - button.getWidth() / 2f) <= activity.views.dp(2),
                    "Icon and label are not centered as a group: " + tag);
            float gap =
                    activity.ar
                            ? button.getWidth()
                                    - button.getPaddingRight()
                                    - icon.getBounds().width()
                                    - (button.getCompoundPaddingLeft()
                                            + label.getLineRight(0)
                                            - button.getScrollX())
                            : button.getCompoundPaddingLeft()
                                    + label.getLineLeft(0)
                                    - button.getScrollX()
                                    - button.getPaddingLeft()
                                    - icon.getBounds().width();
            check(
                    gap >= activity.views.dp(6) && gap <= activity.views.dp(10),
                    "Icon is separated from its label: " + tag);
        });
    }

    void capture(MainActivity activity, String name) throws Exception {
        waitForIdleSync();
        android.os.SystemClock.sleep(350);
        if (!captures) return;
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        check(screenshot != null, "No emulator screenshot");
        try (FileOutputStream out =
                new FileOutputStream(new File(activity.getExternalFilesDir(null), name))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            screenshot.recycle();
        }
    }
}
