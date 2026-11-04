package com.simple.carlauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight in-app map: OpenStreetMap raster tiles served by CARTO, drawn directly on a
 * canvas. Dark or light basemap follows the launcher theme and the marker uses the accent
 * colour the user picked, so it reacts to the system colours.
 */
public class MapView extends View implements LocationListener {

    private static final int TILE = 256;   // slippy-map base tile
    private static final int SCALE = 2;    // @2x raster
    private static final int MIN_ZOOM = 11, MAX_ZOOM = 18;
    private static final double DEFAULT_LAT = -0.1807, DEFAULT_LON = -78.4678; // Quito

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accuracy = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path arrow = new Path();
    private final RectF r = new RectF();

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(48) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return 1;
        }
    };
    private final Set<String> loading = new HashSet<>();
    private final Map<String, Long> failed = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    private double centerLat = DEFAULT_LAT, centerLon = DEFAULT_LON;
    private int zoom = 15;
    private boolean following = true;

    private double myLat = Double.NaN, myLon = Double.NaN;
    private float myHeading, mySpeed, myAccuracy;

    private float downX, downY, lastX, lastY;
    private boolean dragging;
    private long lastTap;
    private float lastTapX, lastTapY;
    private final int slop;

    private LocationManager lm;
    private boolean started;
    private boolean paused;
    private final float d;

    public MapView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setLayerType(LAYER_TYPE_HARDWARE, null);
        text.setTextSize(11 * d);
        text.setShadowLayer(3 * d, 0, 0, 0xAA000000);
        accuracy.setStyle(Paint.Style.FILL);
        marker.setStyle(Paint.Style.FILL);
        markerRing.setStyle(Paint.Style.STROKE);
        markerRing.setStrokeWidth(3 * d);
        markerRing.setColor(0xFFFFFFFF);
        updateColors();
    }

    private void updateColors() {
        int accent = Accent.color(getContext());
        marker.setColor(accent);
        accuracy.setColor((accent & 0x00FFFFFF) | 0x33000000);
        invalidate();
    }

    private String style() {
        return Ui.isDark(getContext()) ? "dark_all" : "light_all";
    }

    // ---------- Map maths ----------

    private double worldSize() {
        return TILE * (double) (1 << zoom);
    }

    private double lonToX(double lon) {
        return (lon + 180) / 360 * worldSize();
    }

    private double latToY(double lat) {
        double s = Math.sin(Math.toRadians(lat));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * worldSize();
    }

    private double xToLon(double x) {
        double lon = x / worldSize() * 360 - 180;
        return ((lon + 180) % 360 + 360) % 360 - 180;
    }

    private double yToLat(double y) {
        double n = Math.PI - 2 * Math.PI * y / worldSize();
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }

    // ---------- Drawing ----------

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        boolean dark = Ui.isDark(getContext());
        c.drawColor(dark ? 0xFF0B0B10 : 0xFFE8EAF0);
        if (paused) return;

        double cx = lonToX(centerLon), cy = latToY(centerLat);
        double halfW = w / (2.0 * SCALE), halfH = h / (2.0 * SCALE);
        int minX = (int) Math.floor((cx - halfW) / TILE);
        int maxX = (int) Math.floor((cx + halfW) / TILE);
        int minY = (int) Math.floor((cy - halfH) / TILE);
        int maxY = (int) Math.floor((cy + halfH) / TILE);
        int n = 1 << zoom;

        fill.setColor(dark ? 0xFF14141C : 0xFFDDE1EA);
        for (int ty = minY; ty <= maxY; ty++) {
            if (ty < 0 || ty >= n) continue;
            for (int tx = minX; tx <= maxX; tx++) {
                int wx = ((tx % n) + n) % n;
                float sx = (float) ((tx * (double) TILE - cx) * SCALE + w / 2.0);
                float sy = (float) ((ty * (double) TILE - cy) * SCALE + h / 2.0);
                String key = style() + "/" + zoom + "/" + wx + "/" + ty;
                Bitmap b = cache.get(key);
                if (b != null) {
                    r.set(sx, sy, sx + TILE * SCALE, sy + TILE * SCALE);
                    c.drawBitmap(b, null, r, tilePaint);
                } else {
                    c.drawRect(sx, sy, sx + TILE * SCALE, sy + TILE * SCALE, fill);
                    load(key, style(), zoom, wx, ty);
                }
            }
        }

        drawMe(c, w, h, cx, cy);

        String credit = "© OpenStreetMap · CARTO";
        text.setColor(dark ? 0xB3FFFFFF : 0xB3000000);
        text.setShadowLayer(3 * d, 0, 0, dark ? 0xAA000000 : 0x66FFFFFF);
        c.drawText(credit, w - text.measureText(credit) - 8 * d, h - 8 * d, text);
    }

    private void drawMe(Canvas c, int w, int h, double cx, double cy) {
        if (Double.isNaN(myLat)) return;
        float sx = (float) ((lonToX(myLon) - cx) * SCALE + w / 2.0);
        float sy = (float) ((latToY(myLat) - cy) * SCALE + h / 2.0);
        if (myAccuracy > 0) {
            double metersPerPx = 156543.03392 * Math.cos(Math.toRadians(myLat)) / (1 << zoom);
            c.drawCircle(sx, sy, (float) (myAccuracy / metersPerPx * SCALE), accuracy);
        }
        c.drawCircle(sx, sy, 11 * d, markerRing);
        c.drawCircle(sx, sy, 8 * d, marker);
        c.save();
        c.rotate(myHeading, sx, sy);
        arrow.reset();
        arrow.moveTo(sx, sy - 18 * d);
        arrow.lineTo(sx - 6 * d, sy - 9 * d);
        arrow.lineTo(sx + 6 * d, sy - 9 * d);
        arrow.close();
        c.drawPath(arrow, marker);
        c.restore();
    }

    // ---------- Tiles ----------

    private void load(final String key, final String style, final int z, final int x, final int y) {
        if (cache.get(key) != null) return;
        Long fail = failed.get(key);
        if (fail != null && SystemClock.elapsedRealtime() - fail < 60_000) return;
        if (!loading.add(key)) return;
        pool.submit(() -> {
            Bitmap b = fromDisk(style, z, x, y);
            if (b == null) b = download(style, z, x, y);
            final Bitmap result = b;
            post(() -> {
                loading.remove(key);
                if (result != null) {
                    cache.put(key, result);
                    failed.remove(key);
                    invalidate();
                } else {
                    failed.put(key, SystemClock.elapsedRealtime());
                }
            });
        });
    }

    private File tileFile(String style, int z, int x, int y) {
        return new File(new File(getContext().getCacheDir(),
                "map/" + style + "/" + z + "/" + x), y + ".png");
    }

    private Bitmap fromDisk(String style, int z, int x, int y) {
        File f = tileFile(style, z, x, y);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    private Bitmap download(String style, int z, int x, int y) {
        HttpURLConnection conn = null;
        try {
            char host = "abcd".charAt((x + y) & 3);
            URL url = new URL("https://" + host + ".basemaps.cartocdn.com/"
                    + style + "/" + z + "/" + x + "/" + y + "@2x.png");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "CleanLauncher/1.0 (car head unit)");
            if (conn.getResponseCode() != 200) return null;
            InputStream in = conn.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            if (b != null) {
                File f = tileFile(style, z, x, y);
                File dir = f.getParentFile();
                if (dir != null && (dir.exists() || dir.mkdirs())) {
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        b.compress(Bitmap.CompressFormat.PNG, 90, out);
                    } catch (Exception ignored) { }
                }
            }
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Deletes tiles older than a month; called once when the map is opened. */
    public void pruneDisk() {
        pool.submit(() -> {
            File root = new File(getContext().getCacheDir(), "map");
            long cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
            prune(root, cutoff, 0);
        });
    }

    private void prune(File f, long cutoff, int depth) {
        if (f == null || !f.exists() || depth > 4) return;
        File[] list = f.listFiles();
        if (list == null) return;
        for (File x : list) {
            if (x.isDirectory()) prune(x, cutoff, depth + 1);
            else if (x.lastModified() < cutoff) //noinspection ResultOfMethodCallIgnored
                x.delete();
        }
    }

    // ---------- Touch ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = lastX - e.getX();
                float dy = lastY - e.getY();
                lastX = e.getX();
                lastY = e.getY();
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) dragging = true;
                if (dragging) {
                    following = false;
                    pan(dx, dy);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap < 300
                            && Math.abs(e.getX() - lastTapX) < 40 * d
                            && Math.abs(e.getY() - lastTapY) < 40 * d) {
                        zoomIn();
                    }
                    lastTap = now;
                    lastTapX = e.getX();
                    lastTapY = e.getY();
                }
                dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void pan(float dx, float dy) {
        double cx = lonToX(centerLon) - dx / (double) SCALE;
        double cy = latToY(centerLat) - dy / (double) SCALE;
        double ws = worldSize();
        cy = Math.max(0, Math.min(ws, cy));
        centerLon = xToLon(cx);
        centerLat = yToLat(cy);
        invalidate();
    }

    // ---------- Public API ----------

    public void setZoom(int z) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        invalidate();
    }

    public int zoom() {
        return zoom;
    }

    public void zoomIn() {
        setZoom(zoom + 1);
    }

    public void zoomOut() {
        setZoom(zoom - 1);
    }

    public void centerOn(double lat, double lon) {
        centerLat = lat;
        centerLon = lon;
        following = true;
        invalidate();
    }

    public void centerOnMe() {
        if (!Double.isNaN(myLat)) centerOn(myLat, myLon);
    }

    public boolean hasFix() {
        return !Double.isNaN(myLat);
    }

    public double speedKmh() {
        return mySpeed * 3.6;
    }

    public double lat() {
        return centerLat;
    }

    public double lon() {
        return centerLon;
    }

    public void start() {
        if (started) return;
        started = true;
        Context c = getContext();
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        try {
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) onLocationChanged(last);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3, this);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this);
            }
        } catch (Exception ignored) { }
        pruneDisk();
    }

    public void stop() {
        started = false;
        if (lm != null) {
            try {
                lm.removeUpdates(this);
            } catch (Exception ignored) { }
        }
        lm = null;
    }

    /** Off-screen pages pause tile loading and GPS. */
    public void setPaused(boolean p) {
        if (paused == p) return;
        paused = p;
        if (p) stop(); else start();
        invalidate();
    }

    @Override
    public void onLocationChanged(Location l) {
        myLat = l.getLatitude();
        myLon = l.getLongitude();
        myHeading = l.hasBearing() ? l.getBearing() : 0;
        mySpeed = l.hasSpeed() ? l.getSpeed() : 0;
        myAccuracy = l.hasAccuracy() ? l.getAccuracy() : 0;
        if (following) {
            centerLat = myLat;
            centerLon = myLon;
        }
        invalidate();
    }

    public void onStatusChanged(String p, int s, Bundle b) { }

    public void onProviderEnabled(String p) { }

    public void onProviderDisabled(String p) { }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
