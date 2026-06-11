package com.ghost.assist.core;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * DEV / PROD / HONEY tri-state configuration.
 * DEV  = debug tools on, local dev mode on (no real hooking)
 * PROD = debug tools off, full hooking active
 * HONEY= debug tools on, full hooking active, telemetry extra (v2+)
 */
public class AppConfig {

    public enum Mode {
        DEV, PROD, HONEY
    }

    private static final String PREFS_NAME = "ncl_cfg";  // seed-based
    private static final String KEY_MODE = "md";           // short hash
    private static final String KEY_LOCAL_DEV = "ld";      // local dev mode
    private static final String KEY_KILL = "kl";           // kill switch
    private static final String KEY_OVERLAY = "ov";        // overlay enabled
    private static final String KEY_SERVER_PORT = "sp";    // server port

    // P21 触发器开关（默认值：B1 关 / B2 开 / B5 开 / 朋友圈红点 开 / 更新红点 开）
    private static final String KEY_B1_SHAKE = "b1";
    private static final String KEY_B2_FG    = "b2";
    private static final String KEY_B5_SCREEN= "b5";
    private static final String KEY_MRD      = "mrd";  // moments red dot
    private static final String KEY_URD      = "urd";  // update red dot
    private static final String KEY_MGI      = "mgi";  // moments group-visible icon (M6a)

    // One-time migration marker: "mv2" = migrated from old DEV-default to PROD-default.
    private static final String KEY_MIG_V2 = "mv2";

    /** Official purchase URL shown in PiracyNotice and DebugServer dashboard. */
    public static final String SHOP_URL = "https://zxmqq.shop";

    // ── S2 真锁信封 — 服务器接入（Phase 1D-server）────────────────
    // 传输强制 HTTPS（信封里的短命 key 材料 k 不得走明文）。
    // 备机本轮不部署：BACKUP 留空，EnvelopeClient 写成「列表 + fallback」结构，
    // 以后开备机只需把 BACKUP 填上 https://miyou.lol，不改客户端代码。
    public static final String GUARD_SERVER_PRIMARY = "https://zxmqq.shop";
    public static final String GUARD_SERVER_BACKUP  = "";   // 备机槽（留空 = 仅主机）
    public static final String GUARD_PRODUCT_ID      = "quantum_wechat";
    public static final String GUARD_RELEASE_ID      = "android_8071";

    /** 真锁服务器候选列表（按序 fallback；空串自动跳过）。 */
    public static String[] guardServerList() {
        if (GUARD_SERVER_BACKUP == null || GUARD_SERVER_BACKUP.isEmpty()) {
            return new String[]{ GUARD_SERVER_PRIMARY };
        }
        return new String[]{ GUARD_SERVER_PRIMARY, GUARD_SERVER_BACKUP };
    }

    private static final AppConfig sInstance = new AppConfig();
    private SharedPreferences mPrefs;
    private Mode mMode = Mode.PROD;   // safe in-memory default before init()
    private boolean mLocalDevMode = false;
    private int mServerPort = 8080;
    private android.content.Context mAppCtx;   // S2: 真锁激活/设备号取值用

    public static AppConfig getInstance() { return sInstance; }

    /** 应用上下文（取设备号 / 初始化 EnvelopeStore 用）；init 后非空。 */
    public android.content.Context getAppContext() { return mAppCtx; }

    public void init(Application app) {
        mAppCtx = app.getApplicationContext();
        mPrefs = app.getSharedPreferences(PREFS_NAME, 0);

        // Migration mv2: old installs persisted "DEV" as default; upgrade to "PROD" once.
        if (!mPrefs.getBoolean(KEY_MIG_V2, false)) {
            SharedPreferences.Editor ed = mPrefs.edit();
            if ("DEV".equals(mPrefs.getString(KEY_MODE, ""))) {
                ed.putString(KEY_MODE, "PROD");
                android.util.Log.i("NCL", "[cfg] mv2 migrate DEV→PROD");
            }
            ed.putBoolean(KEY_MIG_V2, true).apply();
        }

        String modeStr = mPrefs.getString(KEY_MODE, "PROD");
        try { mMode = Mode.valueOf(modeStr); } catch (Exception e) { mMode = Mode.PROD; }
        mLocalDevMode = mPrefs.getBoolean(KEY_LOCAL_DEV, mMode == Mode.DEV);
        mServerPort = mPrefs.getInt(KEY_SERVER_PORT, 8080);
    }

    // --- Mode ---
    public Mode getMode() { return mMode; }
    public void setMode(Mode mode) {
        mMode = mode;
        mPrefs.edit().putString(KEY_MODE, mode.name()).apply();
        // Auto-toggle local dev mode with mode change
        if (mode == Mode.DEV) setLocalDevMode(true);
        if (mode == Mode.PROD) setLocalDevMode(false);
    }

    public boolean isDebugEnabled() { return mMode == Mode.DEV || mMode == Mode.HONEY; }
    public boolean isProdMode() { return mMode == Mode.PROD; }
    public boolean isDevMode()  { return mMode == Mode.DEV; }

    // --- Local dev mode: intercept but skip real hooking ---
    public boolean isLocalDevMode() { return mLocalDevMode; }
    public void setLocalDevMode(boolean enabled) {
        mLocalDevMode = enabled;
        mPrefs.edit().putBoolean(KEY_LOCAL_DEV, enabled).apply();
    }

    // --- Kill switch (v1 placeholder) ---
    public boolean isKillSwitch() { return mPrefs.getBoolean(KEY_KILL, false); }
    public void setKillSwitch(boolean killed) {
        mPrefs.edit().putBoolean(KEY_KILL, killed).apply();
    }

    // --- Overlay ---
    public boolean isOverlayEnabled() { return mPrefs.getBoolean(KEY_OVERLAY, true); }
    public void setOverlayEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_OVERLAY, enabled).apply();
    }

    // --- Server port ---
    public int getServerPort() { return mServerPort; }
    public void setServerPort(int port) {
        mServerPort = port;
        mPrefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    // --- P21 触发器 + 小红点开关 ---
    public boolean isB1Enabled() { return mPrefs.getBoolean(KEY_B1_SHAKE, false); }  // 默认关
    public void setB1Enabled(boolean v) { mPrefs.edit().putBoolean(KEY_B1_SHAKE, v).apply(); }

    public boolean isB2Enabled() { return mPrefs.getBoolean(KEY_B2_FG, true); }      // 默认开
    public void setB2Enabled(boolean v) { mPrefs.edit().putBoolean(KEY_B2_FG, v).apply(); }

    public boolean isB5Enabled() { return mPrefs.getBoolean(KEY_B5_SCREEN, true); }  // 默认开
    public void setB5Enabled(boolean v) { mPrefs.edit().putBoolean(KEY_B5_SCREEN, v).apply(); }

    public boolean isMomentsRedDotEnabled() { return mPrefs.getBoolean(KEY_MRD, true); } // 默认开
    public void setMomentsRedDotEnabled(boolean v) { mPrefs.edit().putBoolean(KEY_MRD, v).apply(); }

    public boolean isUpdateRedDotEnabled() { return mPrefs.getBoolean(KEY_URD, true); }  // 默认开
    public void setUpdateRedDotEnabled(boolean v) { mPrefs.edit().putBoolean(KEY_URD, v).apply(); }

    // M6a 朋友圈「可见分组」图标隐藏（默认开；纯开关驱动，独立于 HIDDEN 状态）
    public boolean isMomentsGroupIconEnabled() { return mPrefs.getBoolean(KEY_MGI, true); }
    public void setMomentsGroupIconEnabled(boolean v) { mPrefs.edit().putBoolean(KEY_MGI, v).apply(); }
}
