package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

/**
 * Client for the HCN factory Bluetooth music service (com.hcn.bluetoothservice), used on
 * Corsa-style units. Registers IMusicCallback for metadata/state and sends AVRCP commands.
 */
final class HcnBt implements ServiceConnection {

    static final String PACKAGE = "com.hcn.bluetoothservice";
    private static final String SERVICE = "com.hcn.bluetooth.service.BluetoothMusicService";
    private static final String DESC = "com.hcn.bluetooth.service.IBluetoothMusicService";
    private static final String CB_DESC = "com.hcn.bluetooth.service.IMusicCallback";

    static final int PLAY = 0;
    static final int PAUSE = 1;
    static final int TOGGLE = 2;
    static final int STOP = 3;
    static final int NEXT = 4;
    static final int PREVIOUS = 5;

    private static final int TX_IS_A2DP_CONNECTED = 1;
    private static final int TX_IS_A2DP_PLAYING = 3;
    private static final int TX_SEND_AVRCP = 5;
    private static final int TX_REGISTER_CB = 10;
    private static final int TX_UNREGISTER_CB = 11;
    private static final int TX_GET_ID3 = 12;
    private static final int STATE_PLAYING = 3;

    interface Listener {
        void onHcnBtChanged();
    }

    private IBinder binder;
    private boolean registered;
    private Listener listener;

    boolean a2dp;
    boolean playing;
    String title = "";
    String artist = "";
    String album = "";

    private int state;
    private long position;
    private long duration;
    private long updateTime;
    private float speed = 1f;

    private final Handler main = new Handler(Looper.getMainLooper());

    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                switch (code) {
                    case 1: // A2DP connection state
                        data.enforceInterface(CB_DESC);
                        a2dp = data.readInt() == 2;
                        if (!a2dp) clear();
                        break;
                    case 2: // unused value from the service
                        data.enforceInterface(CB_DESC);
                        data.readInt();
                        break;
                    case 3: // ID3
                        data.enforceInterface(CB_DESC);
                        title = nz(data.readString());
                        artist = nz(data.readString());
                        album = nz(data.readString());
                        break;
                    case 4: // playback state
                        data.enforceInterface(CB_DESC);
                        if (data.readInt() != 0) {
                            state = data.readInt();
                            position = data.readLong();
                            duration = data.readLong();
                            speed = data.readFloat();
                            updateTime = data.readLong();
                            data.readLong();
                            playing = state == STATE_PLAYING;
                        }
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                if (reply != null && (flags & 1) == 0) reply.writeNoException();
                changed();
                return true;
            } catch (RuntimeException e) {
                Log.w("HcnBt", "callback " + code, e);
                return true;
            } catch (RemoteException e) {
                return false;
            }
        }
    };

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

    void bind(Context c, Listener l) {
        listener = l;
        if (isBound()) return;
        try {
            c.bindService(new Intent(SERVICE).setComponent(new ComponentName(PACKAGE, SERVICE)),
                    this, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) { }
    }

    void unbind(Context c) {
        if (binder != null && registered) call(TX_UNREGISTER_CB, true, null);
        try {
            c.unbindService(this);
        } catch (IllegalArgumentException ignored) { }
        binder = null;
        registered = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = service;
        registered = call(TX_REGISTER_CB, true, null);
        refresh();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
        registered = false;
        playing = false;
        a2dp = false;
        changed();
    }

    /** Reads connection, playback and ID3 off the UI thread. */
    void refresh() {
        new Thread(() -> {
            Parcel p1 = Parcel.obtain();
            if (transact(TX_IS_A2DP_CONNECTED, false, p1)) a2dp = p1.readInt() != 0;
            p1.recycle();

            Parcel p2 = Parcel.obtain();
            if (transact(TX_IS_A2DP_PLAYING, false, p2) && p2.readInt() != 0) {
                playing = true;
                state = STATE_PLAYING;
            }
            p2.recycle();

            Parcel p3 = Parcel.obtain();
            if (transact(TX_GET_ID3, false, p3)) {
                String[] id3 = p3.createStringArray();
                if (id3 != null && id3.length >= 3) {
                    title = nz(id3[0]);
                    artist = nz(id3[1]);
                    album = nz(id3[2]);
                }
            }
            p3.recycle();
            changed();
        }).start();
    }

    void command(final int command) {
        new Thread(() -> call(TX_SEND_AVRCP, false, command)).start();
    }

    float progress() {
        if (duration <= 0) return -1f;
        long pos = position;
        if (playing) pos += (long) ((SystemClock.elapsedRealtime() - updateTime) * speed);
        return Math.max(0f, Math.min(1f, pos / (float) duration));
    }

    long durationMs() {
        return duration;
    }

    long positionMs() {
        long pos = position;
        if (playing) pos += (long) ((SystemClock.elapsedRealtime() - updateTime) * speed);
        if (duration > 0) pos = Math.min(duration, pos);
        return Math.max(0L, pos);
    }

    private void clear() {
        playing = false;
        album = "";
        artist = "";
        title = "";
        position = 0;
        duration = 0;
    }

    private void changed() {
        final Listener l = listener;
        if (l != null) main.post(l::onHcnBtChanged);
    }

    private boolean call(int code, boolean binderArg, Integer intArg) {
        Parcel reply = Parcel.obtain();
        try {
            return transact(code, binderArg, intArg, reply);
        } finally {
            reply.recycle();
        }
    }

    private boolean transact(int code, boolean binderArg, Parcel reply) {
        return transact(code, binderArg, null, reply);
    }

    private boolean transact(int code, boolean binderArg, Integer intArg, Parcel reply) {
        IBinder b = binder;
        if (b == null) return false;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESC);
            if (binderArg) data.writeStrongBinder(callback);
            if (intArg != null) data.writeInt(intArg);
            b.transact(code, data, reply, 0);
            reply.readException();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            data.recycle();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
