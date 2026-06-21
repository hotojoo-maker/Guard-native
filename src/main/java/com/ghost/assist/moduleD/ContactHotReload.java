package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * H↔V 热切换逻辑——通讯录 tab（AddressLiveList / ik3.t0）。
 *
 * 对标：moduleD/ConvHotReload.java（会话 tab v28 收口）。
 *
 * 【本文件门控锁定】（P_CV1，授权检查官 2026-05-27 PASS）
 *   允许：读 StateMachine.isActive() / 读 ContactFilter.sLiveListRef / 调 ik3.t0 的 notifyDataSetChanged
 *   禁止：调 StateMachine.enter/exitHidden / RefreshBus.fire* / NativeBridge.* / AuthManager.* / Bridge 写字段
 *   预审：见 03_execute_执行任务/P_CV1_通讯录V态热切/worklog.md §二
 *
 * 职责：
 *   - BUS-H / BUS-V 回调（由 ContactFilter.install() 里的 RefreshBus.register 委托过来）
 *   - sContactCache 缓存管理（lazy-clear，覆写式 putCache）
 *   - H→V 注回 MvvmList 基类 o/p/h backing（in-place，不替换字段）
 *   - 80ms 异步 post-dedup（F-35 强制铁律：identity dedup 必须 notify 后异步）
 *
 * 跨类访问：与 ContactFilter 同包，直接访问 package-private 成员（sLiveListRef / sAdapterRef
 *           / ADDR_ITEM_CLS / extractWxid 等）。
 */
class ContactHotReload {

    // =========================================================================
    // ── 缓存数据结构 ─────────────────────────────────────────────────────────
    // =========================================================================

    /** 被 H/U 态过滤删除的 fc5.g item；H→V 时注回 AddressLiveList。 */
    static final class CachedContactItem {
        final String id;            // wxid 或 *@chatroom
        final Object item;          // fc5.g 实例
        final String fieldName;     // 诊断标签（链路来源）；null = addAll/backing 链路。仅日志用，不参与逻辑
        final int    originalIndex;
        final long   timestamp;
        CachedContactItem(String id, Object item, String fieldName, int originalIndex) {
            this.id            = id;
            this.item          = item;
            this.fieldName     = fieldName;
            this.originalIndex = originalIndex;
            this.timestamp     = System.currentTimeMillis();
        }
    }

    static final List<CachedContactItem> sContactCache =
            Collections.synchronizedList(new java.util.ArrayList<CachedContactItem>());
    static volatile long sCacheUpdatedMs = 0L;
    /** 缓存有效期：5 分钟（与会话 tab 同口径）。 */
    static final long CACHE_TTL_MS = 300_000L;

    /** id → 最新存活 fc5.g（H 态过滤前的最近实例），BUS-V 注回时优先用此对象避免失活。 */
    static final ConcurrentHashMap<String, Object> sContactItemMap = new ConcurrentHashMap<>();

    static volatile boolean sPendingHide = false;
    static volatile boolean sPendingRestore = false;

    // BUS-V 竞争控制：BUS-H 优先（R-07 同口径）
    static final Handler sBusVHandler = new Handler(Looper.getMainLooper());
    static Runnable sBusVImmRunnable   = null;
    static Runnable sBusVRetryRunnable = null;

    // =========================================================================
    // ── 安装入口 ─────────────────────────────────────────────────────────────
    // =========================================================================

    /**
     * 由 ContactFilter.install() 调用。
     *
     * 【设计决策】本方法不装任何生命周期 hook。密友主通讯录的 live AddressLiveList / adapter
     *   引用由 ContactDiscoveryHook（扫前台 Activity View 树）发现并写 sLiveListRef/sAdapterRef；
     *   本类只在 RefreshBus 回调里读这些引用做 H 清 / V 注（o/p/h）。
     */
    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(ContactFilter.TAG, "[CTHR] install (no hooks; refs discovered by ContactDiscoveryHook)");
    }

    // =========================================================================
    // ── BUS 委托方法（由 ContactFilter.install() 里的 RefreshBus.register 调用）─
    // =========================================================================

    /**
     * V→H：BUS-H 回调体。
     *
     * 取消挂起的 BUS-V Runnable（R-07：BUS-H 优先），设 pendingHide，触发 clean。
     * 不在这里清空 sContactCache —— lazy-clear 铁律（与 ConvHotReload F-34 同口径）。
     */
    static void handleBusHidden() {
        if (sBusVImmRunnable != null)   { sBusVHandler.removeCallbacks(sBusVImmRunnable);   sBusVImmRunnable   = null; }
        if (sBusVRetryRunnable != null) { sBusVHandler.removeCallbacks(sBusVRetryRunnable); sBusVRetryRunnable = null; }
        sPendingRestore = false;
        sPendingHide = true;
        Object liveList = ContactFilter.sLiveListRef;
        if (liveList != null) {
            ContactFilter.cleanLiveList(liveList, "BUS-H");
            ContactFilter.notifyContactAdapter("BUS-H-hide");
        } else {
            // V3 fallback: use backing ArrayList directly
            java.util.ArrayList<Object> bl = ContactFilter.sBackingListRef != null
                    ? ContactFilter.sBackingListRef.get() : null;
            if (bl != null) {
                Log.i(ContactFilter.TAG, "[CTHR:BUS-H] no liveList, cleanBackingList (V3) sz=" + bl.size());
                ContactFilter.cleanBackingList(bl, "BUS-H-backing");
                ContactFilter.notifyContactAdapter("BUS-H-backing");
            } else {
                Log.i(ContactFilter.TAG, "[CTHR:BUS-H] no liveList no backingList, pendingHide=true");
            }
        }
    }

    /**
     * H→V：BUS-V 回调体。
     *
     * 快照缓存 → 主线程 Runnable in-place 注回 AddressLiveList backing(o/p/h) → notify adapter
     * → 80ms 异步 post-dedup（F-35）→ 400ms retry 兜底。
     */
    static void handleBusVisible() {
        sPendingRestore = true;
        final java.util.ArrayList<CachedContactItem> cacheSnap;
        synchronized (sContactCache) {
            cacheSnap = new java.util.ArrayList<>(sContactCache);
        }
        Log.i(ContactFilter.TAG, "[CTHR:BUS-V] cache=" + cacheSnap.size());

        if (cacheSnap.isEmpty()) {
            ContactFilter.notifyContactAdapter("BUS-V-empty");
            return;
        }

        sBusVImmRunnable = new Runnable() {
            @Override
            public void run() {
                sBusVImmRunnable = null;
                if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) return;
                Object liveList = ContactFilter.sLiveListRef;
                if (liveList == null) {
                    // V3 fallback: inject directly into backing ArrayList
                    java.util.ArrayList<Object> bl = ContactFilter.sBackingListRef != null
                            ? ContactFilter.sBackingListRef.get() : null;
                    if (bl != null) {
                        Log.w(ContactFilter.TAG, "[CTHR:BUS-V] no liveList, restoreToBackingDirect (V3)");
                        int injected3 = restoreToBackingListDirect(bl, cacheSnap);
                        Log.i(ContactFilter.TAG, "[CTHR:BUS-V:V3] injected=" + injected3
                                + " backingSz=" + bl.size());
                        ContactFilter.notifyContactAdapter("BUS-V-V3");
                        // F-35: post-dedup after notify
                        sBusVHandler.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (StateMachine.getInstance().getState()
                                        != StateMachine.State.VISIBLE) return;
                                int removed = postDedupGraph("BUS-V-V3-dedup");
                                if (removed > 0) ContactFilter.notifyContactAdapter("BUS-V-V3-dedup");
                            }
                        }, 80);
                    } else {
                        Log.w(ContactFilter.TAG, "[CTHR:BUS-V] no liveList no backingList, fallback notify");
                        ContactFilter.notifyContactAdapter("BUS-V-fallback");
                    }
                    return;
                }
                int injected = restoreToLiveList(liveList, cacheSnap);
                Log.i(ContactFilter.TAG, "[CTHR:BUS-V] in-place injected=" + injected
                        + " on " + liveList.getClass().getSimpleName());
                ContactFilter.notifyContactAdapter("BUS-V-direct");

                // F-35：identity dedup 必须放在 notify 之后异步执行
                sBusVHandler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (StateMachine.getInstance().getState()
                                != StateMachine.State.VISIBLE) return;
                        int removed = postDedupGraph("BUS-V-dedup");
                        Log.i(ContactFilter.TAG, "[CTHR:BUS-V:dedup] removed=" + removed);
                        if (removed > 0) ContactFilter.notifyContactAdapter("BUS-V-dedup");
                    }
                }, 80);
            }
        };
        sBusVHandler.post(sBusVImmRunnable);

        sBusVRetryRunnable = new Runnable() {
            @Override public void run() {
                sBusVRetryRunnable = null;
                if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) return;
                ContactFilter.notifyContactAdapter("BUS-V-retry");
            }
        };
        sBusVHandler.postDelayed(sBusVRetryRunnable, 400);
    }

    // =========================================================================
    // ── 缓存读写 ─────────────────────────────────────────────────────────────
    // =========================================================================

    /** 缓存被过滤删除的 item；同 id 旧缓存直接覆盖（防止积累过期对象）。 */
    static void putCache(String id, Object item, String fieldName, int originalIndex) {
        if (id == null || item == null) return;
        synchronized (sContactCache) {
            for (int i = sContactCache.size() - 1; i >= 0; i--) {
                if (id.equals(sContactCache.get(i).id)) sContactCache.remove(i);
            }
            sContactCache.add(new CachedContactItem(id, item, fieldName, originalIndex));
            sCacheUpdatedMs = System.currentTimeMillis();
        }
        sContactItemMap.put(id, item);
        Log.i(ContactFilter.TAG, "[CTHR:put] id=" + id + " idx=" + originalIndex
                + " field=" + (fieldName != null ? fieldName : "pre-list"));
    }

    // =========================================================================
    // ── H→V 注回（in-place）─────────────────────────────────────────────────
    // =========================================================================

    /**
     * In-place 把 cacheSnap 中缺失的 fc5.g 注回 AddressLiveList 的 backing(o/p/h)。
     *
     * - 不替换字段（替换会让 ik3.t0 持旧引用，notify 无效果）
     * - 优先用 sContactItemMap 中最新存活对象（cached.item 可能 detach）
     * - 已存在同 id 则跳过；按 cached.originalIndex 插入（clamp 到 size）
     * - CME-safe：dup 扫描用 size+get 而非 iterator
     */
    @SuppressWarnings("unchecked")
    static int restoreToLiveList(Object liveList, List<CachedContactItem> cacheSnap) {
        if (cacheSnap == null || cacheSnap.isEmpty()) return 0;
        // P_CV1（2026-05-29）：AddressLiveList 真 backing = MvvmList 基类 o/p/h。
        // 对标 ConvHotReload.restoreToMvvmList：逐字段注入所有含 fc5.g 的 List，触发 RecyclerView 重绘。
        int totalInjected = 0;
        for (String fn : ContactFilter.MVVMLIST_FIELDS) {
            List<Object> backing;
            try {
                Field f = ContactFilter.findFieldInHierarchy(liveList.getClass(), fn);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(liveList);
                if (!(arr instanceof List)) continue;
                backing = (List<Object>) arr;
                if (backing.isEmpty()) continue; // 空字段（如 h）跳过
                Object first = backing.get(0);
                if (first == null
                        || !ContactFilter.ADDR_ITEM_CLS.equals(first.getClass().getName())) {
                    continue; // 只处理元素是 fc5.g 的列表
                }
            } catch (Throwable e) {
                Log.w(ContactFilter.TAG, "[CTHR:restoreInPlace] field=" + fn + " get fail: " + e);
                continue;
            }

            int injected = 0;
            for (CachedContactItem cached : cacheSnap) {
                Object liveItem = sContactItemMap.get(cached.id);
                Object itemToInject = (liveItem != null) ? liveItem : cached.item;
                boolean dup = false;
                try {
                    int sz = backing.size();
                    for (int i = 0; i < sz; i++) {
                        Object cur;
                        try { cur = backing.get(i); }
                        catch (IndexOutOfBoundsException oob) { break; }
                        if (cur == null) continue;
                        String w = ContactFilter.extractWxid(cur);
                        if (w != null && cached.id.equals(w)) { dup = true; break; }
                    }
                } catch (java.util.ConcurrentModificationException cme) {
                    Log.w(ContactFilter.TAG, "[CTHR:restoreInPlace] field=" + fn
                            + " dup-scan CME id=" + cached.id + ", skip");
                    continue;
                }
                if (!dup) {
                    try {
                        int pos = Math.min(cached.originalIndex, backing.size());
                        if (pos < 0) pos = backing.size();
                        backing.add(pos, itemToInject);
                        injected++;
                        Log.i(ContactFilter.TAG, "[CTHR:restoreInPlace] field=" + fn
                                + " id=" + cached.id + " pos=" + pos
                                + (liveItem != null ? " fresh" : " stale"));
                    } catch (java.util.ConcurrentModificationException cme) {
                        Log.w(ContactFilter.TAG, "[CTHR:restoreInPlace] field=" + fn
                                + " insert CME id=" + cached.id + ", skip");
                    }
                }
            }
            if (injected > 0) {
                Log.i(ContactFilter.TAG, "[CTHR:restoreInPlace] field=" + fn
                        + " injected=" + injected + " listSz=" + backing.size());
                totalInjected += injected;
            }
        }
        return totalInjected;
    }

    // =========================================================================
    // ── V3: restore directly to backing ArrayList (no sLiveListRef needed) ──
    // =========================================================================

    /**
     * V3 兜底：sLiveListRef 为 null 时直接操作 addAll 钩取的 backing ArrayList。
     * 逻辑与 restoreToLiveList 一致，跳过 o/p/h 字段反射步骤。
     * CME-safe：dup 扫描用 size+get，插入捕获 CME 跳过。
     */
    @SuppressWarnings("unchecked")
    static int restoreToBackingListDirect(
            java.util.ArrayList<Object> backing, List<CachedContactItem> cacheSnap) {
        if (cacheSnap == null || cacheSnap.isEmpty() || backing == null) return 0;
        int injected = 0;
        for (CachedContactItem cached : cacheSnap) {
            Object liveItem = sContactItemMap.get(cached.id);
            Object itemToInject = (liveItem != null) ? liveItem : cached.item;
            boolean dup = false;
            try {
                int sz = backing.size();
                for (int i = 0; i < sz; i++) {
                    Object cur;
                    try { cur = backing.get(i); }
                    catch (IndexOutOfBoundsException oob) { break; }
                    if (cur == null) continue;
                    String w = ContactFilter.extractWxid(cur);
                    if (w != null && cached.id.equals(w)) { dup = true; break; }
                }
            } catch (java.util.ConcurrentModificationException cme) {
                Log.w(ContactFilter.TAG, "[CTHR:restoreDirect] dup-scan CME id=" + cached.id + " skip");
                continue;
            }
            if (!dup) {
                try {
                    int pos = Math.min(cached.originalIndex, backing.size());
                    if (pos < 0) pos = backing.size();
                    backing.add(pos, itemToInject);
                    injected++;
                    Log.i(ContactFilter.TAG, "[CTHR:restoreDirect] id=" + cached.id
                            + " pos=" + pos + (liveItem != null ? " fresh" : " stale"));
                } catch (java.util.ConcurrentModificationException cme) {
                    Log.w(ContactFilter.TAG, "[CTHR:restoreDirect] insert CME id=" + cached.id + " skip");
                }
            }
        }
        if (injected > 0) {
            Log.i(ContactFilter.TAG, "[CTHR:restoreDirect] injected=" + injected
                    + " backingSz=" + backing.size());
        }
        return injected;
    }

    // =========================================================================
    // ── 80ms 异步 post-dedup（F-35）─────────────────────────────────────────
    // =========================================================================

    /**
     * 入口：扫 contact adapter 全图，所有 fc5.g List 做 identity dedup。
     *
     * F-35 铁律：禁止在 restore 主路径同步去重 —— 那会让 ArrayList.modCount 不变 →
     * RecyclerView 跳过重绘。这里必须在 notify 之后异步跑（80ms 容许 RecyclerView 完成本轮渲染）。
     */
    static int postDedupGraph(String label) {
        Object adapter = ContactFilter.sAdapterRef != null ? ContactFilter.sAdapterRef.get() : null;
        Object liveList = ContactFilter.sLiveListRef;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int total = 0;
        if (adapter != null) {
            total += dedupContactListsInObject(adapter, label, 0, visited);
        }
        if (liveList != null) {
            total += dedupContactListsInObject(liveList, label, 0, visited);
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static int dedupContactListsInObject(
            Object root, String label, int depth, Set<Object> visited) {
        if (root == null || depth > 2 || visited.contains(root)) return 0;
        visited.add(root);
        int total = 0;
        Class<?> cls = root.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable ignored) {
                break;
            }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(root);
                    if (v == null) continue;
                    if (v instanceof List) {
                        List<Object> list = (List<Object>) v;
                        if (visited.contains(list)) continue;
                        visited.add(list);
                        if (!isContactItemList(list)) continue;
                        int removed = dedupListByIdentity(list);
                        if (removed > 0) {
                            Log.i(ContactFilter.TAG, "[CTHR:" + label + "] field="
                                    + f.getName() + " dedup=" + removed
                                    + " listSz=" + list.size()
                                    + " owner=" + root.getClass().getName());
                            total += removed;
                        }
                    } else if (depth < 2 && isTencentHolder(v)) {
                        total += dedupContactListsInObject(v, label, depth + 1, visited);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return total;
    }

    private static int dedupListByIdentity(List<Object> list) {
        if (list == null || list.size() < 2) return 0;
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        int removed = 0;
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) continue;
            if (seen.containsKey(item)) {
                try { it.remove(); removed++; }
                catch (UnsupportedOperationException ignored) {}
            } else {
                seen.put(item, Boolean.TRUE);
            }
        }
        return removed;
    }

    private static boolean isContactItemList(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        try {
            Object first = list.get(0);
            return first != null
                    && ContactFilter.ADDR_ITEM_CLS.equals(first.getClass().getName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTencentHolder(Object obj) {
        if (obj == null) return false;
        String cn = obj.getClass().getName();
        return cn.startsWith("com.tencent.")
                || cn.startsWith("ik3.")
                || cn.startsWith("fc5.")
                || cn.startsWith("kc5.");
    }
}
