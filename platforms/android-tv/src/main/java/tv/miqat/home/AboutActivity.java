package tv.miqat.home;

import android.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import tv.miqat.companion.about.AboutMiqat;
import tv.miqat.companion.about.AboutUpdates;
import tv.miqat.companion.qr.QrImage;
import tv.miqat.core.UiContract;

public final class AboutActivity extends Screen {
    @Override void build() {
        newRoot();
        float start = Tokens.SPACE_SAFE_INLINE;
        float scale = Math.max(1, Ui.factor(this));
        label(t.aboutMiqat(), 60, Palette.PLASTER_100, start, 64, 1450 / scale, 105);
        label(t.aboutVersion() + " \u2066" + AboutMiqat.version(this) + "\u2069",
                36, Palette.PLASTER_100, start, 214, 1020 / scale, 70).setTag("about.version");
        label(t.aboutDeveloper(), 32, Palette.PLASTER_100, start, 312, 1020 / scale, 90);
        label(t.aboutSadaqah(), 28, Palette.PLASTER_100, start, 415, 1020 / scale, 180);
        TextView email = label(t.aboutEmail(), 28, Palette.MIST_400, start, 655, 1020 / scale, 108);
        email.setTag("about.email");
        email.setEnabled(false);

        TextView feedback = text(ui500, 28, Palette.PLASTER_100);
        feedback.setText(t.aboutGithub());
        feedback.setGravity(Gravity.CENTER);
        placeEnd(feedback, start, 214, 450 / scale, 90);
        ImageView qr = new ImageView(this);
        qr.setTag("about.github");
        qr.setContentDescription(t.aboutGithub());
        try {
            drawQr(qr, AboutMiqat.FEEDBACK_URL);
            placeEnd(qr, start, 328, 450 / scale, 450 / scale);
        } catch (Exception failure) {
            Log.w("MiqatAbout", "Feedback QR is unavailable", failure);
            feedback.setText(AboutMiqat.FEEDBACK_URL);
        }

        if (AboutUpdates.available(this)) {
            UiContract.INSTANCE.bind("about.check-updates", "action");
            FocusTile check = action(t.aboutCheckUpdates(), this::checkForUpdates);
            check.setTag("about.check-updates");
            placeTile(check, start, 795, 670 / scale, 76);
        }
        FocusTile back = action(t.back(), this::finish);
        placeTile(back, start, 942, 280, 64);
        setContentView(root);
        back.requestFocus();
    }

    private FocusTile action(String title, Runnable run) {
        FocusTile tile = new FocusTile(this, Tokens.RADIUS_PANEL);
        TextView label = text(ui500, 28, Palette.PLASTER_100);
        label.setText(title);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
        tile.setLook(focused -> label.setTextColor(focused ? Palette.LAPIS_900 : Palette.PLASTER_100));
        tile.setOnClickListener(view -> run.run());
        return tile;
    }

    private void checkForUpdates() {
        AboutUpdates.show(this, new AlertDialog.Builder(this), rtl, new AboutUpdates.Labels(t.aboutCheckUpdates(), t.aboutCheckingUpdates(),
                t.aboutNoUpdate(), t.aboutNewUpdate(), t.aboutUpdateFailed(), t.aboutViewRelease(), t.back()),
                this::showReleaseQr);
    }

    private void showReleaseQr(String url) {
        AlertDialog.Builder release = new AlertDialog.Builder(this)
                .setTitle(t.aboutViewRelease()).setPositiveButton(t.back(), null);
        try {
            ImageView image = new ImageView(this);
            image.setContentDescription(t.aboutViewRelease());
            drawQr(image, url);
            FrameLayout layout = new FrameLayout(this);
            int size = Math.min(Ui.ipx(360), getResources().getDisplayMetrics().widthPixels / 4);
            layout.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            release.setView(layout);
        } catch (Exception failure) {
            Log.w("MiqatAbout", "Release QR is unavailable", failure);
            release.setMessage(url);
        }
        release.show().getWindow().getDecorView()
                .setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    private void drawQr(ImageView image, String url) throws Exception {
        image.setImageBitmap(QrImage.create(this, url, 800, new int[] {
                Palette.LAPIS_900, Palette.LAPIS_700, Palette.GOLD_400, Palette.PLASTER_100, Tokens.PLASTER_100}));
    }

    private TextView label(String value, int size, int color, float x, int y, float width, int height) {
        TextView label = text(ui500, size, color);
        label.setText(value);
        place(label, x, y, width, height);
        return label;
    }

    @Override void onTick() {}
}
