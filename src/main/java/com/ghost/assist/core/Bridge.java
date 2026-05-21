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

    public static Bridge getInstance() { return sInstance; }

    public void init(Application app) {
        mPrefs = app.getSharedPreferences(NAMESPACE, Context.MODE_PRIVATE);
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
    private static final String KEY_MY_WXID = "mwxd";

    public String getMyWxid() { return getString(KEY_MY_WXID, ""); }
    public void setMyWxid(String wxid) { putString(KEY_MY_WXID, wxid != null ? wxid.trim() : ""); }

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
