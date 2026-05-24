package com.ghost.assist.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hot-reload event bus — drives HIDDEN/VISIBLE UI refresh without restarting WeChat.
 *
 * StateMachine calls notifyHiddenChanged() after every state transition.
 * Each Filter registers a RefreshCallback to update its own adapter/list.
 *
 * Mode BALANCED   — main-thread post, immediate.
 * Mode PERFORMANCE — 400ms debounce (postDelayed + removeCallbacks).
 *
 * Constraint: minSdk 27 → cannot use Handler.postDelayed(Runnable, Object, long) (API 28).
 * Uses a stored volatile Runnable for debounce instead.
 */
public class RefreshBus {

    public enum Mode { BALANCED, PERFORMANCE }

    public interface RefreshCallback {
        /** Called on the main thread after a HIDDEN/VISIBLE state change. */
        void onHiddenChanged(boolean hidden);
    }

    private static final String TAG = "NCL";
    private static final int PERF_DELAY_MS = 400;

    private static final RefreshBus sInstance = new RefreshBus();

    private final CopyOnWriteArrayList<Entry> mCallbacks = new CopyOnWriteArrayList<>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private Mode mMode = Mode.BALANCED;
    private volatile Runnable mPendingDispatch;

    private static final class Entry {
        final String name;
        final RefreshCallback cb;
        Entry(String n, RefreshCallback c) { name = n; cb = c; }
    }

    private RefreshBus() {}

    public static RefreshBus getInstance() { return sInstance; }

    public void setMode(Mode mode) { mMode = mode; }
    public Mode getMode() { return mMode; }

    /** Register a named callback. Duplicate names are silently ignored. */
    public void register(String name, RefreshCallback cb) {
        for (Entry e : mCallbacks) {
            if (e.name.equals(name)) return;
        }
        mCallbacks.add(new Entry(name, cb));
        Log.i(TAG, "[BUS] registered " + name);
    }

    /** Called by StateMachine after every HIDDEN/VISIBLE transition. Thread-safe. */
    public synchronized void notifyHiddenChanged(final boolean hidden) {
        Log.i(TAG, "[BUS] hiddenChanged hidden=" + hidden);
        if (mMode == Mode.PERFORMANCE) {
            if (mPendingDispatch != null) mMain.removeCallbacks(mPendingDispatch);
            mPendingDispatch = new Runnable() {
                @Override public void run() {
                    mPendingDispatch = null;
                    dispatch(hidden);
                }
            };
            mMain.postDelayed(mPendingDispatch, PERF_DELAY_MS);
        } else {
            mMain.post(new Runnable() {
                @Override public void run() { dispatch(hidden); }
            });
        }
    }

    private void dispatch(boolean hidden) {
        for (Entry e : mCallbacks) {
            Log.i(TAG, "[BUS] refresh " + e.name);
            try {
                e.cb.onHiddenChanged(hidden);
            } catch (Throwable t) {
                Log.w(TAG, "[BUS] refresh " + e.name + " err: " + t);
            }
        }
    }
}
