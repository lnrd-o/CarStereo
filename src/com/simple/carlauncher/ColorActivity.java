package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Appearance: accent colour ("colores de sistema"), theme and the FFT wallpaper toggle. */
public class ColorActivity extends Activity {

    private final List<View> swatches = new ArrayList<>();
    private HueBar hueBar;
    private View preview;
    private TextView autoChip, lightChip, darkChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Apariencia", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);
        body.addView(buildColors(), new LinearLayout.LayoutParams(0, -1, 1.15f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(12);
        body.addView(buildOptions(), rlp);

        setContentView(root);
    }

    private View buildColors() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(Ui.card(this));

        TextView t = Ui.text(this, "Color de acento", 17, Color.WHITE);
        t.setGravity(Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView hint = Ui.text(this, "Se usa en el mapa, el fondo FFT y los controles.", 12, 0x99FFFFFF);
        hint.setGravity(Gravity.START);
        hint.setPadding(0, dp(4), 0, dp(14));
        card.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int color : Accent.SWATCHES) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            v.setBackground(g);
            Ui.pressable(v, 0.9f);
            v.setOnClickListener(x -> {
                Accent.set(this, color);
                paintSelection();
                paintPreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.rightMargin = dp(8);
            row.addView(v, lp);
            swatches.add(v);
        }
        card.addView(row);

        TextView custom = Ui.text(this, "Personalizado", 13, 0xCCFFFFFF);
        custom.setGravity(Gravity.START);
        custom.setPadding(0, dp(18), 0, dp(6));
        card.addView(custom);

        hueBar = new HueBar(this);
        hueBar.setHue(hueOf(Accent.color(this)));
        hueBar.setOnChange(() -> {
            Accent.set(this, Color.HSVToColor(new float[]{hueBar.hue(), 0.82f, 1f}));
            paintSelection();
            paintPreview();
        });
        card.addView(hueBar, new LinearLayout.LayoutParams(-1, dp(48)));

        preview = new View(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(56));
        plp.topMargin = dp(18);
        card.addView(preview, plp);

        TextView note = Ui.text(this, "El mapa toma este color en el marcador y el fondo FFT en las barras.", 12, 0x99FFFFFF);
        note.setSingleLine(false);
        note.setGravity(Gravity.START);
        note.setPadding(0, dp(14), 0, 0);
        card.addView(note);

        paintSelection();
        paintPreview();
        return card;
    }

    private View buildOptions() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(Ui.card(this));

        TextView t = Ui.text(this, "Tema", 17, Color.WHITE);
        t.setGravity(Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        LinearLayout themes = new LinearLayout(this);
        themes.setPadding(0, dp(12), 0, dp(6));
        autoChip = themeChip(themes, "Auto", Ui.THEME_AUTO);
        lightChip = themeChip(themes, "Claro", Ui.THEME_LIGHT);
        darkChip = themeChip(themes, "Oscuro", Ui.THEME_DARK);
        card.addView(themes);
        paintThemes();

        Switch wave = new Switch(this);
        wave.setText("Fondo FFT (24 bandas)");
        wave.setTextColor(Color.WHITE);
        wave.setTextSize(15);
        wave.setChecked(waveEnabled(this));
        wave.setPadding(0, dp(18), 0, dp(6));
        wave.setOnCheckedChangeListener((b, on) ->
                getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("wave", on).apply());
        card.addView(wave);

        TextView note = Ui.text(this,
                "El fondo FFT lee la salida de audio del radio (necesita permiso de micrófono). "
                        + "El mapa usa OpenStreetMap/CARTO y respeta el tema y el color de acento.",
                12, 0x99FFFFFF);
        note.setSingleLine(false);
        note.setGravity(Gravity.START);
        note.setPadding(0, dp(12), 0, 0);
        card.addView(note);

        TextView samples = Ui.text(this, "Vistas: desliza el inicio para cambiar entre Clásico, Enfoque y Viaje.", 12, 0xB3FFFFFF);
        samples.setSingleLine(false);
        samples.setGravity(Gravity.START);
        samples.setPadding(0, dp(14), 0, 0);
        card.addView(samples);
        return card;
    }

    private TextView themeChip(LinearLayout parent, String label, String mode) {
        TextView c = Ui.text(this, label, 14, Color.WHITE);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        Ui.pressable(c, 0.92f);
        c.setOnClickListener(v -> {
            Ui.setThemeMode(this, mode);
            paintThemes();
            recreate();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(8);
        parent.addView(c, lp);
        return c;
    }

    private void paintThemes() {
        String mode = Ui.themeMode(this);
        chip(autoChip, Ui.THEME_AUTO.equals(mode));
        chip(lightChip, Ui.THEME_LIGHT.equals(mode));
        chip(darkChip, Ui.THEME_DARK.equals(mode));
    }

    private void chip(TextView v, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (on) g.setColors(Accent.gradient(this));
        else g.setColor(0x26FFFFFF);
        v.setBackground(g);
    }

    private void paintSelection() {
        int accent = Accent.color(this);
        for (int i = 0; i < swatches.size(); i++) {
            View v = swatches.get(i);
            int color = Accent.SWATCHES[i];
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            g.setStroke(color == accent ? dp(4) : dp(1), color == accent ? Color.WHITE : 0x33FFFFFF);
            v.setBackground(g);
        }
    }

    private void paintPreview() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                Accent.gradient(this));
        g.setCornerRadius(dp(16));
        preview.setBackground(g);
    }

    static boolean waveEnabled(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("wave", true);
    }

    private static float hueOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[0];
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    /** Rainbow bar; picking a hue sets a custom accent. */
    private static class HueBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float hue;
        private Runnable onChange;

        HueBar(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            knob.setColor(Color.WHITE);
            knob.setShadowLayer(4 * d, 0, 0, 0xAA000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setHue(float h) {
            hue = h;
            invalidate();
        }

        float hue() {
            return hue;
        }

        void setOnChange(Runnable r) {
            onChange = r;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int[] colors = new int[7];
            for (int i = 0; i < 7; i++) colors[i] = Color.HSVToColor(new float[]{i * 60f, 0.82f, 1f});
            paint.setShader(new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth();
            float t = 18 * d;
            c.drawRoundRect(0, (h - t) / 2, w, (h + t) / 2, t / 2, t / 2, paint);
            float x = hue / 360f * w;
            c.drawCircle(x, h / 2f, 10 * d, knob);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            hue = Math.max(0, Math.min(359.9f, e.getX() / Math.max(1, getWidth()) * 360f));
            invalidate();
            if (e.getAction() == MotionEvent.ACTION_UP && onChange != null) onChange.run();
            return true;
        }
    }
}
