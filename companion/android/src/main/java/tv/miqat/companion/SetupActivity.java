package tv.miqat.companion;

/** Host the shared installer with the companion's selected language. */
public final class SetupActivity extends tv.miqat.installer.MainActivity {
    @Override public boolean motionEnabled(){try{return super.motionEnabled()&&!PhoneStore.read(this).value("display.reduce-motion").equals("true");}catch(Exception unavailable){return false;}}
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(MainActivity.localized(base));
    }
}
