package com.ghost.assist.core;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Storage bridge — thin wrapper over SharedPreferences.
 * Mimics MMKV namespace convention: g_<seed4> with short-hash keys.
 *
 * Upgrade path: swap to MMKV when bundled; same API surface.
 * §5.7: namespace = g_<seed4>, keys = 4-char short hash.
 */
public class Bridge {

    private static final String NAMESPACE = "g_a7f2"; // seed-based

    private static final Bridge sInstance = new Bridge();
    private SharedPreferences mPrefs;
    private Application mApp;

    public static Bridge getInstance() { return sInstance; }

    public void init(Application app) {
        mApp = app;
        mPrefs = app.getSharedPreferences(NAMESPACE, Context.MODE_PRIVATE);
        // 跨进程迁移：把当前通知策略落一份到文件，供 :push 进程读取（SP MODE_PRIVATE 不跨进程）。
        writeCrossProcessPolicy(getNotifyPolicy().name());
    }

    // --- String ---
    public void putString(String key, String value) {
        mPrefs.edit().putString(key, value).apply();
    }

    public String getString(String key, String def) {
        return mPrefs.getString(key, def);
    }

    // --- Int ---
    public void putInt(String key, int value) {
        // 状态机键走 commit 强同步（防止杀进程时丢盘 → 冷启动恢复错误）
        // 其他键走 apply 异步（性能优先）
        if ("smst".equals(key)) {
            mPrefs.edit().putInt(key, value).commit();
        } else {
            mPrefs.edit().putInt(key, value).apply();
        }
    }

    public int getInt(String key, int def) {
        return mPrefs.getInt(key, def);
    }

    // --- Boolean ---
    public void putBool(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }

    public boolean getBool(String key, boolean def) {
        return mPrefs.getBoolean(key, def);
    }

    // --- Long ---
    public void putLong(String key, long value) {
        mPrefs.edit().putLong(key, value).apply();
    }

    public long getLong(String key, long def) {
        return mPrefs.getLong(key, def);
    }

    // --- Set ---
    public void putStringSet(String key, java.util.Set<String> values) {
        mPrefs.edit().putStringSet(key, values).apply();
    }

    public java.util.Set<String> getStringSet(String key) {
        return mPrefs.getStringSet(key, new java.util.HashSet<>());
    }

    // --- 防撤回开关 (key: "arc", default: true) ---
    private static final String KEY_ANTI_RECALL = "arc";

    /** 防撤回功能开关。v1 默认 true（对所有消息生效，不限密友）。 */
    public boolean isAntiRecallEnabled() {
        return getBool(KEY_ANTI_RECALL, true);
    }

    public void setAntiRecallEnabled(boolean enabled) {
        putBool(KEY_ANTI_RECALL, enabled);
    }

    // --- 显示密友未读消息数 开关 (key: "shu", default: false) ---
    // false（默认）= 顶部「微信(N)」+ 底 tab 红点 扣掉密友未读（隐藏态默认行为，藏得更干净）
    // true        = 不扣，密友未读照常计入总数显示（PushFilter.UNREADFIX 读此位短路）
    private static final String KEY_SHOW_HIDDEN_UNREAD = "shu";

    public boolean isShowHiddenUnread() {
        return getBool(KEY_SHOW_HIDDEN_UNREAD, false);
    }

    public void setShowHiddenUnread(boolean enabled) {
        putBool(KEY_SHOW_HIDDEN_UNREAD, enabled);
    }

    // --- 密友功能总开关 (key: "f1", default: true) ---
    private static final String KEY_FEATURE = "f1";

    /** 密友功能总开关。关闭时所有 Filter 短路，微信恢复原始行为。 */
    public boolean isFeatureEnabled() {
        return getBool(KEY_FEATURE, true);
    }

    public void setFeatureEnabled(boolean enabled) {
        putBool(KEY_FEATURE, enabled);
    }

    // --- 启动防层模式 (key: "hpm", default: false 均衡模式) ---
    // false = 均衡：冷启动 / 锁屏亮屏显示白色遮罩 (ConvFilter.showColdStartOverlay)
    // true  = 高性能：跳过遮罩、响应更快、要求微信常驻后台不被杀
    // SETTINGS_UI_V2 §7.1 接入
    private static final String KEY_HIGH_PERF_MODE = "hpm";

    public boolean isHighPerfMode() {
        return getBool(KEY_HIGH_PERF_MODE, false);
    }

    public void setHighPerfMode(boolean enabled) {
        putBool(KEY_HIGH_PERF_MODE, enabled);
    }

    // --- 通知策略 NotifyPolicy (key: "nfyp", default: OFF) ---
    // OFF   = 完全静默（当前 PushFilter 行为）
    // VIBRATE = 震动但无声（Phase 2 实现）
    // SOUND   = 正常铃声（Phase 2 实现）
    //
    // v1: 存储已就绪；实际 VIBRATE/SOUND 动作 Phase 2 接入 PushFilter。

    public enum NotifyPolicy {
        OFF, VIBRATE, SOUND;

        public static NotifyPolicy fromString(String s) {
            if (s == null) return OFF;
            switch (s.toUpperCase()) {
                case "VIBRATE": return VIBRATE;
                case "SOUND":   return SOUND;
                default:        return OFF;
            }
        }
    }

    private static final String KEY_NOTIFY_POLICY = "nfyp";
    private static final String KEY_CUSTOM_SOUND  = "csnd";

    public NotifyPolicy getNotifyPolicy() {
        return NotifyPolicy.fromString(getString(KEY_NOTIFY_POLICY, "OFF"));
    }

    public void setNotifyPolicy(NotifyPolicy policy) {
        putString(KEY_NOTIFY_POLICY, policy.name());
        writeCrossProcessPolicy(policy.name());
    }

    // --- 跨进程通知策略文件：主进程写、:push 进程读 ---
    // 背景：Bridge 用 SharedPreferences MODE_PRIVATE，不跨进程；:push 进程也不初始化 Bridge
    //       （铁律 30 :push 只读 NativeBridge），所以 :push 拿不到用户选的静默/震动。
    //       这里把 nfyp 单独落一个文件到 app filesDir（主进程与 :push 同 uid、同目录），
    //       :push 每条密友消息到达时直接读文件拿 live 值。读取仅发生在密友消息命中后（低频）。
    private static final String NFYP_XPROC_FILE = "g_nfyp";

    private void writeCrossProcessPolicy(String name) {
        if (mApp == null || name == null) return;
        try {
            java.io.File f = new java.io.File(mApp.getFilesDir(), NFYP_XPROC_FILE);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(name.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable ignored) {}
    }

    /**
     * :push 进程用：直接读跨进程文件拿 live 通知策略，不依赖 SharedPreferences 缓存。
     * 文件不存在/读失败 → OFF（静默，安全默认）。
     */
    public static NotifyPolicy readPolicyCrossProcess(android.content.Context ctx) {
        if (ctx == null) return NotifyPolicy.OFF;
        try {
            java.io.File f = new java.io.File(ctx.getFilesDir(), NFYP_XPROC_FILE);
            if (!f.exists()) return NotifyPolicy.OFF;
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            int n = fis.read(b);
            fis.close();
            if (n <= 0) return NotifyPolicy.OFF;
            return NotifyPolicy.fromString(new String(b, 0, n, "UTF-8"));
        } catch (Throwable t) {
            return NotifyPolicy.OFF;
        }
    }

    // --- 语音/视频通话通知策略 CallNotifyPolicy (key: "cnfy", default: OFF) ---
    // 来电只有两态：OFF = 静默（默认），VIBRATE = 震动。
    // 来电永不放铃声（反暴露）；SOUND 不作为来电选项，若误存按震动处理。
    private static final String KEY_CALL_NOTIFY_POLICY = "cnfy";

    public NotifyPolicy getCallNotifyPolicy() {
        return NotifyPolicy.fromString(getString(KEY_CALL_NOTIFY_POLICY, "OFF"));
    }

    public void setCallNotifyPolicy(NotifyPolicy policy) {
        putString(KEY_CALL_NOTIFY_POLICY, policy.name());
    }

    /** Custom ringtone URI for SOUND mode (empty string = use system notification sound). */
    public String getCustomSound() {
        return getString(KEY_CUSTOM_SOUND, "");
    }

    public void setCustomSound(String uri) {
        putString(KEY_CUSTOM_SOUND, uri != null ? uri : "");
    }

    // --- Hidden wxid list (A2 密友列表) ---
    private static final String KEY_HIDDEN_LIST = "hlst";
    // --- Hidden group list (A3 密群列表，`*@chatroom`) ---
    private static final String KEY_HIDDEN_GROUPS = "glst";

    public java.util.Set<String> getWxids() {
        return getStringSet(KEY_HIDDEN_LIST);
    }

    public void addWxid(String wxid) {
        java.util.Set<String> set = new java.util.HashSet<>(getWxids());
        set.add(wxid);
        putStringSet(KEY_HIDDEN_LIST, set);
    }

    public void removeWxid(String wxid) {
        java.util.Set<String> set = new java.util.HashSet<>(getWxids());
        set.remove(wxid);
        putStringSet(KEY_HIDDEN_LIST, set);
    }

    public java.util.Set<String> getGroupIds() {
        return getStringSet(KEY_HIDDEN_GROUPS);
    }

    public void addGroupId(String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        java.util.Set<String> set = new java.util.HashSet<>(getGroupIds());
        set.add(groupId);
        putStringSet(KEY_HIDDEN_GROUPS, set);
    }

    public void removeGroupId(String groupId) {
        if (groupId == null) return;
        java.util.Set<String> set = new java.util.HashSet<>(getGroupIds());
        set.remove(groupId);
        putStringSet(KEY_HIDDEN_GROUPS, set);
    }

    /**
     * A3 真假性判定：微信群 username 形如 `xxxxxxxxxxxxxx@chatroom`。
     * 用于在 Filter 拿到 username 后选库匹配。
     */
    public static boolean isGroupId(String id) {
        return id != null && id.endsWith("@chatroom");
    }

    /**
     * 一行总判定 — A2 ∪ A3。Filter 拿到 username 后调用：
     *   if (StateMachine.isActive() && Bridge.getInstance().shouldHideId(name)) remove;
     */
    public boolean shouldHideId(String id) {
        if (id == null || id.isEmpty()) return false;
        if (isGroupId(id)) {
            return getGroupIds().contains(id);
        }
        return getWxids().contains(id);
    }

    /**
     * 密友 ∪ 密群 的并集快照（不可变拷贝）。
     * Filter 内部「先 isEmpty 早返、再 contains 匹配」的模板换它一个就够。
     * 注：单次调用返回新 Set，调用方应缓存到局部变量复用。
     */
    public java.util.Set<String> allHiddenIds() {
        java.util.Set<String> wxids = getWxids();
        java.util.Set<String> groups = getGroupIds();
        if (groups.isEmpty()) return wxids;
        if (wxids.isEmpty()) return groups;
        java.util.HashSet<String> all = new java.util.HashSet<>(wxids.size() + groups.size());
        all.addAll(wxids);
        all.addAll(groups);
        return all;
    }

    /** 密友 + 密群 合计条数（通知/调试面板用） */
    public int getCount() {
        return getWxids().size() + getGroupIds().size();
    }

    /** 单独的密友计数（A2） */
    public int getWxidCount() {
        return getWxids().size();
    }

    /** 单独的密群计数（A3） */
    public int getGroupCount() {
        return getGroupIds().size();
    }

    // --- 当前登录用户 wxid（D2/D3 清洗用）---
    private static final String KEY_MY_WXID  = "mwxd";
    private static final String KEY_MY_ALIAS = "myal";  // 微信号（搜索框显示用）
    private static final String KEY_MY_NICK  = "mynk";  // 昵称

    public String getMyWxid()  { return getString(KEY_MY_WXID, ""); }
    public void   setMyWxid(String wxid) { putString(KEY_MY_WXID, wxid != null ? wxid.trim() : ""); }

    public String getMyAlias() { return getString(KEY_MY_ALIAS, ""); }
    public void   setMyAlias(String alias) { putString(KEY_MY_ALIAS, alias != null ? alias.trim() : ""); }

    public String getMyNick()  { return getString(KEY_MY_NICK, ""); }
    public void   setMyNick(String nick) { putString(KEY_MY_NICK, nick != null ? nick.trim() : ""); }

    /**
     * Re-read wxid from WeChat's own SharedPreferences (login_weixin_username).
     * Call this after alias is captured to ensure myWxid is also up to date.
     * No-op if mPrefs is not initialised yet.
     */
    public void refreshWxid() {
        // Wxid is populated by SelfProfileCapture or switch_account_preferences listener.
        // This is a lightweight probe — ignore silently if context is not available yet.
        try {
            android.content.Context ctx = mPrefs.getString("__ctx_probe__", null) != null
                    ? null : null; // mPrefs is SharedPreferences, not Context — kept as no-op stub
            // Actual refresh is done via SelfProfileCapture's loginWxid hook path.
        } catch (Throwable ignored) {}
    }

    // --- P4-1 授权绑定（wxid + device）---
    private static final String KEY_LICENSED_WXID = "lwxd";
    private static final String KEY_DEVICE_HASH   = "dvhsh";

    public String getLicensedWxid()            { return getString(KEY_LICENSED_WXID, ""); }
    public void   setLicensedWxid(String wxid) { putString(KEY_LICENSED_WXID, wxid != null ? wxid : ""); }

    public String getDeviceHash()              { return getString(KEY_DEVICE_HASH, ""); }
    public void   setDeviceHash(String hash)   { putString(KEY_DEVICE_HASH, hash != null ? hash : ""); }

    // --- P26C 标签隐藏开关 ---
    private static final String KEY_HIDE_CONTACT_LABEL = "hcl";

    public boolean isHideContactLabelEnabled()           { return getBool(KEY_HIDE_CONTACT_LABEL, false); }
    public void    setHideContactLabelEnabled(boolean v) { putBool(KEY_HIDE_CONTACT_LABEL, v); }

    // --- 隐藏功能入口开关 (key: "hei", default: true) ---
    // 仅控制 SettingsEntry 注入到微信「设置」页顶部的「量子密友设置」入口行的可见性，
    // 不影响状态机、过滤或授权（纯 EntryGate 可见性）。
    // true（默认）= 隐藏态自动隐藏入口（= 现状行为）
    // false        = 隐藏态也显示入口（常显，方便随时打开面板）
    private static final String KEY_HIDE_ENTRY_IN_HIDDEN = "hei";

    public boolean isHideEntryInHidden()           { return getBool(KEY_HIDE_ENTRY_IN_HIDDEN, true); }
    public void    setHideEntryInHidden(boolean v) { putBool(KEY_HIDE_ENTRY_IN_HIDDEN, v); }

    // --- 伪装订位（E2，全局伪造定位）---
    // flon=开关(默认关)；flla/flln=纬度/经度(String 存 double 保精度)；fllb=POI 名(显示用)。
    // 注入点 pz0.h.c(arg2=纬度, arg3=经度) 读 flla/flln；坐标由原生选点页 LocationIntent.d/e 写入。
    private static final String KEY_FAKE_LOC_ON = "flon";
    private static final String KEY_FAKE_LAT    = "flla";
    private static final String KEY_FAKE_LNG    = "flln";
    private static final String KEY_FAKE_LABEL  = "fllb";

    public boolean isFakeLocationEnabled()           { return getBool(KEY_FAKE_LOC_ON, false); }
    public void    setFakeLocationEnabled(boolean v) { putBool(KEY_FAKE_LOC_ON, v); }

    public boolean hasFakeLocation() {
        return !getString(KEY_FAKE_LAT, "").isEmpty() && !getString(KEY_FAKE_LNG, "").isEmpty();
    }

    public double getFakeLat() {
        try { return Double.parseDouble(getString(KEY_FAKE_LAT, "")); } catch (Throwable t) { return 0d; }
    }

    public double getFakeLng() {
        try { return Double.parseDouble(getString(KEY_FAKE_LNG, "")); } catch (Throwable t) { return 0d; }
    }

    public String getFakeLocLabel() { return getString(KEY_FAKE_LABEL, ""); }

    public void setFakeLocation(double lat, double lng, String label) {
        putString(KEY_FAKE_LAT, Double.toString(lat));
        putString(KEY_FAKE_LNG, Double.toString(lng));
        putString(KEY_FAKE_LABEL, label != null ? label : "");
    }

    public void clearFakeLocation() {
        putString(KEY_FAKE_LAT, "");
        putString(KEY_FAKE_LNG, "");
        putString(KEY_FAKE_LABEL, "");
    }

    // --- Item field dump（每个类名保留最新一条，互不覆盖）---
    private final java.util.LinkedHashMap<String, String> mItemDumps = new java.util.LinkedHashMap<>();

    public synchronized void addItemDump(String dump) {
        if (dump == null) return;
        // 提取 class= 行作为 key
        String key = "unknown";
        for (String line : dump.split("\n")) {
            if (line.startsWith("class=")) { key = line.substring(6).trim(); break; }
        }
        mItemDumps.put(key, dump); // 同类覆盖，不同类并存
    }

    public synchronized java.util.List<String> getItemDumps() {
        return new java.util.ArrayList<>(mItemDumps.values());
    }

    // --- 全量 addAll 流水（调试用，内存，最近 100 行）---
    private final java.util.ArrayDeque<String> mRawFeed = new java.util.ArrayDeque<>();
    private static final int RAW_MAX = 10000;

    public synchronized void addRawFeedLine(String line) {
        if (line == null) return;
        mRawFeed.addLast(line);
        while (mRawFeed.size() > RAW_MAX) mRawFeed.removeFirst();
    }

    public synchronized java.util.List<String> getRawFeed() {
        return new java.util.ArrayList<>(mRawFeed);
    }

    // --- UIN → wxid 映射（ConvFilter 写入，SearchFilter 读取）---
    // fz2.e.g 字段在 8.0.71 是纯 UIN 字符串，不含 wxid。
    // 从会话列表 l4 contact 对象拿到 UIN(S0()) + wxid(C0()) 对，存到此表。
    private final java.util.concurrent.ConcurrentHashMap<String, String> mUinToWxid =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void putUinMapping(String uin, String wxid) {
        if (uin == null || uin.isEmpty() || wxid == null || wxid.isEmpty()) return;
        mUinToWxid.put(uin, wxid);
    }

    public String getWxidByUin(String uin) {
        if (uin == null || uin.isEmpty()) return null;
        return mUinToWxid.get(uin);
    }

    // --- Feed-seen wxids (in-memory ring, max 50, not persisted) ---
    // key=wxid, value=nickname
    private final java.util.LinkedHashMap<String, String> mFeedSeen = new java.util.LinkedHashMap<>();
    private static final int FEED_SEEN_MAX = 50;

    // --- Conv-seen wxids (conversation list, in-memory ring, max 50) ---
    private final java.util.LinkedHashMap<String, String> mConvSeen = new java.util.LinkedHashMap<>();
    private static final int CONV_SEEN_MAX = 50;

    public synchronized void addFeedWxid(String wxid, String nickname) {
        if (wxid == null || wxid.isEmpty()) return;
        String nick = (nickname != null && !nickname.isEmpty()) ? nickname : mFeedSeen.getOrDefault(wxid, "");
        mFeedSeen.remove(wxid); // move to tail
        mFeedSeen.put(wxid, nick);
        while (mFeedSeen.size() > FEED_SEEN_MAX) {
            mFeedSeen.remove(mFeedSeen.entrySet().iterator().next().getKey());
        }
    }

    /** Returns list of [wxid, nickname] pairs, insertion order */
    public synchronized java.util.List<String[]> getFeedWxids() {
        java.util.List<String[]> result = new java.util.ArrayList<>(mFeedSeen.size());
        for (java.util.Map.Entry<String, String> e : mFeedSeen.entrySet()) {
            result.add(new String[]{e.getKey(), e.getValue()});
        }
        return result;
    }

    public synchronized void addConvWxid(String wxid, String nickname) {
        if (wxid == null || wxid.isEmpty()) return;
        String nick = (nickname != null && !nickname.isEmpty()) ? nickname : mConvSeen.getOrDefault(wxid, "");
        mConvSeen.remove(wxid);
        mConvSeen.put(wxid, nick);
        while (mConvSeen.size() > CONV_SEEN_MAX) {
            mConvSeen.remove(mConvSeen.entrySet().iterator().next().getKey());
        }
    }

    public synchronized java.util.List<String[]> getConvWxids() {
        java.util.List<String[]> result = new java.util.ArrayList<>(mConvSeen.size());
        for (java.util.Map.Entry<String, String> e : mConvSeen.entrySet()) {
            result.add(new String[]{e.getKey(), e.getValue()});
        }
        return result;
    }
}
