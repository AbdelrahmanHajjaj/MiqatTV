package tv.miqat.companion;

import java.util.*;

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
            double t = i / (double) (result.length - 1);
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

    static String html(
            double latitude, double longitude, int background, int foreground, String kaaba) {
        double[][] points = route(latitude, longitude);
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        int zoom = 18;
        while (zoom > 0 && Math.max(maxX - minX, maxY - minY) * (256 << zoom) > 400) zoom--;
        int world = 256 << zoom, count = 1 << zoom;
        double left = (minX + maxX) / 2 * world - 256;
        double top = (minY + maxY) / 2 * world - 256;
        StringBuilder html =
                new StringBuilder(
                        "<!doctype html><html><head><meta name='viewport' content='width=512'><meta"
                            + " http-equiv='Content-Security-Policy' content=\"default-src 'none';"
                            + " img-src https://tile.openstreetmap.org; style-src"
                            + " 'unsafe-inline'\"><style>html,body{padding:0;margin:0;background:"
                                + color(background)
                                + ";color:"
                                + color(foreground)
                                + ";font:16px"
                                + " sans-serif}#map{position:relative;width:512px;height:512px;overflow:hidden;background:#e9e8df}"
                                + "img,svg{position:absolute}a{color:#173b3e;background:white;padding:4px;text-decoration:underline}#credit{position:absolute;bottom:0;right:0;font-size:15px}#north{position:absolute;top:12px;left:12px;background:white;color:#173b3e;padding:5px}</style></head><body><div"
                                + " id='map'>");
        int firstX = (int) Math.floor(left / 256), lastX = (int) Math.floor((left + 511) / 256);
        int firstY = Math.max(0, (int) Math.floor(top / 256));
        int lastY = Math.min(count - 1, (int) Math.floor((top + 511) / 256));
        for (int y = firstY; y <= lastY; y++)
            for (int x = firstX; x <= lastX; x++) {
                int tileX = (x % count + count) % count;
                html.append(
                                "<img alt='' width='256' height='256'"
                                    + " src='https://tile.openstreetmap.org/")
                        .append(zoom)
                        .append('/')
                        .append(tileX)
                        .append('/')
                        .append(y)
                        .append(".png' style='left:")
                        .append(number(x * 256 - left))
                        .append("px;top:")
                        .append(number(y * 256 - top))
                        .append("px'>");
            }
        StringBuilder path = new StringBuilder();
        for (double[] p : points)
            path.append(number(p[0] * world - left))
                    .append(',')
                    .append(number(p[1] * world - top))
                    .append(' ');
        html.append(
                        "<svg width='512' height='512' viewBox='0 0 512 512'"
                                + " aria-label='Qibla'><polyline points='")
                .append(path)
                .append(
                        "' fill='none' stroke='white' stroke-width='9'></polyline><polyline"
                            + " points='")
                .append(path)
                .append("' fill='none' stroke='#0A6060' stroke-width='5'></polyline>");
        for (int index : new int[] {0, points.length - 1}) {
            double[] p = points[index];
            html.append("<circle cx='")
                    .append(number(p[0] * world - left))
                    .append("' cy='")
                    .append(number(p[1] * world - top))
                    .append("' r='9' fill='#173b3e' stroke='white' stroke-width='3'></circle>");
        }
        double[] destination = points[points.length - 1];
        html.append("<text x='")
                .append(number(destination[0] * world - left))
                .append("' y='")
                .append(number(destination[1] * world - top + 30))
                .append(
                        "' text-anchor='middle' fill='#173b3e' stroke='white' stroke-width='3'"
                                + " paint-order='stroke' font-size='20'>")
                .append(kaaba.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("</text>");
        return html.append(
                        "</svg><span id='north'>N ↑</span><a id='credit'"
                                + " href='https://www.openstreetmap.org/copyright'>© OpenStreetMap"
                                + " contributors</a></div></body></html>")
                .toString();
    }

    private static String number(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String color(int value) {
        return String.format(Locale.US, "#%06x", value & 0xffffff);
    }
}
