package com.ghost.assist.core;

import android.app.Application;
import android.content.SharedPreferences;

import com.ghost.assist.BuildConfig;

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
    private static final String KEY_OVERLAY = "ov";        // overlay enabled
    private static final String KEY_SERVER_PORT = "sp";    // server port

    // P21 触发器开关（默认值：B1 关 / B2 开 / B5 开 / 朋友圈红点 开 / 更新红点 开）
    private static final String KEY_B1_SHAKE = "b1";
    private static final String KEY_B2_FG    = "b2";
    private static final String KEY_B5_SCREEN= "b5";
    private static final String KEY_MRD      = "mrd";  // moments red dot
    private static final String KEY_URD      = "urd";  // update red dot
    private static final String KEY_MGI      = "mgi";  // moments group-visible icon (M6a)
    private static final String KEY_HUF      = "huf";  // 整包/手动点「检查更新」版本升级冻结 (fl4.o.Wg/Bg)
    private static final String KEY_AHU      = "ahu";  // 远程自动热更新(Tinker)冻结开关（D-033 默认关=放行）
    private static final String KEY_HUL      = "hul";  // hot-update timeline (最近10条·本机采集)

    // One-time migration marker: "mv2" = migrated from old DEV-default to PROD-default.
    private static final String KEY_MIG_V2 = "mv2";

    /**
     * 盗版引流 / 联系客服落地页（PiracyNotice 弹窗 + DebugServer 面板）。
     * 指向 miyou.pro 客服接待系统的邀请落地页（role B，另一个 AI 维护）：
     * 进页 → 手动输验证码 → 接客服，爬虫爬不了。授权服务器域名（zxmqq.shop）
     * 不在这里——那条走 SO 加密引导段（见 docs/HONEYPOT_蜜罐设计.md §4.5）。
     */
    public static final String SHOP_URL = "https://miyou.pro/miyou-n5afqrli";

    // ── S2 真锁信封 — 服务器接入（Phase 1D-server）────────────────
    // 传输强制 HTTPS（信封里的短命 key 材料 k 不得走明文）。
    public static final String GUARD_PRODUCT_ID      = "quantum_wechat";
    public static final String GUARD_PRODUCT_VERSION = BuildConfig.GUARD_PRODUCT_VERSION;
    // 发行线主索引：随 flavor 注入（官替 android_8071 / 共存 android_8071_coexist），不再硬编码。
    // 客户端上报 + 服务器 release_lines + registry_cipher 折的 S_rel + 卡密 必须同源（发版前对齐）。
    public static final String GUARD_RELEASE_ID      = BuildConfig.GUARD_RELEASE_ID;

    // C2：授权服务器域名不再以明文常量留在这里（grep/strings 一搜就出）。
    // 域名加密在 SO 的 cert-only 引导段（native_core/bootstrap_endpoints.json →
    // bootstrap_cipher.inc），运行时经 NativeBridge.getEndpoint 解出。
    // 顺序：primary → backup1 → backup2，EnvelopeClient 逐台 fallback。
    private static final String[] GUARD_ENDPOINT_KEYS = { "primary", "backup1", "backup2" };

    /**
     * 真锁授权服务器候选列表（从 SO 引导段解密读取，按序 fallback）。
     * 硬 fail-closed：SO 缺失 / 引导段散沙（重打包 / 证书不符）→ 返回空数组 →
     * 无服务器可连（重打包的盗版包连不上服务器，正是反盗版要的效果）。
     * 只接受 https:// 开头的条目（与 EnvelopeClient 强制 HTTPS 对齐）。
     */
    public static String[] guardServerList() {
        java.util.ArrayList<String> list = new java.util.ArrayList<>(GUARD_ENDPOINT_KEYS.length);
        for (String k : GUARD_ENDPOINT_KEYS) {
            String url = NativeBridge.getEndpoint(k);
            if (url != null && url.startsWith("https://") && !list.contains(url)) {
                list.add(url);
            }
        }
        return list.toArray(new String[0]);
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

    // --- Debug-gate facade -------------------------------------------------
    // One named outlet per intent so call sites stop hand-rolling
    // BuildConfig.DEBUG / isDebugEnabled() (and OR-ing them). Audit/red-team
    // can grep these three names to enumerate the release debug surface.
    /**
     * Compile-time gate: true only in debug builds. R8 (release uses
     * proguard-android-optimize) inlines BuildConfig.DEBUG=false and strips the
     * guarded branch — use for anything that must never reach a customer build
     * (self-tests, KDF vectors, forced-state entry points).
     */
    public static boolean isDevBuild()     { return BuildConfig.DEBUG; }

    /**
     * Runtime gate: DEV/HONEY diagnostic mode. May be true in a release build —
     * use for diagnostics that should react to the running mode, not the build.
     */
    public static boolean isDiagnostics()  { return getInstance().isDebugEnabled(); }

    /**
     * Debug build OR runtime diagnostics — for surfaces startable either way
     * (e.g. the local DebugServer). Replaces the hand-written
     * {@code BuildConfig.DEBUG || isDebugEnabled()}.
     */
    public static boolean isDebugSurface() { return isDevBuild() || isDiagnostics(); }

    // --- Local dev mode: intercept but skip real hooking ---
    public boolean isLocalDevMode() { return mLocalDevMode; }
    public void setLocalDevMode(boolean enabled) {
        mLocalDevMode = enabled;
        mPrefs.edit().putBoolean(KEY_LOCAL_DEV, enabled).apply();
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

    // 整包 / 手动点「检查更新」的版本升级冻结（fl4.o.Wg 查更 / fl4.o.Bg 装包弹框）。
    // 默认开 = 继续挡住「手动点更新换版本」（防客户升级把重打包版本换掉）。保持 2026-07-05 之前行为。
    public boolean isHotFreezeEnabled() { return mPrefs.getBoolean(KEY_HUF, true); }
    public void setHotFreezeEnabled(boolean v) { mPrefs.edit().putBoolean(KEY_HUF, v).apply(); }

    // 官方【远程自动热更新（Tinker）】冻结开关：p53.j.b / m53.d0.j / m53.d0.d。
    // 2026-07-05（D-033）用户要求关闭对远程自动热更新的拦截 → 默认 false = 放行
    // （HotUpdateFreeze Tinker 三钩走 observe 分支：只 log 不拦）。需恢复拦截改回默认 true。
    public boolean isAutoHotUpdateFreezeEnabled() { return mPrefs.getBoolean(KEY_AHU, false); }
    public void setAutoHotUpdateFreezeEnabled(boolean v) { mPrefs.edit().putBoolean(KEY_AHU, v).apply(); }

    // 官方热更新时间线（本机 only · 最近10条环形 · 零上报）：只记「真有货」事件，供 adb 采集。
    public synchronized void recordHotUpdate(String tag) {
        if (mPrefs == null) return;
        String ts = new java.text.SimpleDateFormat("MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String prev = mPrefs.getString(KEY_HUL, "");
        if (!prev.isEmpty()) java.util.Collections.addAll(lines, prev.split("\n"));
        lines.add(ts + " " + tag);
        while (lines.size() > 10) lines.remove(0);
        mPrefs.edit().putString(KEY_HUL, android.text.TextUtils.join("\n", lines)).apply();
    }

    public String getHotUpdateLog() {
        return (mPrefs == null) ? "" : mPrefs.getString(KEY_HUL, "");
    }
}
