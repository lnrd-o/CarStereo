package com.simple.carlauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * 24-band spectrum wallpaper: captures the audio output, runs a radix-2 FFT and animates
 * 24 rounded bars behind the home screen. Falls back to a slow idle wave when the output
 * cannot be captured (no RECORD_AUDIO permission or a device without a mix session).
 */
public class WaveView extends View {

    static final int BANDS = 24;
    private static final double F_MIN = 40, F_MAX = 16000;

    private final float[] level = new float[BANDS];
    private final float[] target = new float[BANDS];
    private final float[] hann = new float[1024];
    private final float[] re = new float[1024];
    private final float[] im = new float[1024];
    private final int[] rev = new int[1024];
    private final double[] bandLow = new double[BANDS];
    private final double[] bandHigh = new double[BANDS];

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float d;

    private Visualizer visualizer;
    private int fftSize = 1024;
    private boolean active;
    private boolean idleRunning;
    private long phase;
    private final int sampleRate = 44100;

    private final Runnable idle = new Runnable() {
        public void run() {
            if (!active) { idleRunning = false; return; }
            phase += 3;
            long t = System.currentTimeMillis() + phase;
            for (int b = 0; b < BANDS; b++) {
                double w = 0.5 + 0.5 * Math.sin(t / 900.0 + b * 0.55);
                target[b] = (float) (0.08 + 0.20 * w * (1.0 - b / (double) BANDS * 0.4));
            }
            step();
            invalidate();
            handler.postDelayed(this, 33);
        }
    };

    public WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < 1024; i++) hann[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / 1023.0));
        for (int b = 0; b < BANDS; b++) {
            bandLow[b] = F_MIN * Math.pow(F_MAX / F_MIN, b / (double) BANDS);
            bandHigh[b] = F_MIN * Math.pow(F_MAX / F_MIN, (b + 1) / (double) BANDS);
        }
        updateColors();
    }

    /** Turns the animation/capture on or off. */
    public void setActive(boolean on) {
        if (active == on) return;
        active = on;
        if (on) start(); else stop();
    }

    public boolean isActive() {
        return active;
    }

    private void start() {
        updateColors();
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            startIdle();
            return;
        }
        try {
            int[] range = Visualizer.getCaptureSizeRange();
            int size = 1024;
            if (size < range[0]) size = range[0];
            if (size > range[1]) size = range[1];
            fftSize = size;
            buildTables();
            visualizer = new Visualizer(0);
            visualizer.setCaptureSize(fftSize);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] bytes, int rate) {
                    onWave(bytes);
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] bytes, int rate) { }
            }, Math.max(1, Visualizer.getMaxCaptureRate() / 2), false, true);
            visualizer.setEnabled(true);
        } catch (Throwable t) {
            release();
            startIdle();
        }
    }

    private void stop() {
        release();
        handler.removeCallbacks(idle);
        idleRunning = false;
        for (int b = 0; b < BANDS; b++) {
            level[b] = 0;
            target[b] = 0;
        }
        invalidate();
    }

    private void release() {
        try {
            if (visualizer != null) {
                visualizer.setEnabled(false);
                visualizer.release();
            }
        } catch (Throwable ignored) { }
        visualizer = null;
    }

    private void startIdle() {
        if (idleRunning) return;
        idleRunning = true;
        handler.post(idle);
    }

    private void buildTables() {
        int bits = Integer.numberOfTrailingZeros(fftSize);
        for (int i = 0; i < fftSize; i++) {
            int x = i, out = 0;
            for (int b = 0; b < bits; b++) {
                out = (out << 1) | (x & 1);
                x >>= 1;
            }
            rev[i] = out;
        }
    }

    private void onWave(byte[] bytes) {
        int n = Math.min(bytes.length, fftSize);
        if (n <= 0) return;
        for (int i = 0; i < fftSize; i++) {
            re[i] = i < n ? ((bytes[i] & 0xFF) - 128) * hann[i * 1023 / Math.max(1, n - 1)] : 0;
            im[i] = 0;
        }
        fft();
        double binHz = sampleRate / (double) fftSize;
        for (int b = 0; b < BANDS; b++) {
            int lo = Math.max(1, (int) Math.floor(bandLow[b] / binHz));
            int hi = Math.min(fftSize / 2 - 1, (int) Math.ceil(bandHigh[b] / binHz));
            double sum = 0;
            int count = 0;
            for (int k = lo; k <= hi; k++) {
                sum += Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                count++;
            }
            double mag = count == 0 ? 0 : sum / count;
            double norm = mag / (fftSize * 0.5 * 128.0);
            double db = 20 * Math.log10(norm + 1e-6);
            target[b] = (float) Math.max(0, Math.min(1, (db + 60) / 60));
        }
        handler.post(() -> {
            step();
            invalidate();
        });
    }

    private void fft() {
        int n = fftSize;
        for (int i = 0; i < n; i++) {
            int j = rev[i];
            if (j > i) {
                float tr = re[i]; re[i] = re[j]; re[j] = tr;
                float ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wlR = (float) Math.cos(ang), wlI = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float wr = 1, wi = 0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k, v = i + k + len / 2;
                    float vr = re[v] * wr - im[v] * wi;
                    float vi = re[v] * wi + im[v] * wr;
                    re[v] = re[u] - vr;
                    im[v] = im[u] - vi;
                    re[u] += vr;
                    im[u] += vi;
                    float nwr = wr * wlR - wi * wlI;
                    wi = wr * wlI + wi * wlR;
                    wr = nwr;
                }
            }
        }
    }

    private void step() {
        for (int b = 0; b < BANDS; b++) {
            float t = target[b];
            level[b] += (t - level[b]) * (t > level[b] ? 0.55f : 0.12f);
        }
    }

    private void updateColors() {
        Context c = getContext();
        int a = Accent.color(c);
        int b = Accent.secondary(c);
        int top = (a & 0x00FFFFFF) | 0x30000000;
        int bottom = (a & 0x00FFFFFF) | 0xB0000000;
        bar.setShader(new LinearGradient(0, getHeight() * 0.25f, 0, getHeight(),
                top, bottom, Shader.TileMode.CLAMP));
        glow.setColor((b & 0x00FFFFFF) | 0x14000000);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updateColors();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float gap = w * 0.006f;
        float bw = (w - gap * (BANDS + 1)) / BANDS;
        float baseY = h * 0.94f;
        float maxH = h * 0.55f;
        for (int b = 0; b < BANDS; b++) {
            float bh = Math.max(2 * d, level[b] * maxH);
            float x = gap + b * (bw + gap);
            r.set(x - gap, baseY - bh - gap, x + bw + gap, baseY + gap);
            c.drawRoundRect(r, bw * 0.5f, bw * 0.5f, glow);
            r.set(x, baseY - bh, x + bw, baseY);
            c.drawRoundRect(r, bw * 0.45f, bw * 0.45f, bar);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
