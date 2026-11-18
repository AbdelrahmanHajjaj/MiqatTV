package tv.miqat.companion;

/** Host the shared installer with the companion's selected language. */
public final class SetupActivity extends tv.miqat.installer.MainActivity {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(MainActivity.localized(base));
    }
}
