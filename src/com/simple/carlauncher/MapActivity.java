package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen map with zoom / recenter controls and a live speed chip. */
public class MapActivity extends Activity {

    private MapView map;
    private TextView info, speed;
    private final Handler handler = new Handler();

    private final Runnable tick = new Runnable() {
        public void run() {
            if (map.hasFix()) {
                speed.setText(Math.round(map.speedKmh()) + " km/h");
                info.setText(String.format(java.util.Locale.US,
                        "%.5f, %.5f · zoom %d", map.lat(), map.lon(), map.zoom()));
            } else {
                speed.setText("-- km/h");
                info.setText("Buscando GPS…");
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);

        map = new MapView(this);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), 0);
        top.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Mapa", 24, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, 0, 0);
        title.setShadowLayer(dp(6), 0, 0, 0xAA000000);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        speed = Ui.text(this, "-- km/h", 17, Color.WHITE);
        speed.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(dp(16));
        chip.setColor(0x66000000);
        speed.setBackground(chip);
        top.addView(speed);
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP));

        LinearLayout zoomBox = new LinearLayout(this);
        zoomBox.setOrientation(LinearLayout.VERTICAL);
        zoomBox.addView(round("+", () -> map.zoomIn()));
        zoomBox.addView(round("−", () -> map.zoomOut()));
        zoomBox.addView(round("◎", () -> map.centerOnMe()));
        FrameLayout.LayoutParams zlp = new FrameLayout.LayoutParams(dp(54), -2,
                Gravity.END | Gravity.CENTER_VERTICAL);
        zlp.rightMargin = dp(14);
        root.addView(zoomBox, zlp);

        info = Ui.text(this, "Buscando GPS…", 12, 0xCCFFFFFF);
        info.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(12));
        ibg.setColor(0x66000000);
        info.setBackground(ibg);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.START | Gravity.BOTTOM);
        ilp.setMargins(dp(14), 0, 0, dp(14));
        root.addView(info, ilp);

        setContentView(root);
    }

    private TextView round(String s, Runnable action) {
        TextView b = Ui.text(this, s, 24, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x66000000);
        bg.setStroke(dp(1), 0x44FFFFFF);
        b.setBackground(bg);
        Ui.pressable(b, 0.9f);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(50));
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.start();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.stop();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
