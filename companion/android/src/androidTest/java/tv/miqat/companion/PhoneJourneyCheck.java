package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Fresh use, immediate appearance and unified setup boundaries; no physical TV or network. */
final class PhoneJourneyCheck {
    static void run(Instrumentation test, boolean captures) throws Exception {
        Context context = test.getTargetContext();
        SharedPreferences prefs = PhoneStore.preferences(context);
        Map<String, ?> original = new LinkedHashMap<>(prefs.getAll());
        String language =
                context.getSharedPreferences("companion", 0).getString("language", "system");
        MainActivity[] current = {null};
        try {
            for (String lang : new String[] {"en", "ar"}) {
                prefs.edit().clear().commit();
                context.getSharedPreferences("companion", 0)
                        .edit()
                        .putString("language", lang)
                        .commit();
                exploreFirst(test);
                check(prefs.edit().clear().commit(), "Could not reset fresh-use fixture after Explore first");
                MainActivity welcome = LiveUiCheck.launch(test);
                current[0] = welcome;
                PhoneUiCheck.on(test, () -> {
                    check(welcome.page.equals("onboarding") && welcome.onboarding.step() == 0,
                            "Fresh use must begin with the welcome");
                    check(welcome.body.findViewWithTag("phone.location.detect") == null
                                    && welcome.body.findViewWithTag("phone.reminders.enabled.edit") == null,
                            "Welcome must introduce Miqat before location or alert controls");
                    Set<String> welcomeCopy = new LinkedHashSet<>();
                    for (int i = 0; i < welcome.body.getChildCount(); i++) {
                        View child = welcome.body.getChildAt(i);
                        if (child instanceof TextView) welcomeCopy.add(((TextView) child).getText().toString());
                    }
                    check(welcomeCopy.equals(new LinkedHashSet<>(Arrays.asList(
                                    welcome.t("setup_welcome"), welcome.t("setup_welcome_tagline"),
                                    welcome.t("setup_welcome_body")))),
                            "Welcome retained extra promotional copy");
                });
                PhoneUiCheck.capture(test, welcome, lang + "-journey-welcome", captures);
                PhoneUiCheck.on(test, welcome::finish);
                LiveUiCheck.untilUi(test, welcome::isDestroyed);
                MainActivity a = LiveUiCheck.launch(test);
                current[0] = a;
                PhoneUiCheck.on(test, () -> {
                    check(a.page.equals("onboarding") && a.onboarding.step() == 0,
                            "Unfinished welcome did not resume");
                    a.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                    check(a.onboarding.step() == 1 && !a.phoneUi.settings.locationReady,
                            "Get started must open unconfirmed location setup");
                    check(a.body.findViewWithTag("phone.location.detect") != null
                                    && a.body.findViewWithTag("phone.reminders.enabled.edit") == null,
                            "Location needs compact detection without an alert switch");
                    check(((Button) a.getWindow().getDecorView().findViewWithTag("onboarding.next"))
                                    .getText().toString().equals(a.t("phone_confirm_location")),
                            "Location confirmation must not be replaced with permission instructions");
                });
                resolveLocationRequest(test,a);
                PhoneUiCheck.capture(test, a, lang + "-journey-location", captures);
                PhoneUiCheck.on(test, a::finish);
                LiveUiCheck.untilUi(test, a::isDestroyed);
                MainActivity location = LiveUiCheck.launch(test);
                current[0] = location;
                PhoneUiCheck.on(test, () -> {
                    check(location.page.equals("onboarding") && location.onboarding.step() == 1
                                    && !location.phoneUi.settings.locationReady,
                            "Location setup progress lost after reopening");
                    location.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                    check(location.onboarding.step() == 2 && location.phoneUi.settings.locationReady,
                            "Confirm location must save location and continue to style");
                });
                resolveNotificationRequest(test, location);
                PhoneUiCheck.on(test, () -> {
                    check(location.phoneUi.settings.enabled == PhoneAlarms.notifications(location),
                            "Onboarding alert preference must follow the notification decision");
                    check(prefs.getBoolean("onboarding-notifications-asked", false),
                            "Notification decision was not retained");
                    location.back();
                    check(location.onboarding.step() == 1, "Style Back must return to location");
                    location.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                    check(location.onboarding.step() == 2
                                    && !prefs.getBoolean("onboarding-notifications-pending", false),
                            "Confirming location again repeated the notification request");
                    location.phoneUi.save("reminders.enabled", "false");
                    location.onboarding.notificationResult();
                    check(!location.phoneUi.settings.enabled,
                            "A duplicate notification callback re-enabled alerts");
                });
                PhoneUiCheck.on(test, location::finish);
                LiveUiCheck.untilUi(test, location::isDestroyed);
                MainActivity resumed = LiveUiCheck.launch(test);
                current[0] = resumed;
                PhoneUiCheck.on(test, () -> {
                    check(resumed.page.equals("onboarding") && resumed.onboarding.step() == 2
                                    && resumed.phoneUi.settings.locationReady,
                            "Style setup progress lost after reopening");
                    check(resumed.body.findViewWithTag("phone.appearance.preview") != null
                                    && resumed.body.findViewWithTag("appearance.tab.appearance.theme") != null
                                    && resumed.body.findViewWithTag("appearance.tab.appearance.background") != null,
                            "Style needs one live preview and direct visual choices");
                    check(resumed.getWindow().getDecorView().findViewWithTag("onboarding.skip") == null,
                            "Finish needs no duplicate Skip");
                    check(((Button) resumed.getWindow().getDecorView().findViewWithTag("onboarding.next"))
                                    .getText().toString().equals(resumed.t("setup_finish")),
                            "Setup must finish without alarm access instructions");
                    resumed.body.findViewWithTag("appearance.tab.appearance.theme").performClick();
                });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-personalization", captures);
                String before = PhoneStore.read(context).value("appearance.theme");
                int frostAccent =
                        android.graphics.Color.parseColor(
                                resumed.phoneUi.data.colors("frost").getString("gold-400"));
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.frost")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.theme")
                                            .equals("frost"),
                                    "Guided carousel did not select the previewed theme");
                            check(
                                    ((TextView) resumed.body.findViewWithTag("phone.next.name"))
                                                    .getCurrentTextColor()
                                            == frostAccent,
                                    "Live preview did not apply the selected theme");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.background")
                                    .performClick();
                            resumed.body
                                    .findViewWithTag(
                                            "phone.appearance.background.option.dusk_peaks")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.background")
                                            .equals("dusk_peaks"),
                                    "Live background selection was not applied");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-background", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.next")
                                    .performClick();
                            check(
                                    resumed.page.equals("prayers") && !resumed.onboarding.needed(),
                                    "Completed first use did not reach standalone prayers");
                            check(
                                    resumed.body.findViewWithTag("onboarding.resume") == null,
                                    "Completed setup still shows its Home prompt");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-home-complete", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("phone.location").performClick();
                            if (PhoneLocation.allowed(resumed)) {
                                check(resumed.page.equals("prayers") && resumed.phoneUi.settings.locationReady
                                                && (resumed.phoneUi.locationUi.locator.running
                                                    || resumed.phoneUi.locationUi.locationMessage.equals("phone_location_updated")),
                                        "Permitted city action must refresh without leaving Home");
                                resumed.phoneUi.locationUi.cancelLocation();
                            } else check(resumed.section.equals("calculation"),
                                    "Denied city action must open location settings");
                            resumed.tab("prayers");
                            resumed.onboarding.open();
                            resumed.back();
                            check(
                                    !resumed.onboarding.needed() && resumed.page.equals("prayers"),
                                    "Cancelling a repeated guide erased completed setup");
                            check(
                                    resumed.body.findViewWithTag("onboarding.resume") == null,
                                    "Repeated setup restored a completed Home prompt");
                            resumed.phoneUi.save("reminders.enabled", "false");
                            check(prefs.edit().putBoolean("onboarding-complete", false)
                                            .remove("onboarding-enable-with-permission")
                                            .remove("onboarding-notifications-asked")
                                            .remove("onboarding-notifications-pending").commit(),
                                    "Could not prepare an existing opted-out setup fixture");
                            resumed.onboarding.open();
                            resumed.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                            resumed.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                            check(resumed.onboarding.step() == 2 && !resumed.phoneUi.settings.enabled
                                            && !prefs.getBoolean("onboarding-notifications-pending", false),
                                    "Incomplete setup erased an existing notification opt-out");
                            resumed.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                            check(resumed.page.equals("prayers") && !resumed.onboarding.needed(),
                                    "Existing opted-out user could not finish setup");
                            resumed.phoneUi.open("appearance");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.theme")
                                    .performClick();
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.dusk")
                                    .performClick();
                            check(resumed.phoneUi.settings.value("appearance.theme").equals("dusk"),
                                    "Theme selection did not save immediately");
                            check(resumed.body.findViewWithTag("phone.appearance.preview") != null,
                                    "Live appearance preview missing");
                            resumed.phoneUi.save("display.clock", "24");
                            check(resumed.phoneUi.settings.value("display.clock").equals("24"),
                                    "Clock selection did not save immediately");
                            check(resumed.getWindow().getDecorView().findViewWithTag("appearance.apply") == null,
                                    "Appearance still requires Apply");
                            resumed.back();
                        });
                check(PhoneStore.read(context).value("appearance.theme").equals("dusk")
                                && PhoneStore.read(context).value("display.clock").equals("24"),
                        "Back discarded immediate appearance settings");
                PhoneUiCheck.on(test, () -> resumed.phoneUi.open("appearance"));
                PhoneUiCheck.capture(test, resumed, lang + "-journey-frost-preview", captures);
                PhoneUiCheck.on(test, () -> {
                    check(resumed.phoneUi.settings.value("appearance.theme").equals("dusk"),
                            "Reopening Appearance lost the saved theme");
                    resumed.phoneUi.save("appearance.theme", before);
                    resumed.tab("prayers");
                });
                PhoneUiCheck.capture(test, resumed, lang + "-home-theme-complete", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("widgets.open").performClick();
                            android.widget.Button add =
                                    resumed.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("widget.add");
                            check(
                                    add.getText().toString().equals(resumed.t("widget_add")),
                                    "A new widget must enter Add, not update an invalid widget ID");
                            check(
                                    resumed.body
                                                    .findViewWithTag("widget.preview")
                                                    .findViewById(R.id.widget_times)
                                            != null,
                                    "Widget must render actual RemoteViews");
                        });
                if (captures) {
                    PhoneUiCheck.on(test, () -> resumed.body.findViewWithTag("widget.layout.circle").performClick());
                    PhoneUiCheck.capture(test, resumed, lang + "-journey-widget", true);
                }
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("widget.layout.strip").performClick();
                            resumed.back();
                            check(
                                    resumed.page.equals("prayers"),
                                    "Widget Back did not leave the editor");
                            resumed.body.findViewWithTag("widgets.open").performClick();
                            check(
                                    ((android.view.View)
                                                    resumed.body.findViewWithTag(
                                                            "widget.layout.strip"))
                                            .isSelected(),
                                    "Widget Back discarded the saved layout");
                        });
                WidgetHostCheck.run(test, resumed, captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.tab("tvs");
                            resumed.body.findViewWithTag("pair.open").performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-platforms", captures);
                supportedPlatforms(test, resumed);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.platform.lg").performClick();
                            check(
                                    resumed.page.equals("lg-setup")
                                            && resumed.body.findViewWithTag("setup.instruction.5")
                                                    != null
                                            && resumed.body.findViewWithTag("lg.host") != null
                                            && resumed.body.findViewWithTag("lg.passphrase")
                                                    != null,
                                    "LG numbered guide and credentials must share one page");
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.latest")
                                            .isSelected(),
                                    "Latest must be default");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-lg-guide", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.channel.preview").performClick();
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.preview")
                                            .isSelected(),
                                    "Release choice did not update");
                            resumed.back();
                            resumed.body.findViewWithTag("setup.platform.android").performClick();
                            check(
                                    resumed.body.findViewWithTag("setup.instruction.1") != null
                                            && resumed.getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("setup.continue")
                                                    != null,
                                    "Android preparation and release must share one page");
                            resumed.body.findViewWithTag("setup.channel.preview").performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-channel", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.advanced").performClick();
                            check(
                                    resumed.body.findViewWithTag("setup.local") != null
                                            && resumed.getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("setup.continue")
                                                    == null,
                                    "Advanced must switch to local package UI");
                            resumed.tab("prayers");
                            resumed.body.findViewWithTag("qibla.open").performClick();
                            check(
                                    resumed.page.equals("qibla")
                                            && resumed.body.findViewWithTag("qibla.mode.map") != null,
                                    "Qibla screen did not open");
                        });
                LiveUiCheck.untilUi(
                        test,
                        () -> {
                            View compass = resumed.body.findViewWithTag("qibla.compass");
                            TextView status = resumed.body.findViewWithTag("qibla.status");
                            return resumed.body.findViewWithTag("qibla.location.permission") != null
                                    || resumed.body.findViewWithTag("qibla.location.retry") != null
                                    || compass != null && compass.getVisibility() == View.VISIBLE
                                    || status != null
                                            && status.getText()
                                                    .toString()
                                                    .equals(resumed.t("qibla_no_sensor"));
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-qibla", captures);
                cancelledResult(test, resumed);
                PhoneUiCheck.on(test, resumed::finish);
                LiveUiCheck.untilUi(test, resumed::isDestroyed);
                MainActivity reopened = LiveUiCheck.launch(test);
                current[0] = reopened;
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    reopened.page.equals("prayers")
                                            && reopened.body.findViewWithTag("onboarding.resume")
                                                    == null,
                                    "Completed setup prompt returned after cold launch");
                        });
                PhoneUiCheck.on(test, reopened::finish);
                LiveUiCheck.untilUi(test, reopened::isDestroyed);
                current[0] = null;
            }
        } finally {
            if (current[0] != null) PhoneUiCheck.on(test, current[0]::finish);
            restore(prefs,original);
            context.getSharedPreferences("companion", 0)
                    .edit()
                    .putString("language", language)
                    .commit();
            PhoneAlarms.rebuild(context);
        }
    }

    private static void exploreFirst(Instrumentation test) throws Exception {
        MainActivity a = LiveUiCheck.launch(test);
        SharedPreferences prefs = PhoneStore.preferences(a);
        try {
            PhoneUiCheck.on(test, () -> {
                check(a.onboarding.step() == 0 && a.page.equals("onboarding"),
                        "Explore first must start at the fresh welcome");
                a.getWindow().getDecorView().findViewWithTag("onboarding.skip").performClick();
                check(a.page.equals("prayers") && !a.phoneUi.settings.enabled,
                        "Explore first must open prayers without enabling alerts");
                check(prefs.getBoolean("onboarding-enable-with-permission", false),
                        "Explore first incorrectly recorded an explicit notification opt-out");
                a.phoneUi.open("appearance");
                a.body.findViewWithTag("appearance.tab.appearance.theme").performClick();
                a.body.findViewWithTag("phone.appearance.theme.option.dusk").performClick();
                check(prefs.contains("settings") && !a.phoneUi.settings.enabled
                                && a.phoneUi.settings.value("appearance.theme").equals("dusk"),
                        "Immediate theme selection did not create the expected disabled-alert settings");
                check(prefs.getBoolean("onboarding-enable-with-permission", false),
                        "An unrelated settings save changed the first-use notification choice");
                a.onboarding.open();
                a.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                check(a.onboarding.step() == 1
                                && prefs.getBoolean("onboarding-enable-with-permission", false)
                                && !prefs.getBoolean("onboarding-notifications-asked", false)
                                && !prefs.getBoolean("onboarding-notifications-pending", false),
                        "Resuming setup after Explore first mistook default false for an opt-out");
            });
            resolveLocationRequest(test,a);
        } finally {
            PhoneUiCheck.on(test, a::finish);
            LiveUiCheck.untilUi(test, a::isDestroyed);
        }
    }

    private static void resolveNotificationRequest(Instrumentation test, MainActivity a) throws Exception {
        SharedPreferences prefs = PhoneStore.preferences(a);
        android.accessibilityservice.AccessibilityServiceInfo service = test.getUiAutomation().getServiceInfo();
        int originalFlags = service.flags;
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(service);
        Set<String> observed = new LinkedHashSet<>();
        boolean clicked = false;
        try {
            long deadline = android.os.SystemClock.uptimeMillis() + 10000;
            while (prefs.getBoolean("onboarding-notifications-pending", false)
                    && android.os.SystemClock.uptimeMillis() < deadline) {
                android.view.accessibility.AccessibilityNodeInfo root =
                        test.getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    try {
                        String owner = String.valueOf(root.getPackageName());
                        observed.add(owner);
                        if (!clicked && owner.contains("permissioncontroller"))
                            clicked = declineNotification(root, observed);
                    } finally { root.recycle(); }
                }
                android.os.SystemClock.sleep(50);
            }
        } finally {
            service.flags = originalFlags;
            test.getUiAutomation().setServiceInfo(service);
        }
        check(!prefs.getBoolean("onboarding-notifications-pending", false),
                "System notification decision did not return to onboarding; observed=" + observed);
        LiveUiCheck.untilUi(test, () -> a.hasWindowFocus() && a.onboarding.step() == 2);
    }

    private static void resolveLocationRequest(Instrumentation test,MainActivity a) throws Exception {
        android.accessibilityservice.AccessibilityServiceInfo service=test.getUiAutomation().getServiceInfo();
        service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(service);
        test.getUiAutomation().waitForIdle(300,5000);
        boolean[] clicked={false};
        LiveUiCheck.until(()->{
            android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            try {
                if(!clicked[0] && root!=null && String.valueOf(root.getPackageName()).contains("permissioncontroller"))
                    clicked[0]=declineNotification(root,new LinkedHashSet<>());
            } finally {if(root!=null)root.recycle();}
            return !a.phoneUi.locationUi.locationPermissionPending;
        });
        LiveUiCheck.untilUi(test,a::hasWindowFocus);
        PhoneUiCheck.on(test,()->{if(a.phoneUi.locationUi.locator.running){a.phoneUi.locationUi.cancelLocation();a.render();}});
    }

    static void location(Instrumentation test,boolean captures) throws Exception {
        Context c=test.getTargetContext();SharedPreferences prefs=PhoneStore.preferences(c);
        Map<String,?> original=new LinkedHashMap<>(prefs.getAll());
        SharedPreferences ui=c.getSharedPreferences("companion",0);String lang=ui.getString("language","system");
        boolean granted=PhoneLocation.allowed(c);
        android.location.LocationManager manager=c.getSystemService(android.location.LocationManager.class);
        MainActivity a=null;boolean provider=false;
        try {
            PhoneBackgroundLocation.cancel(c);prefs.edit().clear().commit();
            ui.edit().putString("language",granted?"ar":"en").commit();
            if(granted) {
                check(PhoneBackgroundLocation.allowed(c),"Grant optional background access for the enabled-path fixture");
                manager.addTestProvider("gps",false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE);
                provider=true;manager.setTestProviderEnabled("gps",true);
                android.location.Location fix=new android.location.Location("gps");fix.setLatitude(51.5074);fix.setLongitude(-0.1278);
                fix.setAccuracy(1);fix.setTime(System.currentTimeMillis());fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                manager.setTestProviderLocation("gps",fix);
            }
            a=LiveUiCheck.launch(test);MainActivity shown=a;
            PhoneUiCheck.on(test,()->{
                check(shown.onboarding.step()==0 && !prefs.getBoolean("onboarding-location-asked",false),"Welcome requested location early");
                shown.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick();
                check(shown.onboarding.step()==1 && prefs.getBoolean("onboarding-location-asked",false),"Location entry did not initiate detection automatically");
                check(!shown.phoneUi.settings.value("prayer.background-location").equals("true"),"Automatic background updates opted in without a choice");
            });
            if(granted) {
                LiveUiCheck.untilUi(test,()->!shown.phoneUi.locationUi.locator.running && shown.phoneUi.settings.value("prayer.location").startsWith("detected"));
                check(Math.abs(shown.phoneUi.settings.latitude-51.5074)<0.01,"Automatic setup detection did not use the OS fix");
            } else resolveLocationRequest(test,shown);
            PhoneUiCheck.on(test,()->{
                Switch toggle=shown.body.findViewWithTag("phone.prayer.background-location.edit");
                check(toggle!=null && toggle.isEnabled() && !toggle.isChecked(),"Automatic updates must be visible and optional on the location step");
                check(shown.body.findViewWithTag("phone.country")!=null && shown.body.findViewWithTag("phone.city")!=null,"Manual location fallback is missing");
                shown.render();shown.onboarding.requestLocation();
                check(!shown.phoneUi.locationUi.locationPermissionPending && !shown.phoneUi.locationUi.locator.running,"Rerender repeated automatic detection or permission");
                toggle=shown.body.findViewWithTag("phone.prayer.background-location.edit");toggle.performClick();
            });
            if(!granted)LiveUiCheck.key(test,KeyEvent.KEYCODE_BACK);
            PhoneUiCheck.on(test,()->{
                check(shown.page.equals("onboarding") && shown.onboarding.step()==1,"Optional permission left guided setup");
                shown.body.findViewWithTag("phone.city").performClick();
                ListView cities=shown.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.choice.list");
                cities.performItemClick(null,0,0);
                check(!shown.phoneUi.settings.value("prayer.location").startsWith("detected")
                                && !shown.phoneUi.locationUi.locator.running,"Manual city selection did not take priority over detection");
                check(shown.phoneUi.settings.value("prayer.background-location").equals(Boolean.toString(granted)),"Automatic update choice was lost");
                check(!PhoneBackgroundLocation.enabled(c) && c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB)==null,"Unconfirmed setup scheduled background location");
            });
            PhoneUiCheck.capture(test,shown,granted?"setup-automatic-location":"setup-manual-location",captures);
            PhoneUiCheck.on(test,()->shown.getWindow().getDecorView().findViewWithTag("onboarding.next").performClick());
            resolveNotificationRequest(test,shown);
            PhoneUiCheck.on(test,()->{
                check(shown.phoneUi.settings.locationReady && PhoneBackgroundLocation.enabled(c)==granted,"Confirmation did not apply the explicit automatic update choice");
                check((c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB)!=null)==granted,
                        "Background job did not follow the confirmed automatic update choice");
                shown.back();
                check(shown.onboarding.step()==1 && !shown.phoneUi.locationUi.locationPermissionPending && !shown.phoneUi.locationUi.locator.running,"Returning to location requested detection again");
            });
        } finally {
            if(a!=null){MainActivity close=a;PhoneUiCheck.on(test,close::finish);}
            if(provider)manager.removeTestProvider("gps");
            restore(prefs,original);ui.edit().putString("language",lang).commit();PhoneAlarms.rebuild(c);PhoneBackgroundLocation.sync(c);
        }
    }

    private static void restore(SharedPreferences prefs,Map<String,?> original) {
        SharedPreferences.Editor restore=prefs.edit().clear();
        for(Map.Entry<String,?> entry:original.entrySet()) {
            Object value=entry.getValue();String key=entry.getKey();
            if(value instanceof String)restore.putString(key,(String)value);
            else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
            else if(value instanceof Long)restore.putLong(key,(Long)value);
            else if(value instanceof Integer)restore.putInt(key,(Integer)value);
            else if(value instanceof Float)restore.putFloat(key,(Float)value);
            else if(value instanceof Set)restore.putStringSet(key,new LinkedHashSet<>((Set<String>)value));
            else throw new IllegalStateException("Unknown preference type");
        }
        check(restore.commit(),"Cannot restore journey fixture");
    }

    private static boolean declineNotification(
            android.view.accessibility.AccessibilityNodeInfo node, Set<String> observed) {
        String id = node.getViewIdResourceName();
        if (id != null) observed.add(id);
        // Google builds can retain AOSP resource IDs under a different owning package.
        if (id != null && (id.endsWith(":id/permission_deny_button") || id.endsWith(":id/permission_deny_and_dont_ask_again_button"))
                && node.isVisibleToUser() && node.isEnabled() && node.isClickable()) {
            check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),
                    "Could not decline the system permission request");
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (declineNotification(child, observed)) return true;
            } finally { child.recycle(); }
        }
        return false;
    }

    private static void supportedPlatforms(Instrumentation test, MainActivity a) {
        PhoneUiCheck.on(test, () -> {
            check(a.page.equals("tv-setup"), "Setup and pairing must share the platform chooser");
            check(a.body.findViewWithTag("setup.platform.android") != null
                            && a.body.findViewWithTag("setup.platform.lg") != null,
                    "Supported TV setup entries missing");
            for (String id : new String[] {"tizen", "roku", "other"})
                check(a.body.findViewWithTag("setup.platform." + id) == null,
                        "Unsupported TV setup entry offered: " + id);
            check(!a.networkBusy, "Opening setup started networking");
        });
    }

    private static void cancelledResult(Instrumentation test, MainActivity a) throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1),
                release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean published =
                new java.util.concurrent.atomic.AtomicBoolean();
        String page = a.page;
        PhoneUiCheck.on(
                test,
                () ->
                        a.network(
                                () -> {
                                    entered.countDown();
                                    try {
                                        release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (InterruptedException cancelled) {
                                        /* Some providers can still return a late result after cancellation. */
                                    }
                                    return true;
                                },
                                done -> published.set(true),
                                () -> published.set(true)));
        check(
                entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "Cancellation fixture never started");
        PhoneUiCheck.on(
                test,
                () -> {
                    a.cancelNetwork();
                    a.render();
                });
        release.countDown();
        a.io.shutdown();
        check(
                a.io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS),
                "Cancelled provider did not finish");
        test.waitForIdleSync();
        check(
                !published.get() && !a.networkBusy && a.page.equals(page),
                "A cancelled result changed the current journey");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
