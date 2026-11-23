package tv.miqat.companion;

import android.content.Context;

import java.util.Scanner;
import org.json.JSONArray;

/** A bounded north-up viewport of the actual great-circle path; no network or device access. */
final class QiblaMap {
    private QiblaMap() {}

    private static final double LATITUDE = Math.toRadians(21.4225241);
    private static final double LONGITUDE = Math.toRadians(39.8261818);

    static double[][] route(double latitude, double longitude) {
        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180) throw new IllegalArgumentException("Invalid map coordinates");
        double lat = Math.toRadians(latitude), lon = Math.toRadians(longitude);
        double[] start = vector(lat, lon), end = vector(LATITUDE, LONGITUDE);
        double angle =
                Math.acos(
                        Math.max(
                                -1,
                                Math.min(
                                        1,
                                        start[0] * end[0]
                                                + start[1] * end[1]
                                                + start[2] * end[2])));
        if (Math.abs(Math.sin(angle)) < 0.000001)
            throw new IllegalArgumentException("Qibla route is undefined here");
        double[][] result = new double[65][2];
        double previous = (longitude + 180) / 360;
        for (int i = 0; i < result.length; i++) {
            double t = (1 - Math.cos(Math.PI * i / (result.length - 1))) / 2;
            double[] v = new double[3];
            for (int j = 0; j < 3; j++)
                v[j] =
                        (Math.sin((1 - t) * angle) * start[j] + Math.sin(t * angle) * end[j])
                                / Math.sin(angle);
            double x = (Math.atan2(v[1], v[0]) / Math.PI + 1) / 2;
            while (x - previous > .5) x--;
            while (x - previous < -.5) x++;
            previous = x;
            double projectedLat =
                    Math.max(
                            Math.toRadians(-85.05112878),
                            Math.min(
                                    Math.toRadians(85.05112878),
                                    Math.atan2(v[2], Math.hypot(v[0], v[1]))));
            result[i][0] = x;
            result[i][1] =
                    (1 - Math.log(Math.tan(projectedLat) + 1 / Math.cos(projectedLat)) / Math.PI)
                            / 2;
        }
        return result;
    }

    private static double[] vector(double lat, double lon) {
        return new double[] {
            Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)
        };
    }

    static String html(Context context, double latitude, double longitude,
            String kaaba, String zoomIn, String zoomOut, String recenter) {
        try (Scanner input = new Scanner(context.getResources().openRawResource(R.raw.qibla_map),
                "UTF-8")) {
            JSONArray points = new JSONArray();
            for (double[] point : route(latitude, longitude))
                points.put(new JSONArray().put(point[0]).put(point[1]));
            return input.useDelimiter("\\A").next()
                    .replace("%%ROUTE%%", points.toString())
                    .replace("%%KAABA%%", escape(kaaba))
                    .replace("%%ZOOM_IN%%", escape(zoomIn))
                    .replace("%%ZOOM_OUT%%", escape(zoomOut))
                    .replace("%%RECENTER%%", escape(recenter));
        } catch (org.json.JSONException invalid) {
            throw new IllegalArgumentException("Invalid Qibla route", invalid);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(
                ">", "&gt;").replace("\"", "&quot;");
    }
}
