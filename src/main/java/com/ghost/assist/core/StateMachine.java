package com.ghost.assist.core;

import android.app.Application;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 3-state machine: VISIBLE / HIDDEN / UNLOCKING.
 * No failure counter (per CLAUDE.md §5.1).
 * Persisted via Bridge (MMKV namespace g_<seed>).
 *
 * State flow:
 *   VISIBLE ──(enter/toggle)──▶ HIDDEN ──(exit/toggle)──▶ VISIBLE
 *
 * Global search password (default 111111, six digits):
 *   主界面放大镜 → 全局搜索页输入完整密码 → 不需回车 → 自动回主界面 + exitHidden（密友显形）
 *   仅 HIDDEN 态响应；乱输不会触发状态变化。
 */
public class StateMachine {

    private static final String TAG = "NCL";
    private static final String KEY_STATE = "smst";  // short hash
    private static final String KEY_PWD = "smpw";     // unlock password

    public enum State {
        VISIBLE(0, "显形"),
        HIDDEN(1, "隐藏"),
        UNLOCKING(2, "解锁中");

        public final int code;
        public final String label;
        State(int code, String label) { this.code = code; this.label = label; }
    }

    public interface StateListener {
        void onStateChanged(State oldState, State newState);
    }

    private static final StateMachine sInstance = new StateMachine();
    private State mState = State.VISIBLE;
    private boolean mActive = false;           // true = hidden mode active
    private static final String DEFAULT_PASSWORD = "111111";
    private String mPassword = DEFAULT_PASSWORD;
    private final List<StateListener> mListeners = new CopyOnWriteArrayList<>();

    public static StateMachine getInstance() { return sInstance; }

    public void init(Application app) {
        // Restored from restoreState() after Bridge is ready
        Log.i(TAG, "[SM] init");
    }

    /** Restore state from persistent storage after Bridge is ready */
    public void restoreState() {
        // F-27: startup always HIDDEN — native state takes priority.
        // Never restore VISIBLE or UNLOCKING across cold-start boundaries.
        int savedCode = Bridge.getInstance().getInt(KEY_STATE, State.HIDDEN.code);
        State restored = State.HIDDEN;
        for (State s : State.values()) {
            if (s.code == savedCode) { restored = s; break; }
        }
        if (restored != State.HIDDEN) {
            Log.i(TAG, "[SM] restoreState: saved=" + restored.label + " → forced HIDDEN (F-27)");
            restored = State.HIDDEN;
        }
        mState = restored;
        mActive = true; // HIDDEN is always active
        mPassword = Bridge.getInstance().getString(KEY_PWD, DEFAULT_PASSWORD);
        Log.i(TAG, "[SM] restored state=" + mState.label);
    }

    // --- State access ---
    public State getState() { return mState; }
    public String getStateName() { return mState.label; }
    public int getStateCode() { return mState.code; }

    /** true when hidden mode is active */
    /**
     * 三层门控（顺序不可颠倒）：
     *   1. isVipAuthorized()      — 授权门（v1 stub=true，v2 接 LicenseGate）
     *   2. isFeatureEnabled()     — 密友功能总开关（Bridge MMKV key="f1"）
     *   3. mActive                — 状态机 HIDDEN 态
     */
    public boolean isActive() {
        return isVipAuthorized()
            && Bridge.getInstance().isFeatureEnabled()
            && mActive;
    }

    /** v1 stub — 始终授权；v2 替换为 LicenseGate.check() */
    public boolean isVipAuthorized() { return true; }
    /** true when module is disabled (non-prod mode) */
    public boolean isDisabled() { return !AppConfig.getInstance().isProdMode(); }
    /**
     * High-performance mode: skip the white-flash overlay on resume.
     * v1 stub = false (always show overlay for maximum privacy).
     * v2: user-configurable via SettingsEntry toggle.
     */
    public boolean isHighPerfMode() { return false; }

    // --- State transitions ---

    /** Enter hidden mode (from any state) */
    public synchronized void enterHidden() {
        if (mState == State.HIDDEN) return;
        State old = mState;
        mState = State.HIDDEN;
        mActive = true;
        persist();
        notifyListeners(old, mState);
        Log.i(TAG, "[SM] enterHidden");
    }

    /** Exit hidden mode → visible */
    public synchronized void exitHidden(boolean isBack) {
        if (mState == State.VISIBLE) return;
        State old = mState;
        mState = State.VISIBLE;
        mActive = false;
        persist();
        notifyListeners(old, mState);
        Log.i(TAG, "[SM] exitHidden isBack=" + isBack);
    }

    /** Begin unlocking from hidden mode (global search password) */
    public synchronized void beginUnlock() {
        if (mState != State.HIDDEN) return;
        if (mState == State.UNLOCKING) return;
        State old = mState;
        mState = State.UNLOCKING;
        notifyListeners(old, mState);
        Log.i(TAG, "[SM] beginUnlock");
    }

    /** Attempt password — success → visible, fail → hidden */
    public synchronized boolean attemptUnlock(String input) {
        if (mState != State.UNLOCKING) return false;
        if (mPassword.equals(input)) {
            State old = mState;
            mState = State.VISIBLE;
            mActive = false;
            persist();
            notifyListeners(old, mState);
            Log.i(TAG, "[SM] unlock OK");
            return true;
        }
        // Fail → go back to hidden. SearchUnlock normally calls this only after exact match,
        // but keep the invariant correct for debug API/manual calls.
        State old = mState;
        mState = State.HIDDEN;
        mActive = true;
        notifyListeners(old, mState);
        Log.i(TAG, "[SM] unlock FAIL");
        return false;
    }

    /** Cancel unlocking */
    public synchronized void cancelUnlock() {
        if (mState != State.UNLOCKING) return;
        State old = mState;
        mState = State.HIDDEN;
        mActive = true;
        notifyListeners(old, mState);
        Log.i(TAG, "[SM] cancelUnlock");
    }

    /** Toggle visible/hidden (for shake trigger etc.) */
    public synchronized void toggle() {
        if (mState == State.HIDDEN) {
            exitHidden(false);
        } else if (mState == State.VISIBLE) {
            enterHidden();
        }
    }

    // --- Password management ---
    public String getPassword() { return mPassword; }

    public void setPassword(String newPwd) {
        mPassword = newPwd;
        Bridge.getInstance().putString(KEY_PWD, newPwd);
    }

    // --- Event bus ---
    public void addListener(StateListener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    /** Named variant — logs registration for diagnostics. */
    public void addListener(String name, StateListener listener) {
        addListener(listener);
        Log.i(TAG, "[SM] listener registered " + name);
    }

    public void removeListener(StateListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners(State oldState, State newState) {
        Log.i(TAG, "[SM] notify old=" + oldState.label + " new=" + newState.label);
        for (StateListener l : mListeners) {
            try { l.onStateChanged(oldState, newState); } catch (Throwable e) {
                Log.e(TAG, "[SM] listener error: " + e.getMessage());
            }
        }
        // Drive hot-reload: RefreshBus dispatches to all registered Filter callbacks.
        RefreshBus.getInstance().notifyHiddenChanged(newState != State.VISIBLE);
    }

    // --- Persist ---
    private void persist() {
        Bridge.getInstance().putInt(KEY_STATE, mState.code);
    }
}
