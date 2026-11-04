package com.simple.carlauncher;

import android.content.Context;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;

/**
 * Car battery voltage from the head unit's own sensor (never OBD).
 *
 * Different NWD/Allwinner firmwares expose it in different places, so the first source
 * that returns a plausible 12 V value is probed once and then reused.
 */
final class StereoVoltage {

    enum Source { SETTINGS, PROPERTY, SYSFS, NONE }

    private static final String[] SETTING_KEYS = {
            "mcu_voltage", "mcu_battery_voltage", "voltage", "battery_voltage", "car_voltage"
    };
    private static final String[] PROPERTY_KEYS = {
            "persist.sys.voltage", "persist.mcu.voltage", "ro.mcu.voltage", "sys.mcu.voltage", "mcu.voltage"
    };

    private static Source source;
    private static String foundKey;
    private static String foundPath;

    private StereoVoltage() { }

    static synchronized double read(Context c) {
        if (source == null) probe(c);
        switch (source) {
            case SETTINGS:
                return normalize(Settings.System.getInt(c.getContentResolver(), foundKey, -1));
            case PROPERTY:
                return normalize(propNumber(foundKey, -1));
            case SYSFS:
                return normalize(readNumber(foundPath));
            default:
                return Double.NaN;
        }
    }

    static synchronized String sourceLabel(Context c) {
        if (source == null) probe(c);
        switch (source) {
            case SETTINGS: return "MCU · " + foundKey;
            case PROPERTY: return "propiedad · " + foundKey;
            case SYSFS: return "sysfs · " + foundPath;
            default: return "sin sensor";
        }
    }

    static String status(double v) {
        if (Double.isNaN(v)) return "Sin lectura";
        if (v < 11.6) return "Batería baja";
        if (v < 12.4) return "Motor apagado";
        if (v < 13.4) return "Batería estable";
        if (v < 14.8) return "Cargando (alternador)";
        return "Voltaje alto";
    }

    static int statusColor(double v) {
        if (Double.isNaN(v)) return 0x99FFFFFF;
        if (v < 11.6) return 0xFFE53935;
        if (v < 12.4) return 0xFFFFB300;
        if (v < 13.4) return 0xFF43A047;
        if (v < 14.8) return 0xFF29B6F6;
        return 0xFFE53935;
    }

    // ---------- probing ----------

    private static void probe(Context c) {
        // Settings.System first: the NWD MCU mirrors its values there.
        for (String key : SETTING_KEYS) {
            double v = normalize(Settings.System.getInt(c.getContentResolver(), key, -1));
            if (plausible(v)) { source = Source.SETTINGS; foundKey = key; return; }
        }
        for (String key : PROPERTY_KEYS) {
            double v = normalize(propNumber(key, -1));
            if (plausible(v)) { source = Source.PROPERTY; foundKey = key; return; }
        }
        // /sys/class/power_supply/*/voltage_now (battery, mains, usb...)
        File ps = new File("/sys/class/power_supply");
        File[] list = ps.listFiles();
        if (list != null) {
            for (File d : list) {
                File f = new File(d, "voltage_now");
                double v = normalize(readNumber(f.getAbsolutePath()));
                if (plausible(v)) { source = Source.SYSFS; foundPath = f.getAbsolutePath(); return; }
            }
        }
        source = Source.NONE;
    }

    /** 12.4 V may arrive as 12400 (mV), 1240, 124 (0.1 V) or 12 (V). */
    private static double normalize(double raw) {
        if (raw <= 0) return Double.NaN;
        if (raw > 3000) return raw / 1000.0;
        if (raw >= 1000) return raw / 100.0;
        if (raw >= 100) return raw / 10.0;
        return raw;
    }

    private static boolean plausible(double v) {
        return !Double.isNaN(v) && v >= 7.0 && v <= 17.0;
    }

    private static String propString(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v == null ? def : v.toString();
        } catch (Exception e) {
            return def;
        }
    }

    private static double propNumber(String key, double def) {
        String s = propString(key, null);
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double readNumber(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? -1 : Double.parseDouble(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
