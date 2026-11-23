package tv.miqat.home;

import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import tv.miqat.companion.about.AboutMiqat;
import tv.miqat.companion.qr.QrImage;

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
            qr.setImageBitmap(QrImage.create(this, AboutMiqat.FEEDBACK_URL, 800, new int[] {
                    Palette.LAPIS_900, Palette.LAPIS_700, Palette.GOLD_400, Palette.PLASTER_100, Tokens.PLASTER_100}));
            placeEnd(qr, start, 328, 450 / scale, 450 / scale);
        } catch (Exception failure) {
            Log.w("MiqatAbout", "Feedback QR is unavailable", failure);
            feedback.setText(AboutMiqat.FEEDBACK_URL);
        }

        FocusTile back = new FocusTile(this, Tokens.RADIUS_PANEL);
        TextView backLabel = text(ui500, 28, Palette.PLASTER_100);
        backLabel.setText(t.back());
        backLabel.setGravity(Gravity.CENTER);
        back.addView(backLabel, new FrameLayout.LayoutParams(-1, -1));
        back.setLook(focused -> backLabel.setTextColor(focused ? Palette.LAPIS_900 : Palette.PLASTER_100));
        back.setOnClickListener(view -> finish());
        placeTile(back, start, 942, 280, 64);
        setContentView(root);
        back.requestFocus();
    }

    private TextView label(String value, int size, int color, float x, int y, float width, int height) {
        TextView label = text(ui500, size, color);
        label.setText(value);
        place(label, x, y, width, height);
        return label;
    }

    @Override void onTick() {}
}
