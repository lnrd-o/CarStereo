package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private final HcnRadio hcnRadio = new HcnRadio();
    private NowPlaying nowPlaying;
    private boolean hcnRadioMode;
    private boolean hcnRadioActive;

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
    private boolean hasLightsImage;
    private TextView oilInfo, oilInfoDrive;
    private GradientDrawable oilBg, oilBgDrive;
    private boolean createdDark;
    private String createdProfile;
    private int createdAccent;
    private long lastWeatherFetch;

    // Pages and spectrum wallpaper
    private Pager pager;
    private LinearLayout dots;
    private WaveView wave;
    private MapView mapCard;
    private int page;

    // Big "now playing" card: Bluetooth/media panel and radio panel
    private FrameLayout mediaPanel;
    private LinearLayout radioPanel;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;
    private TextView fmBand, fmFreq, fmUnit, radioPs, radioRt;
    private Bitmap lastArt;
    private boolean radioActive;

    // Compact "now playing" cards used on the other two pages
    private static final class Mini {
        TextView source, title, sub;
        ImageView play;
    }
    private final List<Mini> minis = new ArrayList<>();

    // Voltage
    private TextView voltageChip, voltageBig, voltageStatus, voltageDrive, voltageDriveStatus;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));

        wave = new WaveView(this);
        root.addView(wave, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(4));

        pager = new Pager(this);
        pager.addView(buildClassicPage());
        pager.addView(buildFocusPage());
        pager.addView(buildDrivePage());
        content.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        content.addView(dots, new LinearLayout.LayoutParams(-1, dp(18)));

        content.addView(buildDock(), new LinearLayout.LayoutParams(-1, dp(84)));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        buildDots();
        page = getSharedPreferences("ui", MODE_PRIVATE).getInt("page", 0);
        pager.setOnPage(p -> {
            page = p;
            getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("page", p).apply();
            paintDots();
            mapPaused(p);
        });
        pager.post(() -> {
            pager.setPage(page, false);
            paintDots();
            mapPaused(page);
        });

        radio.bind(this);
        hcnRadioMode = !Profile.isNwd(this) && HcnRadio.installed(this);
        if (hcnRadioMode) hcnRadio.bind(this, this::renderNow);
        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();
        createdAccent = Accent.color(this);
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
        startService(new Intent(this, BatteryLogger.class));
    }

    // ---------- Page 1: classic cards ----------

    private View buildClassicPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(0, 0, 0, dp(6));

        page.addView(buildWeatherCard(), weight(1.05f, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(buildClockCard(false), vweight(0.58f, 0));
        middle.addView(buildVoltageCard(), vweight(0.42f, dp(12)));
        page.addView(middle, weight(0.95f, dp(12)));

        page.addView(buildNowCard(), weight(1.25f, dp(12)));
        return page;
    }

    // ---------- Page 2: focus (map + now) ----------

    private View buildFocusPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, dp(6));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackground(Ui.card(this));
        top.setPadding(dp(20), dp(8), dp(20), dp(8));

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(34);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        clockCol.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(12);
        date.setTextColor(0xCCFFFFFF);
        clockCol.addView(date);
        top.addView(clockCol, new LinearLayout.LayoutParams(0, -2, 1f));

        voltageChip = Ui.text(this, "--.- V", 18, Color.WHITE);
        voltageChip.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable vbg = new GradientDrawable();
        vbg.setCornerRadius(dp(16));
        vbg.setColor(0x33FFFFFF);
        voltageChip.setBackground(vbg);
        Ui.pressable(voltageChip, 0.92f);
        voltageChip.setOnClickListener(v -> startActivity(new Intent(this, BatteryActivity.class)));
        top.addView(voltageChip);

        TextView open = Ui.text(this, "Abrir mapa", 14, Color.WHITE);
        open.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable obg = new GradientDrawable();
        obg.setCornerRadius(dp(16));
        obg.setColors(Accent.gradient(this));
        open.setBackground(obg);
        Ui.pressable(open, 0.92f);
        open.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.leftMargin = dp(10);
        top.addView(open, olp);

        page.addView(top, new LinearLayout.LayoutParams(-1, dp(70)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setBackground(Ui.card(this));
        mapBox.setClipToOutline(true);
        mapCard = new MapView(this);
        mapCard.setPaused(true);
        mapBox.addView(mapCard, new FrameLayout.LayoutParams(-1, -1));
        TextView mapHint = Ui.text(this, "Mapa", 12, 0xCCFFFFFF);
        mapHint.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable mhg = new GradientDrawable();
        mhg.setCornerRadius(dp(12));
        mhg.setColor(0x66000000);
        mapHint.setBackground(mhg);
        FrameLayout.LayoutParams mhlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        mhlp.setMargins(dp(10), dp(10), 0, 0);
        mapBox.addView(mapHint, mhlp);
        mapHint.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        bottom.addView(mapBox, weight(1.35f, 0));

        bottom.addView(buildMiniNowCard(), weight(0.95f, dp(12)));
        page.addView(bottom, vweight(1f, dp(12)));
        return page;
    }

    // ---------- Page 3: drive ----------

    private View buildDrivePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(0, 0, 0, dp(6));

        page.addView(buildClockCard(true), weight(1f, 0));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(buildVoltageDriveCard(), vweight(0.9f, 0));
        right.addView(buildMiniNowCard(), vweight(1.1f, dp(12)));
        page.addView(right, weight(1.1f, dp(12)));
        return page;
    }

    // ---------- Greeting + weather ----------

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0x993FA9FF, 0x667B4DFF}, new int[]{0xFF1E1E28, 0xFF121218}));
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), Accent.alpha(this, 0x44));
        card.setBackground(weatherBg);

        TextView hello = left(Ui.text(this, Profile.hello(this), 34, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hello);

        greeting = left(Ui.text(this, "", 17, 0xCCFFFFFF));
        greeting.setPadding(0, dp(4), 0, 0);
        card.addView(greeting);

        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) {
            car.setImageBitmap(own);
        } else if (bundled != 0) {
            car.setImageResource(bundled);
            if (bundledLights != 0) {
                carLights.setImageResource(bundledLights);
                hasLightsImage = true;
            }
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(0x40FFFFFF);
        }
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        carBox.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.setMargins(dp(30), dp(8), dp(30), dp(8));
        card.addView(carBox, clp);

        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        weatherTemp = left(Ui.text(this, "--°", 50, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 16, Color.WHITE));
        weatherDesc.setSingleLine(false);
        weatherDesc.setMaxLines(2);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 13, 0xB3FFFFFF));
        weatherCity.setPadding(0, dp(2), 0, 0);
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        weatherExtra = left(Ui.text(this, "", 14, 0xE6FFFFFF));
        weatherExtra.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x26FFFFFF);
        chip.setCornerRadius(dp(20));
        weatherExtra.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        card.addView(weatherExtra, lp);

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> fetchWeather(true));
        return card;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText((h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches")
                + " · Bienvenido");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                : w.city + " · Sensación " + w.feelsLike + "°");
        weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        weatherBg.setColors(Ui.isDark(this) ? new int[]{0xFF1E1E28, 0xFF121218}
                : w.isDay ? new int[]{0x993FA9FF, 0x667B4DFF} : new int[]{0x8C2A3A9E, 0x80261A6E});
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark
                    || Accent.color(MainActivity.this) != createdAccent) recreate();
            updateGreeting();
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0);
            handler.postDelayed(() -> {
                if (Ui.isDark(MainActivity.this) != createdDark) recreate();
            }, 1500);
        }
    };

    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0;
    }

    private void showCarLights(boolean on) {
        if (!hasLightsImage) return;
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private void updateOil() {
        if (oilInfo != null) paintOil(oilInfo, oilBg);
        if (oilInfoDrive != null) paintOil(oilInfoDrive, oilBgDrive);
    }

    private void paintOil(TextView text, GradientDrawable bg) {
        if (!Odometer.isConfigured(this)) {
            text.setText("Aceite: configurar");
            if (bg != null) bg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        text.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        if (bg != null) bg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Clock ----------

    private View buildClockCard(boolean drive) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(drive ? 64 : 40);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(drive ? 15 : 14);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(0, dp(6), 0, 0);
        date.setGravity(Gravity.CENTER);
        card.addView(date);

        TextView oil = Ui.text(this, "", 13, Color.WHITE);
        oil.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable obg = new GradientDrawable();
        obg.setCornerRadius(dp(14));
        oil.setBackground(obg);
        Ui.pressable(oil, 0.92f);
        oil.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = dp(8);
        card.addView(oil, olp);
        if (drive) {
            oilInfoDrive = oil;
            oilBgDrive = obg;
        } else {
            oilInfo = oil;
            oilBg = obg;
        }

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openSettings());
        return card;
    }

    // ---------- Voltage ----------

    private View buildVoltageCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        voltageBig = Ui.text(this, "--.- V", 42, Color.WHITE);
        voltageBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(voltageBig);
        voltageStatus = Ui.text(this, "Sin lectura", 13, 0xCCFFFFFF);
        voltageStatus.setPadding(0, dp(2), 0, 0);
        card.addView(voltageStatus);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> startActivity(new Intent(this, BatteryActivity.class)));
        return card;
    }

    private View buildVoltageDriveCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextView title = Ui.text(this, "BATERÍA", 11, 0x99FFFFFF);
        title.setLetterSpacing(0.15f);
        card.addView(title);
        voltageDrive = Ui.text(this, "--.- V", 46, Color.WHITE);
        voltageDrive.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(voltageDrive);
        voltageDriveStatus = Ui.text(this, "Sin lectura", 13, 0xCCFFFFFF);
        card.addView(voltageDriveStatus);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> startActivity(new Intent(this, BatteryActivity.class)));
        return card;
    }

    private void showVoltage(double v) {
        String text = Double.isNaN(v) ? "--.- V" : String.format(Locale.US, "%.1f V", v);
        int color = StereoVoltage.statusColor(v);
        String status = StereoVoltage.status(v);
        if (voltageChip != null) voltageChip.setText(text);
        if (voltageBig != null) voltageBig.setText(text);
        if (voltageStatus != null) {
            voltageStatus.setText(status);
            voltageStatus.setTextColor(color);
        }
        if (voltageDrive != null) voltageDrive.setText(text);
        if (voltageDriveStatus != null) {
            voltageDriveStatus.setText(status);
            voltageDriveStatus.setTextColor(color);
        }
    }

    private final Runnable liveTick = new Runnable() {
        public void run() {
            double v = StereoVoltage.read(MainActivity.this);
            BatteryLog.live(MainActivity.this, v);
            showVoltage(v);
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Big now playing card ----------

    private View buildNowCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{Accent.dark(this), 0xFF2B1A7A, 0xFF0A0A0E},
                        new int[]{0xFF2E1A55, 0xFF1A1030, 0xFF0A0A0E}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        mediaPanel = new FrameLayout(this);
        card.addView(mediaPanel, new FrameLayout.LayoutParams(-1, -1));

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaPanel.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x00000000, 0xCC100A30}));
        mediaPanel.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        slp.setMargins(dp(14), dp(14), 0, 0);
        mediaPanel.addView(songSource, slp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(18));
        songTitle = Ui.text(this, "", 21, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(songTitle);
        songArtist = Ui.text(this, "", 15, 0xCCFFFFFF);
        songArtist.setPadding(0, dp(2), 0, dp(14));
        bottom.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 50, false, () -> {
            if (radioActive) stationStep(false);
            else nowPlaying.skip(false);
        }), new LinearLayout.LayoutParams(dp(50), dp(50)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 66, true, () -> {
            if (radioActive) openRadio();
            else nowPlaying.playPause();
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(66), dp(66));
        plp.setMargins(dp(18), 0, dp(18), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 50, false, () -> {
            if (radioActive) stationStep(true);
            else nowPlaying.skip(true);
        }), new LinearLayout.LayoutParams(dp(50), dp(50)));
        bottom.addView(controls);
        mediaPanel.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Radio panel (shown when the FM tuner owns the speakers)
        radioPanel = new LinearLayout(this);
        radioPanel.setOrientation(LinearLayout.VERTICAL);
        radioPanel.setGravity(Gravity.CENTER);
        radioPanel.setPadding(dp(16), dp(18), dp(16), dp(16));
        GradientDrawable rbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Accent.dark(this), 0xFF141022});
        rbg.setCornerRadius(dp(18));
        radioPanel.setBackground(rbg);
        radioPanel.setVisibility(View.GONE);

        fmBand = Ui.text(this, "RADIO FM", 14, Color.WHITE);
        radioPanel.addView(fmBand);
        fmFreq = Ui.text(this, "--.-", 54, Color.WHITE);
        fmFreq.setTypeface(Typeface.DEFAULT_BOLD);
        radioPanel.addView(fmFreq);
        fmUnit = Ui.text(this, "MHz", 14, 0x99FFFFFF);
        radioPanel.addView(fmUnit);
        radioPs = Ui.text(this, "Sin RDS", 15, 0xCCFFFFFF);
        radioPs.setPadding(0, dp(8), 0, dp(2));
        radioPanel.addView(radioPs);
        radioRt = Ui.text(this, "", 13, 0xB3FFFFFF);
        radioRt.setSingleLine(true);
        radioRt.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        radioRt.setMarqueeRepeatLimit(-1);
        radioRt.setSelected(true);
        radioRt.setPadding(0, 0, 0, dp(10));
        radioPanel.addView(radioRt);

        LinearLayout rControls = new LinearLayout(this);
        rControls.setGravity(Gravity.CENTER);
        rControls.addView(Ui.iconButton(this, R.drawable.ic_prev, 50, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        rControls.addView(Ui.iconButton(this, R.drawable.ic_radio, 60, true, this::openRadio),
                new LinearLayout.LayoutParams(dp(60), dp(60)));
        rControls.addView(Ui.iconButton(this, R.drawable.ic_next, 50, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        radioPanel.addView(rControls);
        card.addView(radioPanel, new FrameLayout.LayoutParams(-1, -1));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> {
            if (radioActive) openRadio();
            else nowPlaying.open(this);
        });
        return card;
    }

    // ---------- Compact now playing cards ----------

    private View buildMiniNowCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.card(this));

        Mini m = new Mini();
        m.source = Ui.text(this, "MÚSICA", 11, 0xCCFFFFFF);
        m.source.setLetterSpacing(0.12f);
        m.source.setGravity(Gravity.START);
        card.addView(m.source);

        m.title = Ui.text(this, "", 20, Color.WHITE);
        m.title.setTypeface(Typeface.DEFAULT_BOLD);
        m.title.setGravity(Gravity.START);
        m.title.setPadding(0, dp(6), 0, 0);
        card.addView(m.title);

        m.sub = Ui.text(this, "", 14, 0xCCFFFFFF);
        m.sub.setGravity(Gravity.START);
        card.addView(m.sub);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        m.play = Ui.iconButton(this, R.drawable.ic_play, 54, true, () -> {
            if (radioActive) openRadio();
            else nowPlaying.playPause();
        });
        row.addView(m.play, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(44), dp(44));
        nlp.leftMargin = dp(10);
        row.addView(Ui.iconButton(this, R.drawable.ic_next, 44, false, () -> {
            if (radioActive) stationStep(true);
            else nowPlaying.skip(true);
        }), nlp);
        card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));

        minis.add(m);
        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> {
            if (radioActive) openRadio();
            else nowPlaying.open(this);
        });
        return card;
    }

    private void renderNow() {
        boolean radioOn = Profile.isNwd(this) ? Ui.radioIsSource(this)
                : (hcnRadioMode && hcnRadioActive);
        radioActive = radioOn;
        RadioClient.Station st = null;
        String rt = "";
        if (radioOn) {
            if (hcnRadioMode) {
                st = hcnRadio.isBound() ? hcnRadio.current() : null;
                rt = hcnRadio.isBound() ? hcnRadio.radioText() : "";
            } else {
                st = radio.current();
            }
        }
        String rBand = st == null ? "RADIO" : st.bandLabel();
        String rFreq = st == null ? "--.-" : st.frequencyLabel();
        String rUnit = st == null ? "MHz" : (st.isAm() ? "kHz" : "MHz");
        String rPs = st == null ? "" : st.psName;

        if (mediaPanel != null) mediaPanel.setVisibility(radioOn ? View.GONE : View.VISIBLE);
        if (radioPanel != null) radioPanel.setVisibility(radioOn ? View.VISIBLE : View.GONE);
        if (radioOn) {
            fmBand.setText(rBand + (rPs.isEmpty() ? "" : " · RDS"));
            fmFreq.setText(rFreq);
            fmUnit.setText(rUnit);
            radioPs.setText(rPs.isEmpty() ? "Sin RDS" : rPs);
            if (radioRt != null) {
                radioRt.setText(rt);
                radioRt.setVisibility(rt.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }

        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        String src = s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase();
        String title = empty ? "Nada sonando" : t.title;
        String sub = empty ? "Toca para abrir la música" : t.artist;

        songSource.setText(src);
        songTitle.setText(title);
        songArtist.setText(sub);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            if (t.art != lastArt) {
                lastArt = t.art;
                art.setPadding(0, 0, 0, 0);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.clearColorFilter();
                art.setAlpha(1f);
                art.setImageBitmap(t.art);
            }
        } else {
            lastArt = null;
            art.setPadding(dp(80), dp(56), dp(80), dp(170));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
            art.setAlpha(0.25f);
        }

        for (Mini m : minis) {
            if (radioOn) {
                m.source.setText("RADIO · " + rBand);
                m.title.setText(rPs.isEmpty() ? rFreq + " " + rUnit : rPs);
                m.sub.setText(!rt.isEmpty() ? rt
                        : rFreq + " " + rUnit + (rPs.isEmpty() ? "" : " · RDS"));
                m.play.setImageResource(R.drawable.ic_radio);
            } else {
                m.source.setText(src);
                m.title.setText(title);
                m.sub.setText(sub);
                m.play.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            }
        }
    }

    @Override
    public void onNowPlayingChanged() {
        if (hcnRadioMode && nowPlaying.hcn.isBound() && nowPlaying.hcn.playing) {
            hcnRadioActive = false;
        }
        renderNow();
    }

    private void stationStep(boolean next) {
        if (hcnRadioMode) {
            withHcnRadio(() -> {
                if (next) hcnRadio.presetUp();
                else hcnRadio.presetDown();
            });
            return;
        }
        if (!Profile.isNwd(this)) {
            openRadio();
            return;
        }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio);
            handler.postDelayed(this::renderNow, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::renderNow, 300);
    }

    /** Binds the HCN radio if needed, brings its audio up and runs the action. */
    private void withHcnRadio(Runnable action) {
        hcnRadioActive = true;
        Runnable run = () -> {
            if (nowPlaying != null && nowPlaying.hcn.isBound() && nowPlaying.hcn.playing) {
                nowPlaying.hcn.command(HcnBt.PAUSE);
            }
            hcnRadio.requestPlay();
            handler.postDelayed(() -> {
                action.run();
                handler.postDelayed(this::renderNow, 400);
            }, 250);
        };
        if (hcnRadio.isBound()) run.run();
        else hcnRadio.bind(this, run);
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            if (Profile.isNwd(MainActivity.this) || hcnRadioMode) renderNow();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockIcon(R.drawable.ic_nav,
                () -> startActivity(new Intent(this, MapActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_bluetooth, this::openBluetooth), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_radio, this::openRadio), weight(1f, 0));

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        center.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight(1f, 0));

        dock.addView(dockIcon(R.drawable.ic_w_moon,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_battery,
                () -> startActivity(new Intent(this, BatteryActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_eq, this::openEqualizer), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_palette,
                () -> startActivity(new Intent(this, ColorActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_gear, this::openSettings), weight(1f, 0));
        return dock;
    }

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            dot.setBackground(g);
            Ui.pressable(dot, 0.8f);
            final int target = i;
            dot.setOnClickListener(v -> pager.setPage(target, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(dot, lp);
        }
        paintDots();
    }

    private void paintDots() {
        if (dots == null) return;
        for (int i = 0; i < dots.getChildCount(); i++) {
            GradientDrawable g = (GradientDrawable) dots.getChildAt(i).getBackground();
            g.setColor(i == page ? Accent.color(this) : 0x40FFFFFF);
        }
    }

    private void mapPaused(int p) {
        if (mapCard != null) mapCard.setPaused(p != 1);
    }

    // ---------- Shortcuts ----------

    private void openRadio() {
        if (hcnRadioMode) {
            hcnRadioActive = true;
            startActivity(new Intent(this, RadioActivity.class));
            return;
        }
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(eq) && !launchByKeyword("ecualiz", "equaliz", "audio", "sonido", "dsp"))
            toast("No se encontró un ecualizador");
    }

    private void openSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    /** Opens the first installed app whose name or package matches one of the keywords. */
    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private View dockIcon(int icon, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return f;
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark
                || !profileStamp().equals(createdProfile)
                || Accent.color(this) != createdAccent) {
            recreate();
            return;
        }
        updateGreeting();
        updateOil();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        boolean lights = hasLightsImage && carLightsOn();
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);

        boolean needLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
        boolean needAudio = ColorActivity.waveEnabled(this)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED;
        if (needLocation || needAudio) {
            List<String> perms = new ArrayList<>();
            if (needLocation) perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (needAudio) perms.add(Manifest.permission.RECORD_AUDIO);
            requestPermissions(perms.toArray(new String[0]), 1);
        }

        wave.setActive(ColorActivity.waveEnabled(this));
        mapPaused(page);
        if (hcnRadioMode) hcnRadio.bind(this, this::renderNow);
        handler.post(periodic);
        handler.post(liveTick);
        if (Profile.isNwd(this) || hcnRadioMode) handler.post(radioPoll);
        nowPlaying.addListener(this);
        renderNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(illReceiver);
        } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(liveTick);
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
        if (mapCard != null) mapCard.stop();
        wave.setActive(false);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean location = false;
        for (int i = 0; i < perms.length && i < results.length; i++) {
            if (Manifest.permission.ACCESS_FINE_LOCATION.equals(perms[i])
                    && results[i] == PackageManager.PERMISSION_GRANTED) location = true;
        }
        if (location) {
            fetchWeather(true);
            mapPaused(page);
        }
        wave.setActive(ColorActivity.waveEnabled(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
        hcnRadio.unbind(this);
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
