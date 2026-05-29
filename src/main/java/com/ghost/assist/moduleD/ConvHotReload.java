package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.StateMachine;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * H↔V 热切换逻辑——从 ConvFilter 独立出来的维护区段。
 *
 * 职责：
 *   BUS-H / BUS-V 回调处理、sConvCache 缓存管理、H→V 注回、
 *   LauncherUI onResume/onStart/onWindowFocusChanged 挂钩。
 *
 * 铁律：
 *   - RefreshBus.register() 调用必须在 ConvFilter.install() 里执行；
 *     本类只提供 handleBusHidden() / handleBusVisible() 委托方法。
 *   - 过滤逻辑（L1/L2/L4/INIT/wxid提取/cleanMvvmList）全部保留在 ConvFilter。
 *
 * 跨类访问：本类与 ConvFilter 同包，直接访问 ConvFilter 的 package-private 成员。
 */
class ConvHotReload {

    // =========================================================================
    // ── 缓存数据结构 ────────────────────────────────────────────────────────
    // =========================================================================

    /** 被 H/U 态过滤删除的 item；H→V 时注回 MvvmList。 */
    static final class CachedConvItem {
        final String wxid;
        final Object item;
        final String fieldName;    // MvvmList 内部数组字段名；null = 拦截前
        final int    originalIndex;
        final long   timestamp;
        CachedConvItem(String wxid, Object item, String fieldName, int originalIndex) {
            this.wxid          = wxid;
            this.item          = item;
            this.fieldName     = fieldName;
            this.originalIndex = originalIndex;
            this.timestamp     = System.currentTimeMillis();
        }
    }

    // =========================================================================
    // ── 热切换状态字段 ───────────────────────────────────────────────────────
    // =========================================================================

    /** 被 H/U 态过滤删除的 item 缓存，H→V 时注回。 */
    static final List<CachedConvItem> sConvCache =
            Collections.synchronizedList(new java.util.ArrayList<CachedConvItem>());
    static volatile long sCacheUpdatedMs = 0L;
    /** 缓存有效期：5 分钟（锁屏 4min+ 曾触发 60s TTL 过期导致 cache=0）。 */
    static final long CACHE_TTL_MS = 300_000L;

    /**
     * wxid → 最新存活 kc5.y，由 La/L1/L0addAll hooks 在过滤前写入。
     * BUS-V restore 时优先取此对象（避免 cached.item 失活导致渲染静默跳过）。
     */
    @SuppressWarnings("unchecked")
    static final ConcurrentHashMap<String, Object> sConvItemMap = new ConcurrentHashMap<>();
    /** BUS-V replay 防递归标志（当前未实际赋 true，保留作安全围栏）。 */
    static volatile boolean sAddAllReplaying = false;

    // ── V→H pendingHide ──────────────────────────────────────────────────────
    // BUS-H 设为 true；LauncherUI.onResume HIDDEN 态时消费，确保异步 DB reload 后再 clean。
    static volatile boolean sPendingHide = false;

    // ── H→V pendingRestore ───────────────────────────────────────────────────
    // BUS-V 设为 true；LauncherUI.onResume VISIBLE 态时消费，兜底 notify。
    static volatile boolean sPendingRestore = false;

    // ── BUS-V 竞争取消（铁律 R-07：BUS-H 优先于 BUS-V）─────────────────────
    static final Handler sBusVHandler = new Handler(Looper.getMainLooper());
    static Runnable sBusVImmRunnable   = null;
    static Runnable sBusVRetryRunnable = null;

    /**
     * MvvmConvList 专用 ref——仅由 CL1n / MCL-c / MCL-ctor / L4(kc5.v0) 写入。
     * L1n/L3 hooks 拦截所有 MvvmList 子类（搜索/通讯录等）会污染 ConvFilter.sMvvmListRef，
     * BUS-V Runnable 必须用本字段。
     */
    static volatile WeakReference<Object> sConvMvvmListRef;

    // =========================================================================
    // ── 安装入口 ─────────────────────────────────────────────────────────────
    // =========================================================================

    /**
     * 由 ConvFilter.install() 调用。
     * 安装 LauncherUI onResume/onStart/onWindowFocusChanged hooks。
     * RefreshBus.register() 留在 ConvFilter.install()，避免 INIT 时序问题。
     */
    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installLauncherResumeHook(lpparam);
    }

    // =========================================================================
    // ── BUS 委托方法（由 ConvFilter.install() 里的 RefreshBus.register 调用）─
    // =========================================================================

    /**
     * V→H：BUS-H 回调体。
     * 取消所有挂起的 BUS-V delayed runnables（R-07），设 pendingHide，触发 clean。
     */
    static void handleBusHidden() {
        // R-07: BUS-H 优先——立即取消所有 BUS-V 延迟 Runnable
        if (sBusVImmRunnable != null)   { sBusVHandler.removeCallbacks(sBusVImmRunnable);   sBusVImmRunnable   = null; }
        if (sBusVRetryRunnable != null) { sBusVHandler.removeCallbacks(sBusVRetryRunnable); sBusVRetryRunnable = null; }
        sPendingRestore = false;
        // 不在这里清空 sConvCache——lazy-clear 铁律（F-34 根因）:
        // cleanMvvmList 找到 item 时才清空旧缓存，removed=0 时保留缓存给下一次 H→V。
        sPendingHide = true;
        ConvFilter.cleanConvData("BUS-H");
        Log.i(ConvFilter.TAG, "[BUS] refresh ConvFilter cleaned pendingHide=true");
        // BUS-H 时 Activity 可能已 stop，WeChat 恢复时会 repopulate MvvmList；
        // 80ms 延迟 notify 是早期兜底，sPendingHide 确保 onResume 也会 re-clean。
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                notifyConvAdapter("BUS-H-hide");
            }
        }, 80);
    }

    /**
     * H→V：BUS-V 回调体。
     * 快照缓存 → 主线程 Runnable 里 in-place 注回 → notify adapter。
     * 400ms retry 兜底（MvvmList 可能在 post 之后才被 WeChat 重建）。
     */
    static void handleBusVisible() {
        ConvFilter.removeColdStartOverlay("BUS-V");
        sPendingRestore = true;
        // 快照时间点：BUS-V fire 时立即抓取（避免并发 BUS-H 修改缓存）
        final List<CachedConvItem> cacheSnap;
        synchronized (sConvCache) {
            cacheSnap = new java.util.ArrayList<>(sConvCache);
        }
        Log.i(ConvFilter.TAG, "[BUS-V] cache=" + cacheSnap.size());
        final List<CachedConvItem> visibleSnap =
                ConvFilter.expandCacheWithWarm(cacheSnap, "BUS-V");
        Log.i(ConvFilter.TAG, "[BUS-V] visibleSnap=" + visibleSnap.size());
        if (!visibleSnap.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) return;
                    // 优先用 MvvmConvList 专用 ref（不受搜索/通讯录 MvvmList 子类污染）
                    Object liveMvvmList = getConvMvvmList();
                    if (liveMvvmList == null) {
                        Log.w(ConvFilter.TAG, "[BUS-V] no convMvvmList, fallback notify");
                        notifyConvAdapter("BUS-V-fallback");
                        return;
                    }
                    // In-place 注入：直接修改 adapter 持有的 List 对象（同一引用）。
                    // MvvmList.n(newList) 会替换字段，adapter 仍持旧引用 → 无效果。
                    int injected = restoreToMvvmList(liveMvvmList, visibleSnap);
                    int adapterInjected = ConvFilter.restoreAdapterGraphFromCache(
                            visibleSnap, "BUS-V-adapter");
                    Log.i(ConvFilter.TAG, "[BUS-V] in-place injected=" + injected
                            + " adapterInjected=" + adapterInjected
                            + " on " + liveMvvmList.getClass().getSimpleName());
                    notifyConvAdapter("BUS-V-direct");

                    // v27 post-dedup：v24 风格多次 insert 已使 RecyclerView 看到列表变更
                    // 触发重绘；80ms 后扫整个 adapter 图、把 identity 重复项剔掉再 notify，
                    // 让最终态只剩单次出现的密群/密友（先脏后净，肉眼最多见 80ms 闪一下）。
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override public void run() {
                            if (StateMachine.getInstance().getState()
                                    != StateMachine.State.VISIBLE) return;
                            int removed = ConvFilter.postDedupAdapterGraph("BUS-V-dedup");
                            Log.i(ConvFilter.TAG, "[BUS-V:dedup] removed=" + removed);
                            if (removed > 0) {
                                notifyConvAdapter("BUS-V-dedup");
                            }
                        }
                    }, 80);
                }
            });
        } else {
            notifyConvAdapter("BUS-V-imm");
            Log.w(ConvFilter.TAG, "[BUS-V] cache empty");
        }
        // 400ms retry 兜底：MvvmList 可能在 post 之后才被 WeChat 重建
        sBusVRetryRunnable = new Runnable() {
            @Override public void run() {
                if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) return;
                notifyConvAdapter("BUS-V-retry");
                sBusVRetryRunnable = null;
            }
        };
        sBusVHandler.postDelayed(sBusVRetryRunnable, 400);
    }

    // =========================================================================
    // ── LauncherUI 生命周期 hooks ─────────────────────────────────────────────
    // =========================================================================

    private static void installLauncherResumeHook(XC_LoadPackage.LoadPackageParam lpparam) {
        // ── onResume: pendingHide / pendingRestore 消费 ───────────────────────
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            android.app.Activity act = (android.app.Activity) param.thisObject;
                            String cn = act.getClass().getName()
                                    .toLowerCase(java.util.Locale.ROOT);
                            boolean isLauncher = cn.contains("launcherui") || cn.contains("mainui");
                            if (!isLauncher) return;

                            StateMachine.State state = StateMachine.getInstance().getState();

                            // V→H pendingHide：WeChat 恢复时异步重推数据，onResume 再 clean 一次
                            if (sPendingHide) {
                                if (state != StateMachine.State.HIDDEN) {
                                    sPendingHide = false;
                                } else {
                                    sPendingHide = false;
                                    Log.i(ConvFilter.TAG,
                                            "[CF:pendingHide] LauncherUI resumed HIDDEN → multi-shot clean");
                                    final android.app.Activity launcherH = act;
                                    final Handler ph = new Handler(Looper.getMainLooper());
                                    // 三连射：next-frame / +150ms / +400ms
                                    final long[] shots = {0L, 150L, 400L};
                                    for (final long delay : shots) {
                                        ph.postDelayed(new Runnable() {
                                            @Override public void run() {
                                                if (StateMachine.getInstance().getState()
                                                        != StateMachine.State.HIDDEN) return;
                                                refreshAdapterFromActivity(launcherH);
                                                ConvFilter.cleanConvData("pendingHide-" + delay);
                                                notifyConvAdapter("pendingHide-" + delay);
                                            }
                                        }, delay);
                                    }
                                }
                            }

                            // H→V pendingRestore：BUS-V replay 已在主线程 post，此处只补 notify
                            if (sPendingRestore) {
                                sPendingRestore = false;
                                if (state == StateMachine.State.VISIBLE) {
                                    Log.i(ConvFilter.TAG,
                                            "[CF:pendingRestore] LauncherUI resumed VISIBLE → safety notify");
                                    final android.app.Activity launcherV = act;
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override public void run() {
                                            refreshAdapterFromActivity(launcherV);
                                            notifyConvAdapter("pendingRestore");
                                        }
                                    });
                                }
                            }
                        }
                    });
            Log.i(ConvFilter.TAG, "[CF] pendingHide LauncherUI resume hook installed");
        } catch (Throwable e) {
            Log.w(ConvFilter.TAG, "[CF] pendingHide hook fail: " + e);
        }

        // ── onStart: early-clean，覆盖冷启动/锁屏第一帧 ─────────────────────
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onStart",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            android.app.Activity act = (android.app.Activity) param.thisObject;
                            String cn = act.getClass().getName()
                                    .toLowerCase(java.util.Locale.ROOT);
                            if (!cn.contains("launcherui") && !cn.contains("mainui")) return;
                            if (StateMachine.getInstance().getState()
                                    != StateMachine.State.HIDDEN) return;
                            // 首次进 LauncherUI：注册熄屏监听（只注册一次）
                            if (!ConvFilter.sScreenReceiverInstalled) {
                                ConvFilter.sScreenReceiverInstalled = true;
                                try {
                                    android.content.IntentFilter sf =
                                            new android.content.IntentFilter(
                                                    android.content.Intent.ACTION_SCREEN_OFF);
                                    act.getApplicationContext().registerReceiver(
                                            new android.content.BroadcastReceiver() {
                                                @Override
                                                public void onReceive(android.content.Context c,
                                                        android.content.Intent i) {
                                                    ConvFilter.sScreenWasLocked = true;
                                                    Log.i(ConvFilter.TAG,
                                                            "[CF:screen] off → locked=true");
                                                }
                                            }, sf);
                                    Log.i(ConvFilter.TAG, "[CF:screen] receiver registered");
                                } catch (Throwable t) {
                                    Log.w(ConvFilter.TAG, "[CF:screen] register fail: " + t);
                                }
                            }
                            Log.i(ConvFilter.TAG, "[CF:start] LauncherUI onStart HIDDEN → clean+notify");
                            ConvFilter.showColdStartOverlay(act);
                            ConvFilter.cleanConvData("start-H");
                            notifyConvAdapter("start-H");
                        }
                    });
            Log.i(ConvFilter.TAG, "[CF] onStart hook installed");
        } catch (Throwable e) {
            Log.w(ConvFilter.TAG, "[CF] onStart hook fail: " + e);
        }

        // ── onWindowFocusChanged: 最后兜底（onStart notify 如错过第一帧） ────
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onWindowFocusChanged",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            boolean hasFocus = Boolean.TRUE.equals(param.args[0]);
                            if (!hasFocus) return;
                            android.app.Activity act = (android.app.Activity) param.thisObject;
                            String cn = act.getClass().getName()
                                    .toLowerCase(java.util.Locale.ROOT);
                            if (!cn.contains("launcherui") && !cn.contains("mainui")) return;
                            if (StateMachine.getInstance().getState()
                                    != StateMachine.State.HIDDEN) return;
                            Log.i(ConvFilter.TAG,
                                    "[CF:focus] LauncherUI focus HIDDEN → clean+notify");
                            ConvFilter.cleanConvData("focus-H");
                            notifyConvAdapter("focus-H");
                        }
                    });
            Log.i(ConvFilter.TAG, "[CF] onWindowFocusChanged hook installed");
        } catch (Throwable e) {
            Log.w(ConvFilter.TAG, "[CF] onWindowFocusChanged hook fail: " + e);
        }
    }

    // =========================================================================
    // ── 缓存读写 ──────────────────────────────────────────────────────────────
    // =========================================================================

    /** 缓存被过滤删除的 item；同一 wxid 旧缓存直接覆盖。 */
    static void putCache(String wxid, Object item, String fieldName, int originalIndex) {
        synchronized (sConvCache) {
            for (int i = sConvCache.size() - 1; i >= 0; i--) {
                if (wxid.equals(sConvCache.get(i).wxid)) sConvCache.remove(i);
            }
            sConvCache.add(new CachedConvItem(wxid, item, fieldName, originalIndex));
            sCacheUpdatedMs = System.currentTimeMillis();
        }
        Log.i(ConvFilter.TAG, "[CF:cache] save wxid=" + wxid + " index=" + originalIndex
                + " field=" + (fieldName != null ? fieldName : "pre-list"));
    }

    // =========================================================================
    // ── H→V 注回（in-place）────────────────────────────────────────────────
    // =========================================================================

    /**
     * CL1n VISIBLE 路径：把缓存中缺失的 item 直接插入 WeChat 传入的 list（in-place）。
     * 只处理 kc5.y / CONV_MAIN_UI 类型的会话列表，非会话 update 直接 return。
     */
    @SuppressWarnings("unchecked")
    static void injectCachedItems(List<Object> list, String label) {
        synchronized (sConvCache) {
            if (sConvCache.isEmpty()) return;
        }
        if (!list.isEmpty()) {
            String cn = list.get(0).getClass().getName();
            if (!ConvFilter.CONV_MAIN_UI.equals(cn) && !"kc5.y".equals(cn)) return;
        }
        int injected = 0;
        synchronized (sConvCache) {
            for (CachedConvItem cached : sConvCache) {
                boolean found = false;
                for (Object item : list) {
                    if (cached.wxid.equals(ConvFilter.extractWxid(item))) { found = true; break; }
                }
                if (!found) {
                    Object liveItem = sConvItemMap.get(cached.wxid);
                    Object itemToInject = (liveItem != null) ? liveItem : cached.item;
                    // P_CF3：按 field_conversationTime 降序插入，拿不到时间回退旧 originalIndex
                    long t = ConvFilter.extractConvTime(itemToInject);
                    int pos = ConvFilter.insertPosByTime(list, t, cached.originalIndex);
                    list.add(pos, itemToInject);
                    injected++;
                }
            }
        }
        if (injected > 0) {
            Log.i(ConvFilter.TAG, "[CF:inject] " + label + " injected=" + injected
                    + " listSz=" + list.size());
        }
    }

    /**
     * BUS-V / L4 VISIBLE 路径：把缓存 item in-place 注入 MvvmConvList 的所有 backing 数组。
     *
     * 镜像 cleanMvvmList 的 in-place remove——遍历 ALL 字段（o/p/h），对每个字段各插一次。
     * 优先从 sConvItemMap 取最新存活 kc5.y（避免缓存中旧对象失活导致渲染静默跳过）。
     */
    @SuppressWarnings("unchecked")
    static int restoreToMvvmList(Object mvvmList, List<CachedConvItem> cacheSnap) {
        if (cacheSnap == null || cacheSnap.isEmpty()) return 0;
        int totalInjected = 0;
        // v27 回退到 v24：移除 list-visited 与 identity check（v25/v26 引入），
        // 允许 MvvmConvList.h/o/p 即使引用同一 backing List 也被多次扫到，
        // 确保 RecyclerView 数据源被实际改写到位、触发渲染。
        // 副作用：密群多拍交由 dedup wxid-only 控制（业务可接受，专项 P27 处理）。
        for (String fn : ConvFilter.MVVMLIST_ARRAY_FIELDS) {
            try {
                Field f = ConvFilter.findFieldRecursive(mvvmList.getClass(), fn);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(mvvmList);
                if (!(arr instanceof List)) continue;
                List<Object> list = (List<Object>) arr;
                if (list.isEmpty()) continue;
                String firstCls = list.get(0).getClass().getName();
                if (!ConvFilter.CONV_MAIN_UI.equals(firstCls) && !"kc5.y".equals(firstCls)) continue;
                // dup-scan 不直接迭代 live list（微信 Kotlin coroutine 可能并发写），
                // 用 size+get 的下标访问 + CME 兜底，命中即 abort 当前快照重启。
                int injected = 0;
                for (CachedConvItem cached : cacheSnap) {
                    Object liveItem = sConvItemMap.get(cached.wxid);
                    Object itemToInject = (liveItem != null) ? liveItem : cached.item;
                    boolean dup = false;
                    try {
                        int sz = list.size();
                        for (int i = 0; i < sz; i++) {
                            Object item;
                            try { item = list.get(i); }
                            catch (IndexOutOfBoundsException oob) { break; }
                            String w = ConvFilter.extractWxid(item);
                            if (w != null && cached.wxid.equals(w)) { dup = true; break; }
                        }
                    } catch (java.util.ConcurrentModificationException cme) {
                        Log.w(ConvFilter.TAG, "[CF:restoreInPlace] field=" + fn
                                + " dup-scan CME wxid=" + cached.wxid + ", skip this wxid");
                        continue;
                    }
                    if (!dup) {
                        try {
                            // P_CF3：按 field_conversationTime 降序插入，拿不到时间回退旧 originalIndex
                            long t = ConvFilter.extractConvTime(itemToInject);
                            int pos = ConvFilter.insertPosByTime(list, t, cached.originalIndex);
                            list.add(pos, itemToInject);
                            injected++;
                            if (liveItem != null) {
                                Log.i(ConvFilter.TAG,
                                        "[CF:restoreInPlace] wxid=" + cached.wxid + " used fresh from map");
                            } else {
                                Log.i(ConvFilter.TAG,
                                        "[CF:restoreInPlace] wxid=" + cached.wxid + " used stale cache");
                            }
                        } catch (java.util.ConcurrentModificationException cme) {
                            Log.w(ConvFilter.TAG, "[CF:restoreInPlace] field=" + fn
                                    + " insert CME wxid=" + cached.wxid + ", skip");
                        }
                    }
                }
                if (injected > 0) {
                    Log.i(ConvFilter.TAG, "[CF:restoreInPlace] field=" + fn
                            + " injected=" + injected + " listSz=" + list.size());
                    totalInjected += injected;
                }
            } catch (Throwable e) {
                Log.w(ConvFilter.TAG, "[CF:restoreInPlace] field=" + fn + " fail: " + e);
            }
        }
        return totalInjected;
    }

    /**
     * 旧版 BUS-V restore（TTL 检查版），保留供排障参考，当前不在主路径使用。
     */
    @SuppressWarnings("unchecked")
    static void restoreCachedItems() {
        int cacheSize;
        List<CachedConvItem> snapshot;
        synchronized (sConvCache) {
            cacheSize = sConvCache.size();
            snapshot = new java.util.ArrayList<CachedConvItem>(sConvCache);
        }
        Log.i(ConvFilter.TAG, "[CF:restore] state=V cache=" + cacheSize);
        if (cacheSize == 0) return;

        long cacheAgeMs = System.currentTimeMillis() - sCacheUpdatedMs;
        if (cacheAgeMs > CACHE_TTL_MS) {
            Log.w(ConvFilter.TAG, "[CF:restore] cache expired age=" + cacheAgeMs
                    + "ms ttl=" + CACHE_TTL_MS + "ms");
            sConvCache.clear();
            return;
        }

        Object mvvmList = ConvFilter.getLiveMvvmList();
        if (mvvmList == null) {
            Log.w(ConvFilter.TAG, "[CF:restore] mvvmList=null, skip");
            return;
        }
        Log.i(ConvFilter.TAG, "[CF:restore] mvvm=" + mvvmList.getClass().getSimpleName());

        int injected = 0;
        for (CachedConvItem cached : snapshot) {
            boolean anyInjected = false;
            for (String fn : ConvFilter.MVVMLIST_ARRAY_FIELDS) {
                try {
                    Field f = ConvFilter.findFieldRecursive(mvvmList.getClass(), fn);
                    if (f == null) continue;
                    f.setAccessible(true);
                    Object arr = f.get(mvvmList);
                    if (!(arr instanceof List)) continue;
                    List<Object> targetList = (List<Object>) arr;
                    boolean dup = false;
                    for (Object item : targetList) {
                        String wxid = ConvFilter.extractWxid(item);
                        if (cached.wxid.equals(wxid)) { dup = true; break; }
                    }
                    if (dup) {
                        Log.i(ConvFilter.TAG, "[CF:restore] skip dup wxid=" + cached.wxid
                                + " field=" + fn);
                        continue;
                    }
                    // P_CF3：按 field_conversationTime 降序插入，拿不到时间回退旧 originalIndex
                    long t = ConvFilter.extractConvTime(cached.item);
                    int insertAt = ConvFilter.insertPosByTime(targetList, t, cached.originalIndex);
                    targetList.add(insertAt, cached.item);
                    anyInjected = true;
                    Log.i(ConvFilter.TAG, "[CF:restore] inject wxid=" + cached.wxid
                            + " index=" + insertAt + " field=" + fn);
                } catch (Throwable e) {
                    Log.w(ConvFilter.TAG, "[CF:restore] inject fail wxid=" + cached.wxid
                            + " field=" + fn + ": " + e);
                }
            }
            if (anyInjected) injected++;
        }
        Log.i(ConvFilter.TAG, "[CF:restore] done injected=" + injected + " / " + cacheSize);
    }

    // =========================================================================
    // ── MvvmConvList ref ─────────────────────────────────────────────────────
    // =========================================================================

    /**
     * BUS-V 专用：优先返回 MvvmConvList 实例（sConvMvvmListRef 不受 L1n/L3 污染）。
     * Fallback：走 adapter → findMvvmListOnAdapter。
     */
    static Object getConvMvvmList() {
        Object m = sConvMvvmListRef != null ? sConvMvvmListRef.get() : null;
        if (m != null) return m;
        Object adapter = ConvFilter.getConvAdapter();
        if (adapter != null) return ConvFilter.findMvvmListOnAdapter(adapter);
        return null;
    }

    // =========================================================================
    // ── Adapter notify ───────────────────────────────────────────────────────
    // =========================================================================

    /** 在主线程调用 notifyDataSetChanged（会话列表 adapter 专用）。 */
    static void notifyConvAdapter(String tag) {
        // 优先：sConvAdapterRef（ConversationListView.setAdapter 写入，不受 q2/h0 污染）
        Object adapter = ConvFilter.getConvAdapter();
        if (adapter == null && ConvFilter.sConvListView != null) {
            adapter = ConvFilter.callMethod(ConvFilter.sConvListView, "getAdapter");
            if (adapter != null) {
                Log.i(ConvFilter.TAG, "[BUS:" + tag + "] adapter via view.getAdapter()="
                        + adapter.getClass().getSimpleName());
                ConvFilter.sConvAdapterRef = new WeakReference<>(adapter);
            }
        }
        if (adapter == null) {
            Object fallback = ConvFilter.sAdapterRef != null ? ConvFilter.sAdapterRef.get() : null;
            if (fallback != null && ConvFilter.ADAPTER_CLASS_71.equals(fallback.getClass().getName())) {
                adapter = fallback;
                Log.i(ConvFilter.TAG, "[BUS:" + tag + "] adapter via sAdapterRef(v0)="
                        + fallback.getClass().getSimpleName());
            } else if (fallback != null) {
                Log.w(ConvFilter.TAG, "[BUS:" + tag + "] sAdapterRef="
                        + fallback.getClass().getSimpleName() + " is not v0, skip");
            }
        }
        if (adapter == null) {
            Log.w(ConvFilter.TAG, "[BUS:" + tag + "] conv adapter null (view="
                    + ConvFilter.sConvListView + "), skip notify");
            return;
        }
        try {
            adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
            Log.i(ConvFilter.TAG, "[BUS:" + tag + "] notified adapter="
                    + adapter.getClass().getSimpleName() + " cache=" + sConvCache.size());
        } catch (Throwable t) {
            Log.w(ConvFilter.TAG, "[BUS:" + tag + "] notify fail: " + t);
        }
    }

    // =========================================================================
    // ── Adapter / view 刷新（LauncherUI resume 时使用）────────────────────────
    // =========================================================================

    /**
     * 从 Activity DecorView 树找到 ConversationListView 并刷新 adapter/mvvmList ref。
     * 主线程调用，pendingHide / pendingRestore Handler.post 里使用。
     */
    static void refreshAdapterFromActivity(android.app.Activity act) {
        Class<?> clvClass = ConvFilter.sConvListViewClass;
        if (clvClass == null) {
            Log.w(ConvFilter.TAG, "[CF:refresh] sConvListViewClass null, skip");
            return;
        }
        try {
            android.view.View root = act.getWindow().getDecorView();
            android.view.View found = findViewByClass(root, clvClass);
            if (found == null) {
                Log.w(ConvFilter.TAG, "[CF:refresh] ConvListView not found in DecorView");
                return;
            }
            ConvFilter.sConvListView = found;
            Object adapter = ConvFilter.callMethod(found, "getAdapter");
            if (adapter != null) {
                ConvFilter.sConvAdapterRef = new WeakReference<>(adapter);
                Log.i(ConvFilter.TAG,
                        "[CF:refresh] adapter from hierarchy: " + adapter.getClass().getName());
                Object mvvm = ConvFilter.findMvvmListOnAdapter(adapter);
                if (mvvm != null) {
                    ConvFilter.sMvvmListRef = new WeakReference<>(mvvm);
                    Log.i(ConvFilter.TAG, "[CF:refresh] MvvmList from adapter: "
                            + mvvm.getClass().getSimpleName());
                }
            } else {
                Log.w(ConvFilter.TAG, "[CF:refresh] getAdapter() returned null on ConvListView");
            }
        } catch (Throwable t) {
            Log.w(ConvFilter.TAG, "[CF:refresh] fail: " + t);
        }
    }

    /** BFS view tree walk；返回第一个属于 targetClass 的 View。 */
    private static android.view.View findViewByClass(android.view.View root, Class<?> targetClass) {
        if (root == null) return null;
        if (targetClass.isInstance(root)) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View found = findViewByClass(vg.getChildAt(i), targetClass);
                if (found != null) return found;
            }
        }
        return null;
    }

    // =========================================================================
    // ── DEV 模式兼容 ──────────────────────────────────────────────────────────
    // =========================================================================

    /**
     * Called by UiContextTracker when LauncherUI returns to foreground (DEV/HONEY only).
     * installLauncherResumeHook handles PROD. In DEV mode both run; the second clean
     * finds nothing to remove and is a fast no-op.
     */
    public static void triggerHideIfNeeded() {
        if (!StateMachine.getInstance().isActive()) return;
        ConvFilter.cleanConvData("resume-hide");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                notifyConvAdapter("resume-hide");
            }
        });
    }
}
