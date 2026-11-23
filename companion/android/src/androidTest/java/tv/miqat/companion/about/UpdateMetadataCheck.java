package tv.miqat.companion.about;

import java.io.IOException;
import org.json.JSONException;

public final class UpdateMetadataCheck {
    private UpdateMetadataCheck() {}

    public static void run() throws Exception {
        String stable = "{\"tag\":\"v0.7.6\",\"android\":{"
                + "\"tv.miqat.companion\":{\"versionCode\":38,\"versionName\":\"0.5.22\"},"
                + "\"tv.miqat.home\":{\"versionCode\":15,\"versionName\":\"0.7.4\"}}}";
        AboutUpdates.Result phone = AboutUpdates.read(stable, "tv.miqat.companion");
        AboutUpdates.Result tv = AboutUpdates.read(stable, "tv.miqat.home");
        if (phone.code() != 38 || tv.code() != 15 || !phone.version().equals("0.5.22")
                || !phone.url().equals("https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/tag/v0.7.6")) {
            throw new AssertionError("One stable release must supply independent Android versions");
        }
        reject(stable.replace("v0.7.6", "v0.7.6-preview.1"), "tv.miqat.companion");
        reject(stable.replace(":38", ":38.5"), "tv.miqat.companion");
        reject(stable, "tv.miqat.webos");
    }

    private static void reject(String data, String appId) throws Exception {
        try {
            AboutUpdates.read(data, appId);
        } catch (IOException | JSONException expected) {
            return;
        }
        throw new AssertionError("Accepted invalid stable update metadata");
    }
}
