package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Client for the HCN factory radio (com.hcn.autoradio), the platform used by Corsa-style
 * units. Talks raw Binder to IRadioServiceAPI, including RDS RadioText (getRdsRT).
 */
final class HcnRadio implements ServiceConnection {

    static final String PACKAGE = "com.hcn.autoradio";
    private static final String ACTION = "com.hcn.autoradio.FM_PLUG_SERVICE";
    private static final String SERVICE = "com.hcn.autoradio.service.FMPlugService";
    private static final String DESC = "com.hcn.autoradio.IRadioServiceAPI";

    private static final int TX_GOTO_FREQ = 14;
    private static final int TX_GOTO_INDEX = 16;
    private static final int TX_GET_BAND = 18;
    private static final int TX_GET_FREQ = 19;
    private static final int TX_GET_PS = 20;
    private static final int TX_REQUEST_PLAY = 29;
    private static final int TX_RELEASE_PLAY = 32;
    private static final int TX_SET_BAND = 34;
    private static final int TX_SEEK_DOWN = 36;
    private static final int TX_SEEK_UP = 37;
    private static final int TX_PRESET_DOWN = 38;
    private static final int TX_PRESET_UP = 39;
    private static final int TX_GET_INDEX = 42;
    private static final int TX_IS_MUTED = 45;
    private static final int TX_GET_PRESET = 46;
    private static final int TX_GET_RDS_RT = 54;

    private IBinder binder;
    private Runnable onReady;

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isBound() {
        return binder != null && binder.isBinderAlive();
    }

    void bind(Context c, Runnable ready) {
        if (isBound()) {
            if (ready != null) ready.run();
            return;
        }
        onReady = ready;
        try {
            c.bindService(new Intent(ACTION).setComponent(new ComponentName(PACKAGE, SERVICE)),
                    this, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) { }
    }

    void unbind(Context c) {
        if (binder == null) return;
        try {
            c.unbindService(this);
        } catch (IllegalArgumentException ignored) { }
        binder = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = service;
        Runnable r = onReady;
        onReady = null;
        if (r != null) r.run();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    // ---------- Reads ----------

    /** Current station; frequency comes in kHz (FM 96100 -> 96.10 MHz). */
    RadioClient.Station current() {
        Parcel freqReply = send(TX_GET_FREQ, null);
        if (freqReply == null) return null;
        int khz = freqReply.readInt();
        freqReply.recycle();
        Parcel psReply = send(TX_GET_PS, null);
        String ps = "";
        if (psReply != null) {
            String s = psReply.readString();
            ps = s == null ? "" : s.trim();
            psReply.recycle();
        }
        return station(khz, ps);
    }

    /** Saved stations of the current band (may contain empty slots). */
    List<RadioClient.Station> presets() {
        List<RadioClient.Station> out = new ArrayList<>();
        Parcel reply = send(TX_GET_PRESET, null);
        if (reply == null) return out;
        int[] freqs = reply.createIntArray();
        reply.recycle();
        if (freqs == null) return out;
        for (int khz : freqs) out.add(station(khz, ""));
        return out;
    }

    int currentIndex() {
        Parcel reply = send(TX_GET_INDEX, null);
        if (reply == null) return -1;
        int i = reply.readInt();
        reply.recycle();
        return i;
    }

    /** RDS RadioText (song/title announced by the station), empty when there is none. */
    String radioText() {
        Parcel reply = send(TX_GET_RDS_RT, null);
        if (reply == null) return "";
        String s = reply.readString();
        reply.recycle();
        return s == null ? "" : s.trim();
    }

    int band() {
        Parcel reply = send(TX_GET_BAND, null);
        if (reply == null) return -1;
        int b = reply.readInt();
        reply.recycle();
        return b;
    }

    boolean isMuted() {
        Parcel reply = send(TX_IS_MUTED, null);
        if (reply == null) return true;
        int m = reply.readInt();
        reply.recycle();
        return m != 0;
    }

    // ---------- Controls ----------

    void requestPlay() {
        recycle(send(TX_REQUEST_PLAY, null));
    }

    void releasePlay() {
        recycle(send(TX_RELEASE_PLAY, null));
    }

    void gotoFreq(int khz) {
        recycle(send(TX_GOTO_FREQ, khz));
    }

    void gotoPreset(int index) {
        recycle(send(TX_GOTO_INDEX, index));
    }

    void seekUp() {
        recycle(send(TX_SEEK_UP, null));
    }

    void seekDown() {
        recycle(send(TX_SEEK_DOWN, null));
    }

    void presetUp() {
        recycle(send(TX_PRESET_UP, null));
    }

    void presetDown() {
        recycle(send(TX_PRESET_DOWN, null));
    }

    void setBand(int band) {
        recycle(send(TX_SET_BAND, band));
    }

    // ---------- Conversion ----------

    /** Empty preset slots reported by the factory service. */
    static boolean isEmptySlot(int khz) {
        return khz <= 0 || khz == 87500 || khz == 531 || khz == 522 || khz == 520;
    }

    /** kHz -> RadioClient.Station (FM keeps MHz*100 so frequencyLabel() works). */
    static RadioClient.Station station(int khz, String ps) {
        boolean am = khz < 2000;
        int band = am ? 3 : 0;
        int freq = am ? khz : khz / 10;
        return new RadioClient.Station(band, freq, ps);
    }

    /** RadioClient.Station -> kHz, what gotoFreq() expects. */
    static int toKhz(RadioClient.Station s) {
        return s.isAm() ? s.frequency : s.frequency * 10;
    }

    private static void recycle(Parcel p) {
        if (p != null) p.recycle();
    }

    private Parcel send(int code, Integer arg) {
        IBinder b = binder;
        if (b == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESC);
            if (arg != null) data.writeInt(arg);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply;
        } catch (Exception e) {
            reply.recycle();
            return null;
        } finally {
            data.recycle();
        }
    }
}
