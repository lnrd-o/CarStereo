package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Color;

/**
 * Accent colour chosen by the user ("colores de sistema"). Every screen, the map and the
 * spectrum wallpaper take their highlight colour from here.
 */
final class Accent {

    static final int DEFAULT = 0xFF2EB8FF;
    static final int[] SWATCHES = {
            0xFF2EB8FF, 0xFF7C4DFF, 0xFF00C853, 0xFFFF6D00, 0xFFFF3D71, 0xFFFFC400
    };
    private static final String KEY = "accent";

    private Accent() { }

    static int color(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(KEY, DEFAULT);
    }

    static void set(Context c, int color) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt(KEY, color).apply();
    }

    /** Second colour for gradients: the accent rotated a few degrees. */
    static int secondary(Context c) {
        float[] hsv = new float[3];
        Color.colorToHSV(color(c), hsv);
        hsv[0] = (hsv[0] + 42f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * 0.85f + 0.1f);
        return Color.HSVToColor(hsv);
    }

    static int[] gradient(Context c) {
        return new int[]{color(c), secondary(c)};
    }

    static int alpha(Context c, int a) {
        return (color(c) & 0x00FFFFFF) | (a << 24);
    }

    /** Darker variant, used for light-theme backgrounds. */
    static int dark(Context c) {
        float[] hsv = new float[3];
        Color.colorToHSV(color(c), hsv);
        hsv[2] *= 0.45f;
        return Color.HSVToColor(hsv);
    }
}
