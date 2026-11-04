package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Battery voltage: live reading from the head unit's own sensor and a per-minute history. */
public class BatteryActivity extends Activity {

    private final Handler handler = new Handler();
    private TextView value, status, source, minTv, maxTv, avgTv;
    private Chart chart;
    private int hours = 24;

    private final Runnable tick = new Runnable() {
        public void run() {
            double v = StereoVoltage.read(BatteryActivity.this);
            BatteryLog.live(BatteryActivity.this, v);
            showLive(v);
            handler.postDelayed(this, 1000);
        }
    };

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
        TextView title = Ui.text(this, "Batería", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = chip("Borrar historial");
        clear.setOnClickListener(v -> {
            BatteryLog.clear(this);
            refresh();
        });
        bar.addView(clear);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);

        body.addView(buildLive(), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1.7f);
        clp.leftMargin = dp(12);
        body.addView(buildChartCard(), clp);

        setContentView(root);
    }

    private View buildLive() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Ui.card(this));

        value = Ui.text(this, "--.- V", 56, Color.WHITE);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(value);

        status = Ui.text(this, "Sin lectura", 17, Color.WHITE);
        status.setPadding(dp(16), dp(7), dp(16), dp(7));
        card.addView(status);

        source = Ui.text(this, "", 12, 0x99FFFFFF);
        source.setPadding(0, dp(10), 0, dp(16));
        card.addView(source);

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);
        minTv = stat(stats, "Mín");
        maxTv = stat(stats, "Máx");
        avgTv = stat(stats, "Media");
        card.addView(stats);

        TextView note = Ui.text(this, "Sensor interno del radio · una muestra por minuto", 12, 0x99FFFFFF);
        note.setSingleLine(false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(16), 0, 0);
        card.addView(note);
        return card;
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 12, 0x99FFFFFF);
        TextView v = Ui.text(this, "--", 20, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(l);
        col.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        parent.addView(col, lp);
        return v;
    }

    private View buildChartCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, "Historial", 16, Color.WHITE);
        t.setGravity(Gravity.START);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView c24 = chip("24 h");
        c24.setOnClickListener(v -> { hours = 24; refresh(); });
        TextView c7 = chip("7 días");
        c7.setOnClickListener(v -> { hours = 24 * 7; refresh(); });
        LinearLayout.LayoutParams c7lp = new LinearLayout.LayoutParams(-2, -2);
        c7lp.leftMargin = dp(8);
        head.addView(c24);
        head.addView(c7, c7lp);
        card.addView(head);

        chart = new Chart(this);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, 0, 1f);
        chp.topMargin = dp(8);
        card.addView(chart, chp);
        return card;
    }

    private TextView chip(String s) {
        TextView c = Ui.text(this, s, 13, Color.WHITE);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x26FFFFFF);
        c.setBackground(bg);
        Ui.pressable(c, 0.92f);
        return c;
    }

    private void showLive(double v) {
        if (Double.isNaN(v)) {
            value.setText("--.- V");
            status.setText("Sin lectura");
            status.setBackground(bg(0x26FFFFFF));
            source.setText("Fuente: " + StereoVoltage.sourceLabel(this));
            return;
        }
        value.setText(String.format(java.util.Locale.US, "%.1f V", v));
        status.setText(StereoVoltage.status(v));
        status.setBackground(bg(StereoVoltage.statusColor(v)));
        source.setText("Fuente: " + StereoVoltage.sourceLabel(this));
    }

    private GradientDrawable bg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(color);
        return g;
    }

    private void refresh() {
        List<BatteryLog.Sample> data = BatteryLog.since(this, hours);
        chart.setData(data, hours);
        minTv.setText(fmt(BatteryLog.min(data)));
        maxTv.setText(fmt(BatteryLog.max(data)));
        avgTv.setText(fmt(BatteryLog.avg(data)));
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "--" : String.format(java.util.Locale.US, "%.1f V", v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    /** Line chart of the stored samples, window relative to now. */
    private static class Chart extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path area = new Path();
        private final float d;
        private List<BatteryLog.Sample> data = new ArrayList<>();
        private int hours = 24;

        Chart(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.4f * d);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(Accent.color(c));
            grid.setColor(0x22FFFFFF);
            grid.setStrokeWidth(1 * d);
            label.setTextSize(10 * d);
            label.setColor(0x99FFFFFF);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setData(List<BatteryLog.Sample> s, int hours) {
            data = s;
            this.hours = hours;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float left = 8 * d, right = w - 44 * d, top = 8 * d, bottom = h - 8 * d;
            if (right <= left) return;

            if (data.size() < 2) {
                label.setTextSize(13 * d);
                c.drawText("Sin muestras todavía", left, h / 2f, label);
                return;
            }
            label.setTextSize(10 * d);

            double min = BatteryLog.min(data), max = BatteryLog.max(data);
            double pad = Math.max(0.6, (max - min) * 0.15);
            min -= pad;
            max += pad;
            if (max - min < 1.0) max = min + 1.0;

            for (int i = 0; i <= 2; i++) {
                float y = top + (bottom - top) * i / 2f;
                c.drawLine(left, y, right, y, grid);
                double v = max - (max - min) * i / 2.0;
                c.drawText(String.format(java.util.Locale.US, "%.1f", v), right + 6 * d, y + 3 * d, label);
            }

            long now = System.currentTimeMillis() / 60000L;
            long start = now - hours * 60L;
            float span = Math.max(1, now - start);

            path.reset();
            area.reset();
            float firstX = 0, firstY = 0, lastX = 0, lastY = 0;
            for (int i = 0; i < data.size(); i++) {
                BatteryLog.Sample s = data.get(i);
                float x = left + (right - left) * Math.max(0, Math.min(1, (s.minute - start) / span));
                float y = bottom - (bottom - top) * (float) ((s.value - min) / (max - min));
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, bottom);
                    area.lineTo(x, y);
                    firstX = x;
                    firstY = y;
                } else {
                    path.lineTo(x, y);
                    area.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }
            area.lineTo(lastX, bottom);
            area.close();

            int accent = Accent.color(getContext());
            fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
                    (accent & 0x00FFFFFF) | 0x55000000,
                    (accent & 0x00FFFFFF) | 0x05000000, Shader.TileMode.CLAMP));
            c.drawPath(area, fillPaint);
            line.setColor(accent);
            c.drawPath(path, line);

            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(accent);
            c.drawCircle(lastX, lastY, 3.4f * d, dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(1.6f * d);
            dot.setColor(Color.WHITE);
            c.drawCircle(lastX, lastY, 3.4f * d, dot);
        }
    }
}
