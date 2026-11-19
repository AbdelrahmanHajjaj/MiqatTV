package tv.miqat.companion;

import android.graphics.*;
import android.hardware.*;
import android.view.*;
import android.widget.TextView;

import tv.miqat.core.QiblaRules;

/** Foreground compass. No location tracking or sensor service survives the screen. */
final class QiblaUi implements SensorEventListener {
    private final MainActivity a;
    private final SensorManager sensors;
    private final float[] rotation = new float[9], screen = new float[9], angles = new float[3];
    private Compass compass;
    private TextView status;
    private double bearing;
    private float declination;
    private boolean registered;

    QiblaUi(MainActivity activity) {
        a = activity;
        sensors = a.getSystemService(SensorManager.class);
    }

    void render() {
        stop();
        if (a.phoneUi.settings == null || !a.phoneUi.settings.locationReady) {
            a.body.addView(a.views.text(a.t("phone_location_needed"), 16, a.ink));
            a.views.button(
                    a.body, a.t("phone_choose_location"), () -> a.phoneUi.open("calculation"));
            return;
        }
        PhoneStore settings = a.phoneUi.settings;
        Double target = QiblaRules.INSTANCE.bearing(settings.latitude, settings.longitude);
        if (target == null) {
            a.body.addView(a.views.text(a.t("qibla_at_kaaba"), 16, a.ink));
            return;
        }
        bearing = target;
        a.body.addView(
                a.views.text(
                        a.t("qibla_bearing")
                                + " · "
                                + a.phoneUi.digits(
                                        String.format(java.util.Locale.US, "%.1f°", bearing)),
                        20,
                        a.accent));
        try {
            a.body.addView(a.views.text(a.phoneUi.location(), 14, a.muted));
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_current_location"), 14, a.muted));
        }
        compass = new Compass();
        compass.setVisibility(View.GONE);
        a.body.addView(compass);
        status = a.views.text(a.t("qibla_wait"), 15, a.ink);
        status.setTag("qibla.status");
        a.body.addView(status);
        a.body.addView(a.views.text(a.t("qibla_hint"), 15, a.muted));
        declination =
                new GeomagneticField(
                                (float) settings.latitude,
                                (float) settings.longitude,
                                0,
                                System.currentTimeMillis())
                        .getDeclination();
        Sensor sensor =
                sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null && sensors != null)
            sensor = sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        registered =
                sensor != null
                        && sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        if (!registered) status.setText(a.t("qibla_no_sensor"));
    }

    void stop() {
        if (registered && sensors != null) sensors.unregisterListener(this);
        registered = false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (!registered) return;
        if (status != null) {
            status.setText(
                    accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                            ? a.t("qibla_accuracy")
                            : a.t("qibla_ready"));
            if (compass != null)
                compass.setAlpha(
                        accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ? .35f : 1f);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!registered || !a.phoneForeground || !a.page.equals("qibla")) return;
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        int display = a.getWindowManager().getDefaultDisplay().getRotation();
        int x =
                display == Surface.ROTATION_90
                        ? SensorManager.AXIS_Y
                        : display == Surface.ROTATION_180
                                ? SensorManager.AXIS_MINUS_X
                                : display == Surface.ROTATION_270
                                        ? SensorManager.AXIS_MINUS_Y
                                        : SensorManager.AXIS_X;
        int y =
                display == Surface.ROTATION_90
                        ? SensorManager.AXIS_MINUS_X
                        : display == Surface.ROTATION_180
                                ? SensorManager.AXIS_MINUS_Y
                                : display == Surface.ROTATION_270
                                        ? SensorManager.AXIS_X
                                        : SensorManager.AXIS_Y;
        if (!SensorManager.remapCoordinateSystem(rotation, x, y, screen)) return;
        SensorManager.getOrientation(screen, angles);
        double north = Math.toDegrees(angles[0]) + declination;
        compass.direction = (float) ((bearing - north + 720) % 360);
        compass.setVisibility(View.VISIBLE);
        compass.invalidate();
        onAccuracyChanged(event.sensor, event.accuracy);
        compass.setContentDescription(
                String.format(
                        a.phoneUi.locale(),
                        a.t("qibla_direction"),
                        a.phoneUi.digits(
                                String.format(java.util.Locale.US, "%.0f", compass.direction))));
    }

    private final class Compass extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        float direction;

        Compass() {
            super(a);
            setTag("qibla.compass");
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        @Override
        protected void onMeasure(int width, int height) {
            int size = Math.min(MeasureSpec.getSize(width), a.views.dp(280));
            setMeasuredDimension(resolveSize(size, width), resolveSize(size, height));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f,
                    cy = getHeight() / 2f,
                    r = Math.min(cx, cy) - a.views.dp(18);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(
                    new RadialGradient(
                            cx,
                            cy,
                            r,
                            new int[] {a.surface, a.ground},
                            null,
                            Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(a.views.dp(1));
            paint.setColor((a.accent & 0xffffff) | 0x55000000);
            canvas.drawCircle(cx, cy, r * .98f, paint);
            canvas.drawCircle(cx, cy, r * .73f, paint);
            for (int angle = 0; angle < 360; angle += 5) {
                int tick = canvas.save();
                canvas.rotate(angle, cx, cy);
                paint.setColor(angle % 30 == 0 ? a.accent : (a.muted & 0xffffff) | 0x55000000);
                canvas.drawLine(
                        cx, cy - r * .89f, cx, cy - r * (angle % 30 == 0 ? .8f : .85f), paint);
                canvas.restoreToCount(tick);
            }
            int save = canvas.save();
            canvas.rotate(direction, cx, cy);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((a.accent & 0xffffff) | 0x18000000);
            arrow.reset();
            arrow.moveTo(cx, cy - r * .65f);
            arrow.lineTo(cx + r * .23f, cy + r * .25f);
            arrow.lineTo(cx - r * .23f, cy + r * .25f);
            arrow.close();
            canvas.drawPath(arrow, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(a.views.dp(2));
            paint.setColor(a.accent);
            canvas.drawLine(cx, cy + r * .22f, cx, cy - r * .4f, paint);
            paint.setStyle(Paint.Style.FILL);
            float k = r * .17f, top = cy - r * .62f;
            paint.setColor(a.accent);
            canvas.drawRoundRect(
                    cx - k - 3, top - k - 3, cx + k + 3, top + k + 3, k * .18f, k * .18f, paint);
            paint.setColor(0xff171b1c);
            canvas.drawRoundRect(cx - k, top - k, cx + k, top + k, k * .12f, k * .12f, paint);
            paint.setColor(a.accent);
            canvas.drawRect(cx - k, top - k * .5f, cx + k, top - k * .2f, paint);
            canvas.drawRect(cx + k * .35f, top + k * .2f, cx + k * .65f, top + k, paint);
            canvas.drawCircle(cx, cy, a.views.dp(5), paint);
            canvas.restoreToCount(save);
        }
    }
}
