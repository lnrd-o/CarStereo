package com.simple.carlauncher;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

/**
 * Samples the internal voltage once a minute (the home screen polls the live value every
 * second). Runs while the launcher is alive; START_STICKY brings it back if killed.
 */
public class BatteryLogger extends Service {

    private static final long SAMPLE_MS = 60 * 1000;

    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        public void run() {
            double v = StereoVoltage.read(BatteryLogger.this);
            BatteryLog.live(BatteryLogger.this, v);
            BatteryLog.sample(BatteryLogger.this, v);
            handler.postDelayed(this, SAMPLE_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
