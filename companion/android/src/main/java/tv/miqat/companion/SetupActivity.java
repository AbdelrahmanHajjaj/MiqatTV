package tv.miqat.companion;

/** Host the shared installer with the companion's selected language. */
public final class SetupActivity extends tv.miqat.installer.MainActivity {
    @Override protected tv.miqat.installer.InstallerPalette createPalette() {
        try {
            org.json.JSONObject colors = new Catalog(this).data.getJSONObject("themes")
                    .getJSONObject(PhoneStore.read(this).value("appearance.theme"))
                    .getJSONObject("colors");
            return new tv.miqat.installer.InstallerPalette(
                    color(colors, "lapis-900"), color(colors, "lapis-800"), color(colors, "lapis-700"),
                    color(colors, "gold-400"), color(colors, "gold-200"),
                    color(colors, "plaster-100"), color(colors, "mist-400"));
        } catch (Exception invalidPreferences) {
            return super.createPalette();
        }
    }

    private static int color(org.json.JSONObject colors, String key) throws org.json.JSONException {
        return android.graphics.Color.parseColor(colors.getString(key));
    }

    @Override public boolean motionEnabled(){try{return super.motionEnabled()&&!PhoneStore.read(this).value("display.reduce-motion").equals("true");}catch(Exception unavailable){return false;}}
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(MainActivity.localized(base));
    }
}
