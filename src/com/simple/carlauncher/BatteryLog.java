package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Voltage history: one sample per minute, kept for a week. The live reading is stored
 * separately so the UI can poll it every second without touching the history file.
 */
final class BatteryLog {

    static final class Sample {
        final long minute;
        double value;

        Sample(long minute, double value) {
            this.minute = minute;
            this.value = value;
        }
    }

    private static final String PREFS = "battery";
    private static final String LIVE = "live";
    private static final String LIVE_AT = "live_at";
    private static final int KEEP_MINUTES = 7 * 24 * 60;
    private static long lastLiveAt;
    private static float lastLive;

    private BatteryLog() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "battery.csv");
    }

    /** Live value shown on the home screen; throttled so it does not hit flash every second. */
    static void live(Context c, double v) {
        if (Double.isNaN(v)) return;
        long now = System.currentTimeMillis();
        if (now - lastLiveAt < 10_000 && Math.abs(v - lastLive) < 0.1) return;
        lastLiveAt = now;
        lastLive = (float) v;
        prefs(c).edit()
                .putFloat(LIVE, (float) v)
                .putLong(LIVE_AT, now)
                .apply();
    }

    static double liveValue(Context c) {
        return prefs(c).getFloat(LIVE, Float.NaN);
    }

    static long liveAt(Context c) {
        return prefs(c).getLong(LIVE_AT, 0);
    }

    /** One history sample per minute (a second call in the same minute replaces it). */
    static synchronized void sample(Context c, double v) {
        if (Double.isNaN(v)) return;
        long minute = System.currentTimeMillis() / 60000L;
        List<Sample> all = read(c);
        if (!all.isEmpty() && all.get(all.size() - 1).minute == minute) {
            all.get(all.size() - 1).value = v;
        } else {
            all.add(new Sample(minute, v));
        }
        long cutoff = minute - KEEP_MINUTES;
        int drop = 0;
        while (drop < all.size() && all.get(drop).minute < cutoff) drop++;
        if (drop > 0) all = new ArrayList<>(all.subList(drop, all.size()));
        write(c, all);
    }

    /** Samples from the last {@code hours} hours. */
    static synchronized List<Sample> since(Context c, int hours) {
        long cutoff = System.currentTimeMillis() / 60000L - hours * 60L;
        List<Sample> out = new ArrayList<>();
        for (Sample s : read(c)) if (s.minute >= cutoff) out.add(s);
        return out;
    }

    static synchronized void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
        prefs(c).edit().remove(LIVE).remove(LIVE_AT).apply();
    }

    static double min(List<Sample> s) {
        double v = Double.NaN;
        for (Sample x : s) if (Double.isNaN(v) || x.value < v) v = x.value;
        return v;
    }

    static double max(List<Sample> s) {
        double v = Double.NaN;
        for (Sample x : s) if (Double.isNaN(v) || x.value > v) v = x.value;
        return v;
    }

    static double avg(List<Sample> s) {
        if (s.isEmpty()) return Double.NaN;
        double sum = 0;
        for (Sample x : s) sum += x.value;
        return sum / s.size();
    }

    // ---------- file ----------

    private static List<Sample> read(Context c) {
        List<Sample> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                try {
                    out.add(new Sample(Long.parseLong(line.substring(0, comma).trim()),
                            Double.parseDouble(line.substring(comma + 1).trim())));
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void write(Context c, List<Sample> samples) {
        try (FileWriter w = new FileWriter(file(c), false)) {
            StringBuilder sb = new StringBuilder(samples.size() * 16);
            for (Sample s : samples) sb.append(s.minute).append(',').append(s.value).append('\n');
            w.write(sb.toString());
        } catch (Exception ignored) { }
    }
}
