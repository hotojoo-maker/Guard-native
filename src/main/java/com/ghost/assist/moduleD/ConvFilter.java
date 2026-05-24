package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.DebugTelemetry;
import com.ghost.assist.debug.DebugTelemetry;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Conversation list filter - v3 (8.0.71 static analysis corrected 2026-05-20)
 *
 * Chain (8.0.71, static analysis):
 *   Adapter:      kc5.v0  (field f286278p → MvvmConvList)
 *   Item class:   kc5.y
 *   Contact:      kc5.y.d (field "d") → com.tencent.mm.storage.l4
 *   wxid getter:  l4.h1() → field_username  ← CORRECTED from C0()
 *   Batch insert: MvvmList.n(List, boolean)  ← CORRECTED from m() in 8.0.66
 *
 * Chain (8.0.66 reference):
 *   Adapter: f45.s0 (field p → MvvmList), Item: f45.u, Contact: m3, wxid: m3.j1()
 *   Batch insert: MvvmList.m(List, boolean)
 *
 * Hook layers:
 *   L1: MvvmList.n/m(?, boolean) / MvvmConvList.n/m(?, boolean) — getDeclaredMethods scan
 *   L2: MvvmList.s(?) — fallback
 *   L4: kc5.v0.notifyDataSetChanged — primary clean-before gate (confirmed working)
 *   INIT: warm-attach via ConversationListView constructor
 *
 *   extractWxid(): CONTACT_FIELD_NAMES("d","e",...) → WXID_GETTER_NAMES("h1","C0","j1",...)
 *                  fallback: scan all 0-param String methods for isWxid() match
 *
 * FAILURE_LOG constraints:
 *   No hook K0/getCount/getView; no notifyItemRange*; clean-before only
 */
public class ConvFilter {

    static final String TAG = "NCL";

    private static final String MVVMLIST_CLASS      = "com.tencent.mm.plugin.mvvmlist.MvvmList";
    // 8.0.71: MvvmList subclass for conversation list (static analysis confirmed)
    static final String MVVMCONV_CLASS      = "com.tencent.mm.ui.conversation.adapter.MvvmConvList";
    private static final String CONV_LIST_VIEW      = "com.tencent.mm.ui.conversation.ConversationListView";
    static final String ADAPTER_CLASS_71    = "kc5.v0";   // confirmed 8.0.71
    static final String ADAPTER_CLASS_66    = "f45.s0";

    // MvvmList internal ArrayList field names
    static final String[] MVVMLIST_ARRAY_FIELDS = {"o", "p", "h"};
    // Contact field on conversation item (8.0.66: item.d = m3 contact obj)
    private static final String[] CONTACT_FIELD_NAMES = {"d", "e", "f", "a", "b", "c"};
    // Methods on contact obj that return wxid
    // C0() = 8.0.71 l4.C0() → field_digestUser = wxid  (confirmed by live broad-scan 2026-05-22)
    // h1() = returns "officialaccounts" for public account items — NOT wxid for regular contacts
    private static final String[] WXID_GETTER_NAMES = {"C0", "h1", "j1", "i1", "k1", "getUsername", "getUserName"};
    // Fields on contact obj that hold wxid directly
    private static final String[] WXID_FIELD_NAMES = {"field_userName", "username", "d", "e"};
    private static final String UNREAD_FIELD = "field_unReadCount";
    // Adapter fields that may hold MvvmList — f286278p confirmed 8.0.71 kc5.v0
    private static final String[] MVVMLIST_HOLDER_FIELDS = {"f286278p", "p", "q", "o", "r", "a", "b"};

    private static volatile boolean sInstalled = false;
    private static volatile boolean gCleaning = false;
    private static volatile long gLastCleanMs = 0L;
    private static final long COOLDOWN_MS = 200L;

    /** Weak ref to the live conversation adapter — updated on each notifyDataSetChanged hit. */
    static volatile WeakReference<Object> sAdapterRef;
    /** 会话列表专用 adapter（ConversationListView.setAdapter 写入，不受 q2/h0 污染）。 */
    static volatile WeakReference<Object> sConvAdapterRef;
    /**
     * Strong ref to the ConversationListView itself.
     * The View is held by WeChat's main Activity layout — it lives as long as WeChat's main page
     * is alive, so it won't be GC'd even when the search page is on top.
     * We call view.getAdapter() at notify-time to always get the live adapter.
     */
    static volatile Object sConvListView = null;
    /** Saved at installAdapterDiscovery time; used to walk DecorView at onResume. */
    static volatile Class<?> sConvListViewClass = null;
    /** 8.0.71：MvvmConvList 实例（与 h0 adapter 解耦，L1/CL1 路径写入）。 */
    static volatile WeakReference<Object> sMvvmListRef;

    // 8.0.71 实测：会话 UI adapter = preference.h0，数据层 = MvvmConvList（h/o/p 字段）
    static final String ADAPTER_CLASS_71_H0 = "com.tencent.mm.ui.base.preference.h0";

    private static final Set<String> sDiagSeen = Collections.synchronizedSet(new HashSet<String>());
    private static volatile String sAdapterClassName = null;
    private static final Set<String> sHookedAdapters = Collections.synchronizedSet(new HashSet<String>());

    // ── 冷启动补刀窗口 ───────────────────────────────────────────────────────
    // 冷启动时 WeChat 从 SQLite cache 恢复会话列表，走直接字段写入路径，
    // 绕过 L0/L1/La 全部 hook（F-32 根因）。密友 item 在 MvvmList 里存在
    // 约 20-100ms，L4 第一次命中 kc5.v0 时补两次 clean+notify 把残影抹掉。
    // 保护条件：HIDDEN 态 + 进程启动后 15s 内 + 只执行一次。
    private static volatile boolean sColdCleanDone = false;
    // 进程启动时间基准（类加载时即记录，早于任何 Activity）
    private static final long sColdStartMs = android.os.SystemClock.elapsedRealtime();
    private static final long COLD_WINDOW_MS = 15_000L; // 冷启动补刀有效窗口

    // ── 冷启动/锁屏遮罩（C1/C2/F-32 视觉兜底）────────────────────────────
    // LauncherUI.onStart(HIDDEN) 时注入一个全屏不透明 View 盖住会话列表，
    // 直到 L4 cleaned>0 后淡出移除，彻底遮住密友闪现窗口和 DiffUtil 裂缝。
    // 锁屏场景（sColdCleanDone=true）：数据已干净，500ms 后自动移除。
    // 冷启动场景（sColdCleanDone=false）：等 L4 信号或 5s 安全移除。
    private static java.lang.ref.WeakReference<android.view.View> sColdStartOverlay = null;
    /** 熄屏后置 true；showColdStartOverlay 消费后重置，区分锁屏解锁与普通 Activity 切换。 */
    static volatile boolean sScreenWasLocked = false;
    static volatile boolean sScreenReceiverInstalled = false;

    // ── V→H pendingHide ──────────────────────────────────────────────────────
    // Set to true by BUS-H callback; cleared on the first LauncherUI.onResume
    // that fires AFTER the V→H transition. Ensures a re-clean+notify is
    // delivered even if WeChat repopulates MvvmList during onResume's own
    // data-push path (which runs after our 80ms BUS-H notify window).
    // Must NOT be shared with H→V restore logic.

    // ── H→V 热恢复：L0addAll 累积 Map ────────────────────────────────────────
    // 每次 addAll 在过滤前，把全量条目（含密友）按 wxid 写入 Map。
    // 多批次并发 addAll 都 merge 进来，不会互相覆盖。
    // BUS-V 时：Map.values() → MvvmList.n(fullList, false) → StateFlow 原子更新。
    // ConvHotReload.sAddAllReplaying 防止 BUS-V replay 触发自身 hook 递归。
    // sConvItemMap / CachedConvItem / sConvCache → moved to ConvHotReload.java

    // ─────────────────────────────────────────────────────────────────────────

    // 8.0.71: conversation item class (full qualified, stable)
    static final String CONV_MAIN_UI = "com.tencent.mm.ui.conversation.MainUI";

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        installH0DataHook(lpparam);
        installKc5AHook(lpparam);
        installMvvmListHooks(lpparam);
        installMvvmListL3Hooks(lpparam);
        installMvvmListEHook(lpparam);
        installMvvmConvHooks(lpparam);
        installMvvmConvConstructorHook(lpparam);
        installAddAllHook();
        installAdapterDiscovery(lpparam);
        installAdapterHook(lpparam);
        scheduleColdStartSweeps();

        // Hot-reload hooks (LauncherUI lifecycle) installed by ConvHotReload
        ConvHotReload.install(lpparam);

        // RefreshBus must be registered here (INIT timing rule)
        StateMachine.getInstance().addListener("ConvFilter",
                (oldState, newState) -> { /* log only */ });
        RefreshBus.getInstance().register("ConvFilter", hidden -> {
            StateMachine.State state = StateMachine.getInstance().getState();
            if (state == StateMachine.State.HIDDEN) {
                ConvHotReload.handleBusHidden();
            } else if (state == StateMachine.State.VISIBLE) {
                ConvHotReload.handleBusVisible();
            }
        });

        Log.i(TAG, "[CF] ConvFilter installed");
    }

    // ── Stub methods (implementations pending probe validation) ───────────────

    private static void installH0DataHook(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "[CF] Lh0 stub (h0 covered by L1/L4 in 8.0.71)");
    }

    private static void installMvvmListEHook(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "[CF] Le stub");
    }

    private static void installMvvmConvConstructorHook(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "[CF] MCL-ctor stub");
    }

    // =========================================================================
    // L1 + L2 - MvvmList entry hooks
    // Uses getDeclaredMethods() scan - not tied to exact parameter types
    // =========================================================================

    private static void installMvvmListHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> mvvmCls = lpparam.classLoader.loadClass(MVVMLIST_CLASS);

            // L1: 8.0.71 batch insert = n(List/ik3.d, boolean)  [was m() in 8.0.66]
            // Also hook m() for single-item path (belt+suspenders)
            int l1Count = 0;
            for (Method m : mvvmCls.getDeclaredMethods()) {
                String mn = m.getName();
                if (!"n".equals(mn) && !"m".equals(mn)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[1] != boolean.class) continue;
                final String sig = m.toGenericString();
                final String label = "n".equals(mn) ? "L1n" : "L1m";
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        rememberMvvmList(param.thisObject);
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null || list.isEmpty()) return;
                        String thisCls = param.thisObject.getClass().getName();
                        String itemCls = list.get(0).getClass().getName();
                        if (!sDiagSeen.contains(thisCls + label)) {
                            sDiagSeen.add(thisCls + label);
                            Log.i(TAG, "[CF:L1broad] " + label + " thisCls=" + thisCls
                                    + " sz=" + list.size() + " itemCls=" + itemCls);
                            Bridge.getInstance().addRawFeedLine(
                                    "[CF:L1broad] " + label + " " + thisCls + " item=" + itemCls);
                        }
                        // L1 is the path for hidden-friend conversations on cold start
                        // (they enter via MvvmList.n, not ArrayList.addAll).
                        String firstCls = list.get(0).getClass().getName();
                        if (CONV_MAIN_UI.equals(firstCls) || "kc5.y".equals(firstCls)) {
                            for (Object item : list) {
                                String wxid = extractWxid(item);
                                if (wxid != null) ConvHotReload.sConvItemMap.put(wxid, item);
                            }
                        }
                        filterConvList(list, label);
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 批次写入后立刻清 h/o/p，避免 0.3s 渲染泄露
                        if (StateMachine.getInstance().isActive()) {
                            cleanLiveMvvmList(param.thisObject, label + "-post");
                        }
                    }
                });
                l1Count++;
                Log.i(TAG, "[CF] L1 hooked: " + sig);
            }
            if (l1Count == 0) {
                // Dump all method names for diagnostics
                StringBuilder sb = new StringBuilder("[CF] L1 no match. All MvvmList methods:");
                for (Method m : mvvmCls.getDeclaredMethods()) {
                    sb.append(" ").append(m.getName())
                      .append("(").append(java.util.Arrays.toString(m.getParameterTypes())).append(")");
                }
                Log.w(TAG, sb.toString());
            }

            // L2: any method named "s" with exactly 1 param
            int l2Count = 0;
            for (Method m : mvvmCls.getDeclaredMethods()) {
                if (!"s".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                final String sig = m.toGenericString();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null) return;
                        filterConvList(list, "L2s");
                    }
                });
                l2Count++;
                Log.i(TAG, "[CF] L2 hooked: " + sig);
            }
            if (l2Count == 0) {
                Log.w(TAG, "[CF] L2 no match, will rely on L1");
            }

        } catch (Throwable e) {
            Log.w(TAG, "[CF] MvvmList load fail: " + e.getMessage());
        }
    }

    // =========================================================================
    // L3 - MvvmList single-item event channel  (HOOK_POINTS F04-L3)
    // 8.0.66 documented: MvvmList.w(nd3.o0) — triggered on new message push.
    // In 8.0.71 the method name / event class may differ; we scan ALL 1-param
    // non-List, non-boolean methods not already covered by L1/L2 (n/m/s).
    // Strategy: afterHookedMethod → direct cleanMvvmList(thisObject) so the
    // single newly-inserted item is removed before WeChat's own notify fires.
    // =========================================================================

    private static final Set<String> sL3HookedMethods =
            Collections.synchronizedSet(new HashSet<String>());

    // =========================================================================
    // L3 - MvvmList single-item event channel  (HOOK_POINTS F04-L3)
    // =========================================================================

    private static void installMvvmListL3Hooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> mvvmCls = lpparam.classLoader.loadClass(MVVMLIST_CLASS);
            int count = 0;
            for (Method m : mvvmCls.getDeclaredMethods()) {
                String mn = m.getName();
                // Already covered by L1/L2
                if ("n".equals(mn) || "m".equals(mn) || "s".equals(mn)) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1) continue;
                // Skip primitive / boolean single-param methods (getters/flags)
                Class<?> p0 = params[0];
                if (p0.isPrimitive() || p0 == Boolean.class) continue;
                // Skip methods returning non-void (likely getters)
                if (m.getReturnType() != void.class) continue;
                final String sig = m.toGenericString();
                if (!sL3HookedMethods.add(sig)) continue;
                final String label = "L3_" + mn;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!StateMachine.getInstance().isActive()) return;
                        if (Bridge.getInstance().allHiddenIds().isEmpty()) return;
                        try {
                            int removed = cleanMvvmList(param.thisObject);
                            if (removed > 0) {
                                Log.i(TAG, "[CF] " + label + " post-clean removed=" + removed);
                                Bridge.getInstance().addRawFeedLine(
                                        "[CF:" + label + "] removed=" + removed);
                                DebugTelemetry.getInstance().addRemoved("conv_l3", removed);
                            }
                        } catch (Throwable e) {
                            Log.w(TAG, "[CF] " + label + " clean fail: " + e.getMessage());
                        }
                    }
                });
                count++;
                Log.i(TAG, "[CF] L3 hooked: " + sig);
            }
            if (count == 0) {
                Log.w(TAG, "[CF] L3 no candidates on MvvmList");
            } else {
                Log.i(TAG, "[CF] L3 total hooked=" + count);
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CF] L3 install fail: " + e.getMessage());
        }
    }

    // =========================================================================
    // MvvmConvList hooks — scan both n() and m() since 8.0.71 uses n() for batch
    // =========================================================================

    // =========================================================================
    // La — kc5.a.a(List) 扼流点 hook
    //
    // 等价于 Catfish 8.0.70 va5.a.a(List) hookNewCon 注入点。
    // 所有会话数据（冷启动 PATH3:B + 热更新 addAll）必经此方法。
    // 动态实证 2026-05-22：消息触发时 sz=10，kc5.y item，✅ 命中。
    //
    // H/U 态：filterConvList 过滤密友 → 密友从未进入下游 ArrayList
    // V 态  ：直接放行，密友自然出现
    // =========================================================================

    private static final String KC5A_CLASS = "kc5.a";

    private static void installKc5AHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> kc5aCls = lpparam.classLoader.loadClass(KC5A_CLASS);
            int hooked = 0;
            for (final Method m : kc5aCls.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 1) continue;
                if (!java.util.List.class.isAssignableFrom(pt[0])
                        && !java.util.Collection.class.isAssignableFrom(pt[0])) continue;
                final String sig = m.toGenericString();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected void beforeHookedMethod(MethodHookParam param) {
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null || list.isEmpty()) return;

                        int before = list.size();
                        Log.i(TAG, "[CF:La] before size=" + before
                                + " active=" + StateMachine.getInstance().isActive());
                        // BEFORE filtering. La is the upstream choke point — hidden friends
                        // are removed here and never reach L1/L0addAll, so this is the only
                        // place we can capture their kc5.y objects for BUS-V restore.
                        if (!list.isEmpty()) {
                            String cn = list.get(0).getClass().getName();
                            if (CONV_MAIN_UI.equals(cn) || "kc5.y".equals(cn)) {
                                for (Object item : list) {
                                    String wxid = extractWxid(item);
                                    if (wxid != null) ConvHotReload.sConvItemMap.put(wxid, item);
                                }
                            }
                        }

                        if (!StateMachine.getInstance().isActive()) {
                            Log.i(TAG, "[CF:La] skip visible");
                            return;
                        }
                        Set<String> hidden = Bridge.getInstance().allHiddenIds();
                        if (hidden.isEmpty()) {
                            Log.i(TAG, "[CF:La] skip no-ids");
                            return;
                        }

                        filterConvList(list, "La");

                        int removed = before - list.size();
                        if (removed > 0) {
                            Log.i(TAG, "[CF:La] removed=" + removed);
                        }
                    }
                });
                hooked++;
                Log.i(TAG, "[CF] La hooked: " + sig);
            }
            if (hooked == 0) {
                Log.w(TAG, "[CF] La no a(List) found on " + KC5A_CLASS);
            }
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "[CF] La class not found: " + KC5A_CLASS);
        } catch (Throwable e) {
            Log.w(TAG, "[CF] La hook fail: " + e.getMessage());
        }
    }

    // =========================================================================
    // ArrayList.addAll hook — L0addAll 兜底（保留一轮观察，La 稳定后可降级）
    // MomentsFilter confirmed: [MF] addAll firstItem=MainUI size=4
    // =========================================================================

    // =========================================================================
    // MvvmConvList hooks — scan both n() and m() since 8.0.71 uses n() for batch
    // =========================================================================

    private static void installMvvmConvHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> convListCls = lpparam.classLoader.loadClass(MVVMCONV_CLASS);
            int l1Count = 0;
            for (Method m : convListCls.getDeclaredMethods()) {
                String mn = m.getName();
                if (!"n".equals(mn) && !"m".equals(mn)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[1] != boolean.class) continue;
                final String sig = m.toGenericString();
                final String label = "n".equals(mn) ? "CL1n" : "CL1m";
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null) return;
                        filterConvList(list, label);
                    }
                });
                l1Count++;
                Log.i(TAG, "[CF] MvvmConvList." + mn + " hooked: " + sig);
            }
            // Also hook any method named "s" with 1 param
            for (Method m : convListCls.getDeclaredMethods()) {
                if (!"s".equals(m.getName()) || m.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null) return;
                        filterConvList(list, "CL2s");
                    }
                });
            }
            if (l1Count == 0) {
                StringBuilder sb = new StringBuilder("[CF] MvvmConvList no n/m(?,boolean). methods:");
                for (Method m : convListCls.getDeclaredMethods())
                    sb.append(" ").append(m.getName()).append("(").append(m.getParameterTypes().length).append(")");
                Log.w(TAG, sb.toString());
            }
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "[CF] MvvmConvList not found: " + MVVMCONV_CLASS);
        } catch (Throwable e) {
            Log.w(TAG, "[CF] MvvmConvList hook fail: " + e.getMessage());
        }
    }

    // =========================================================================
    // ArrayList.addAll hook — 8.0.71 actual data path
    // MomentsFilter confirmed: [MF] addAll firstItem=MainUI size=4
    // =========================================================================

    private static void installAddAllHook() {
        try {
            Method addAll = java.util.ArrayList.class.getMethod("addAll", java.util.Collection.class);
            XposedBridge.hookMethod(addAll, new XC_MethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void beforeHookedMethod(MethodHookParam param) {
                    java.util.Collection<?> coll = (java.util.Collection<?>) param.args[0];
                    if (coll == null || coll.isEmpty()) return;
                    Object first = coll.iterator().next();
                    if (first == null) return;
                    if (!CONV_MAIN_UI.equals(first.getClass().getName())) return;

                    // Diag: first time dump all String fields on MainUI
                    if (sDiagSeen.add("MAINUI_fields")) {
                        dumpMainUiFields(first);
                    }

                    // Filter
                    filterConvList((List<Object>) param.args[0], "addAll");
                }
            });
            Log.i(TAG, "[CF] ArrayList.addAll hook ok (MainUI path)");
        } catch (Throwable e) {
            Log.w(TAG, "[CF] addAll hook fail: " + e.getMessage());
        }
    }

    /** Deep dump of kc5.y fields — find the wxid path. */
    private static void dumpMainUiFields(Object item) {
        try {
            String itemCls = item.getClass().getName();
            StringBuilder sb = new StringBuilder("[CF:itemdump cls=" + itemCls + "]");
            Class<?> cls = item.getClass();
            for (int depth = 0; cls != null && cls != Object.class && depth < 5; depth++) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v == null) continue;
                    String fn = f.getName();
                    if (v instanceof String) {
                        String s = (String) v;
                        if (!s.isEmpty()) sb.append(" .").append(fn).append("=").append(s.length() > 30 ? s.substring(0,30) : s);
                    } else if (v instanceof Integer || v instanceof Long || v instanceof Boolean) {
                        sb.append(" .").append(fn).append("=").append(v);
                    } else {
                        // nested object — scan ALL 0-param String methods + all String fields
                        String vcn = v.getClass().getName();
                        if (vcn.startsWith("java.") || vcn.startsWith("android.") || vcn.startsWith("kotlin.")) continue;
                        sb.append(" .").append(fn).append("[").append(vcn).append("]");
                        // scan all 0-param methods returning String
                        try {
                            Class<?> vc = v.getClass();
                            while (vc != null && vc != Object.class) {
                                for (Method nm : vc.getDeclaredMethods()) {
                                    if (nm.getParameterTypes().length != 0) continue;
                                    if (!nm.getReturnType().equals(String.class)) continue;
                                    try {
                                        nm.setAccessible(true);
                                        String r = (String) nm.invoke(v);
                                        if (r != null && !r.isEmpty())
                                            sb.append(".").append(nm.getName()).append("()=").append(r.length()>25?r.substring(0,25):r);
                                    } catch (Throwable ignored3) {}
                                }
                                vc = vc.getSuperclass();
                            }
                        } catch (Throwable ignored2) {}
                        // also try direct String fields
                        try {
                            for (Field nf : v.getClass().getDeclaredFields()) {
                                nf.setAccessible(true);
                                Object nv = nf.get(v);
                                if (nv instanceof String && !((String)nv).isEmpty())
                                    sb.append(".").append(nf.getName()).append("=").append(((String)nv).length()>20?((String)nv).substring(0,20):(String)nv);
                            }
                        } catch (Throwable ignored2) {}
                    }
                }
                cls = cls.getSuperclass();
            }
            Log.i(TAG, sb.toString());
            Bridge.getInstance().addRawFeedLine(sb.toString());
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // Adapter runtime discovery via ConversationListView.setAdapter
    // =========================================================================

    private static void installAdapterDiscovery(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clvCls = lpparam.classLoader.loadClass(CONV_LIST_VIEW);
            XC_MethodHook discoveryHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // hookMethodsByName walks the parent chain and hooks RecyclerView.setAdapter()
                    // too — meaning ANY RecyclerView's setAdapter fires this hook.
                    // Guard: only update conv refs when the calling View is actually a
                    // ConversationListView instance, not a search/other page's RecyclerView.
                    if (!clvCls.isInstance(param.thisObject)) return;

                    Object adapter = param.args[0];
                    if (adapter == null) return;
                    String cn = adapter.getClass().getName();
                    sConvAdapterRef = new WeakReference<>(adapter);
                    // Keep a strong ref to the View itself — won't be GC'd as long as
                    // WeChat's main activity is alive (survives search page navigation).
                    sConvListView = param.thisObject;
                    Log.i(TAG, "[CF] conv adapter set: " + cn);
                    if (!cn.equals(sAdapterClassName)) {
                        sAdapterClassName = cn;
                        Log.i(TAG, "[CF] adapter discovered: " + cn);
                        if (!ADAPTER_CLASS_66.equals(cn)) {
                            hookAdapterByClass(adapter.getClass());
                        }
                    }
                }
            };

            // Hook all setAdapter methods on ConversationListView and its parents
            int hooked = hookMethodsByName(clvCls, "setAdapter", 1, discoveryHook);
            if (hooked > 0) {
                Log.i(TAG, "[CF] adapter discovery hook ok (" + hooked + ")");
            } else {
                Log.w(TAG, "[CF] adapter discovery: setAdapter not found on " + CONV_LIST_VIEW);
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CF] adapter discovery load fail: " + e.getMessage());
        }
    }

    // =========================================================================
    // L4 - notifyDataSetChanged clean-before
    // =========================================================================

    private static void installAdapterHook(XC_LoadPackage.LoadPackageParam lpparam) {
        for (String cn : new String[]{ADAPTER_CLASS_71, ADAPTER_CLASS_71_H0, ADAPTER_CLASS_66}) {
            try {
                Class<?> adapterCls = lpparam.classLoader.loadClass(cn);
                hookAdapterByClass(adapterCls);
                Log.i(TAG, "[CF] L4 hooked hardcode " + cn);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable e) {
                Log.w(TAG, "[CF] L4 hardcode " + cn + " fail: " + e.getMessage());
            }
        }
    }

    private static void hookAdapterByClass(Class<?> adapterCls) {
        if (!sHookedAdapters.add(adapterCls.getName())) return;
        try {
            int n = hookMethodsByName(adapterCls, "notifyDataSetChanged", 0,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object adapter = param.thisObject;
                        String cn = adapter.getClass().getName();
                        sAdapterRef = new WeakReference<>(adapter);
                        // Prime sConvAdapterRef + sMvvmListRef from kc5.v0 only.
                        // L4 is the first reliable signal that kc5.v0 is alive (setAdapter /
                        // onAttachedToWindow hooks miss the cold-start window).
                        // Do NOT include ADAPTER_CLASS_71_H0 here: h0 fires L4 after kc5.v0
                        // and would overwrite sConvAdapterRef with the wrong adapter.
                        // q2 (search adapter) is also excluded because its name ≠ kc5.v0.
                        if (ADAPTER_CLASS_71.equals(cn)) {
                            sConvAdapterRef = new WeakReference<>(adapter);
                            Object mv = findMvvmListOnAdapter(adapter);
                            if (mv != null) {
                                rememberMvvmList(mv);
                                ConvHotReload.sConvMvvmListRef = new WeakReference<>(mv);
                            }

                            // ── 冷启动补刀 ────────────────────────────────────────────────
                            // 条件：① 首次命中 kc5.v0  ② HIDDEN 态  ③ 冷启动窗口内
                            // 作用：抹去 SQLite cache 直写路径留下的密友残影（F-32 根因）
                            // 保护：每次 run() 前重新检查 isActive()；flag 保证只执行一次；
                            //       不影响 H→V / V→H 热切换（flag 已置 true，不会再触发）
                            if (!sColdCleanDone
                                    && StateMachine.getInstance().isActive()
                                    && android.os.SystemClock.elapsedRealtime()
                                            - sColdStartMs < COLD_WINDOW_MS) {
                                sColdCleanDone = true;
                                Log.i(TAG, "[CF:cold] HIDDEN cold-start scheduled (100ms+300ms)");
                                final Handler ch = new Handler(Looper.getMainLooper());
                                ch.postDelayed(new Runnable() {
                                    @Override public void run() {
                                        if (!StateMachine.getInstance().isActive()) return;
                                        cleanConvData("cold-100");
                                        ConvHotReload.notifyConvAdapter("cold-100");
                                    }
                                }, 100);
                                ch.postDelayed(new Runnable() {
                                    @Override public void run() {
                                        if (!StateMachine.getInstance().isActive()) return;
                                        cleanConvData("cold-300");
                                        ConvHotReload.notifyConvAdapter("cold-300");
                                    }
                                }, 300);
                            }
                        } else if (ADAPTER_CLASS_71_H0.equals(cn)
                                && StateMachine.getInstance().isActive()
                                && !gCleaning) {
                            // ── h0 冷启动先手 clean ────────────────────────────────────────
                            // h0 在 8.0.71 冷启动时比 v0 早 ~2s 出现，且共享同一个 MvvmList。
                            // 在此清洗 → 密友在第一帧就已不在 MvvmList → v0 到来时 removed=0。
                            // 不更新 sConvAdapterRef（保持 R-05 铁律），仅做 MvvmList 清洗。
                            // 同样走 L4-NoDiff：cancel h0 的 notify + repost，避免 DiffUtil 动画。
                            Object h0mv = findMvvmListOnAdapter(adapter);
                            if (h0mv != null) {
                                int h0removed = cleanMvvmList(h0mv);
                                Log.i(TAG, "[CF:h0-L4] cold clean removed=" + h0removed);
                                if (h0removed > 0) {
                                    param.setResult(null);   // cancel h0 DiffUtil notify
                                    gCleaning = true;
                                    try {
                                        XposedBridge.invokeOriginalMethod(
                                                (java.lang.reflect.Method) param.method,
                                                adapter, new Object[0]);
                                    } catch (Throwable ignored) {}
                                    gCleaning = false;
                                }
                            }
                            return; // h0 处理完毕，不走 isConvListAdapter 路径
                        }
                        Log.i(TAG, "[CF:L4] entry adapter=" + adapter.getClass().getSimpleName()
                                + " cleaning=" + gCleaning
                                + " state=" + (StateMachine.getInstance().isActive() ? "H" : "V"));
                        if (gCleaning) return;
                        if (!isConvListAdapter(adapter)) return;
                        long now = System.currentTimeMillis();
                        if (now - gLastCleanMs < COOLDOWN_MS) return;
                        gLastCleanMs = now;

                        // L4-NoDiff: clean hidden items, then replace WeChat's notify with a
                        // synchronous clean call via invokeOriginalMethod.
                        //
                        // Background (F-32 root cause, 2026-05-23):
                        //   On cold start WeChat restores the conv list from SQLite cache via a
                        //   direct-field path that bypasses L0/L1/La hooks. Hidden items sit in
                        //   MvvmList.h/o/p until notifyDataSetChanged fires. The original approach
                        //   posted the replacement notify via Handler.post — on a cold-start-loaded
                        //   main thread this queued 400-500ms behind WeChat's own init work, leaving
                        //   a visible shadow window.
                        //
                        // Fix v2 (sync): cancel WeChat's notify, then call invokeOriginalMethod()
                        //   directly (synchronous, bypasses all Xposed hooks, safe inside
                        //   beforeHookedMethod because param.setResult(null) already prevents the
                        //   original from running again). gCleaning=true prevents re-entry.
                        //   Result: UI updates in the SAME frame, zero deferred delay.
                        //
                        // Rollback: comment out param.setResult + invokeOriginalMethod lines and
                        //   uncomment the Handler.post block below; WeChat's original notify resumes.
                        int removed = l4CleanAndCount();
                        Log.i(TAG, "[CF:L4] cleaned=" + removed);
                        if (removed == 0 && !StateMachine.getInstance().isActive()) {
                            // VISIBLE mode: inject any missing cached items into the backing array
                            // BEFORE WeChat's notifyDataSetChanged rebinds the adapter.
                            // Covers the background→foreground resume case where WeChat directly
                            // writes to the backing field (bypassing La/CL1n hooks), then fires
                            // notifyDataSetChanged. Without this, the friend never reappears until
                            // a new message triggers WeChat's own n() through CL1n.
                            synchronized (ConvHotReload.sConvCache) {
                                if (!ConvHotReload.sConvCache.isEmpty()) {
                                    Object ml = ConvHotReload.getConvMvvmList();
                                    if (ml != null) {
                                        List<ConvHotReload.CachedConvItem> snap =
                                                new java.util.ArrayList<>(ConvHotReload.sConvCache);
                                        int inj = ConvHotReload.restoreToMvvmList(ml, snap);
                                        if (inj > 0) {
                                            Log.i(TAG, "[CF:L4-inject] injected=" + inj);
                                        }
                                    }
                                }
                            }
                        }
                        if (removed > 0) {
                            // [L4-NoDiff-v2] cancel WeChat's notify (suppresses DiffUtil animation)
                            param.setResult(null);
                            // [L4-NoDiff-v2] synchronous replacement notify — no Handler.post delay
                            gCleaning = true;
                            try {
                                XposedBridge.invokeOriginalMethod(
                                        (java.lang.reflect.Method) param.method,
                                        adapter, new Object[0]);
                                Log.i(TAG, "[CF:L4] sync-notify ok");
                                // Overlay removal: data is now clean, fade out mask
                                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override public void run() {
                                        removeColdStartOverlay("L4-clean");
                                    }
                                }, 100);
                            } catch (Throwable t) {
                                Log.w(TAG, "[CF:L4] sync-notify fail: " + t);
                            } finally {
                                gCleaning = false;
                            }
                        }
                        // else: nothing removed → let WeChat's original notify proceed normally
                    }
                });
            if (n > 0) {
                Log.i(TAG, "[CF] L4 notifyDataSetChanged hooked: " + adapterCls.getName());
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CF] L4 hook fail on " + adapterCls.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clean hidden items from MvvmList and return removal count for L4-NoDiff decision.
     * Returns 0 if nothing removed or state is VISIBLE — caller will let WeChat's notify proceed.
     */
    private static int l4CleanAndCount() {
        if (!StateMachine.getInstance().isActive()) return 0;
        if (Bridge.getInstance().allHiddenIds().isEmpty()) return 0;
        Object mvvmList = getLiveMvvmList();
        if (mvvmList != null) {
            gCleaning = true;
            try {
                return cleanMvvmList(mvvmList);
            } finally {
                gCleaning = false;
            }
        }
        // mvvmList ref not yet available — fall back to adapter-path clean.
        // Return 0: we can't count what was removed, so don't cancel WeChat's notify.
        cleanConvData("L4");
        return 0;
    }

    // =========================================================================
    // INIT warm-attach
    // =========================================================================

    private static void installWarmAttach(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clvCls = lpparam.classLoader.loadClass(CONV_LIST_VIEW);
            XC_MethodHook warmHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    scheduleWarmAttach(param.thisObject);
                }
            };
            int hooked = 0;
            for (Constructor<?> ctor : clvCls.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, warmHook);
                hooked++;
            }
            Log.i(TAG, "[CF] INIT warm-attach hook ok (" + hooked + " ctors)");
        } catch (Throwable e) {
            Log.w(TAG, "[CF] INIT warm-attach fail: " + e.getMessage());
        }
    }

    private static void scheduleWarmAttach(final Object convListView) {
        // delay=200ms: give WeChat time to fully attach the adapter and populate MvvmList
        // before we attempt to clean. 0ms was occasionally too early on slow hardware.
        // runs before the first RecyclerView frame is drawn — prevents cold-start flash of
        // hidden friends (F-32).  2000ms was Step1-verify; 0ms is the live value.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Object adapter = callMethod(convListView, "getAdapter");
                    if (adapter == null) return;
                    sConvAdapterRef = new WeakReference<>(adapter);
                    Log.i(TAG, "[CF:warm] start adapter=" + adapter.getClass().getSimpleName());

                    Object mvvmList = findMvvmListOnAdapter(adapter);
                    if (mvvmList == null) {
                        Log.i(TAG, "[CF:warm] mvvmList=null skip");
                        return;
                    }

                    sMvvmListRef = new WeakReference<>(mvvmList);
                    // Do NOT call safeWarmCleanAndNotify here — its isLauncherUiActive() guard
                    // returns false during cold start (no Activity.onResume yet), so it skips
                    // the clean and hidden items remain visible for the first render frame.
                    // We clean directly and post our own notify (same NoDiff pattern as L4).
                    if (!StateMachine.getInstance().isActive()) return;
                    if (Bridge.getInstance().allHiddenIds().isEmpty()) return;
                    gCleaning = true;
                    int removed;
                    try {
                        removed = cleanMvvmList(mvvmList);
                    } finally {
                        gCleaning = false;
                    }
                    Log.i(TAG, "[CF:warm] clean removed=" + removed);
                    if (removed == 0) return;
                    gLastCleanMs = System.currentTimeMillis();
                    final Object adapterSnap = adapter;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            gCleaning = true;
                            try {
                                adapterSnap.getClass()
                                        .getMethod("notifyDataSetChanged")
                                        .invoke(adapterSnap);
                                Log.i(TAG, "[CF:warm] deferred-notify ok");
                            } catch (Throwable t) {
                                Log.w(TAG, "[CF:warm] deferred-notify fail: " + t);
                            } finally {
                                gCleaning = false;
                            }
                        }
                    });
                } catch (Throwable e) {
                    Log.w(TAG, "[CF] INIT warm-attach run fail: " + e.getMessage());
                }
            }
        }, 200);
    }

    // =========================================================================
    // V→H pendingHide — Activity.recreate() on LauncherUI.onResume
    //
    // Why recreate() (nuclear / Catfish-style):
    //   BUS-H fires while Activity is STOPPING. Any MvvmList clean or
    //   notifyDataSetChanged call at that moment hits an off-screen View.
    //   On resume, WeChat repopulates the conv list via its own data-push
    //   path AFTER onResume — hidden items return. Rather than chasing
    //   adapter refs and timing windows, we let Android rebuild LauncherUI
    //   from scratch. The new instance starts with state=HIDDEN, so L1/La
    //   扼流点 filter hidden contacts on the fresh data-push path naturally.
    //
    //   hookAllMethods(Activity.class, "onResume", after) fires after the
    //   Activity.onResume() base-class call returns. We post clean+notify on the
    //   main-thread queue so it runs after LauncherUI.onResume() fully completes,
    //   including any post-super data push.
    //
    // =========================================================================

    

    /**
     * Time-based cold-start补刀 — fires at 500ms / 1500ms / 3000ms after module init,
     * regardless of whether L4 has fired. Covers the window where WeChat renders from
     * in-memory cache before the first kc5.v0.notifyDataSetChanged fires.
     *
     * Runs only if:  ① state = HIDDEN  ② within COLD_WINDOW_MS of sColdStartMs
     * No-op if refs are still null (addAll/La already filtered at injection time).
     */
    private static void scheduleColdStartSweeps() {
        final Handler h = new Handler(Looper.getMainLooper());
        final long[] delaysMs = {500L, 1500L, 3000L};
        for (final long d : delaysMs) {
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!StateMachine.getInstance().isActive()) return;
                    if (android.os.SystemClock.elapsedRealtime() - sColdStartMs > COLD_WINDOW_MS) return;
                    Log.i(TAG, "[CF:cold-init] sweep @" + d + "ms");
                    cleanConvData("cold-init-" + d);
                    ConvHotReload.notifyConvAdapter("cold-init-" + d);
                }
            }, d);
        }
    }

    /**
     * Show a full-screen opaque overlay on LauncherUI to mask the cold-start hidden-friend
     * flash (C1) and DiffUtil crack (F-32), and the lock-screen frozen-frame residual (C2).
     *
     * Called from LauncherUI.onStart when state=HIDDEN. The overlay matches the window
     * background color so it looks like a natural loading state to users.
     *
     * Removal is triggered by L4 after cleaned>0 (cold start) or by a 500ms safety timer
     * (lock screen, where data is already clean before onStart fires).
     */
    static void showColdStartOverlay(android.app.Activity act) {
        if (sColdStartOverlay != null && sColdStartOverlay.get() != null) return;
        // 只在冷启动（进程刚起，sColdCleanDone=false）或锁屏解锁（sScreenWasLocked=true）时显示
        // 普通 Activity 切换（从聊天/设置页返回）两个条件都不满足，直接跳过
        boolean isColdStart  = !sColdCleanDone;
        boolean isLockScreen = sScreenWasLocked;
        if (!isColdStart && !isLockScreen) {
            Log.i(TAG, "[CF:overlay] skip (normal resume)");
            return;
        }
        sScreenWasLocked = false; // 消费锁屏标记
        // 高性能模式：用户主动选择不要遮罩
        if (StateMachine.getInstance().isHighPerfMode()) {
            Log.i(TAG, "[CF:overlay] skip (highPerfMode)");
            return;
        }
        try {
            android.view.ViewGroup root =
                    (android.view.ViewGroup) act.getWindow().getDecorView();
            final android.view.View overlay = new android.view.View(act);
            overlay.setBackgroundColor(0xFFFFFFFF);
            android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            root.addView(overlay, lp);
            sColdStartOverlay = new java.lang.ref.WeakReference<>(overlay);
            Log.i(TAG, "[CF:overlay] shown coldCleanDone=" + sColdCleanDone);
            // Safety removal: lock screen (data already clean) 500ms; cold start 5s
            long safetyMs = sColdCleanDone ? 500L : 5000L;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { removeColdStartOverlay("safety"); }
            }, safetyMs);
        } catch (Throwable t) {
            Log.w(TAG, "[CF:overlay] show fail: " + t);
        }
    }

    /** Remove the cold-start overlay with a 200ms fade-out. Safe to call multiple times. */
    static void removeColdStartOverlay(final String src) {
        final android.view.View v =
                sColdStartOverlay != null ? sColdStartOverlay.get() : null;
        if (v == null) return;
        sColdStartOverlay = null;
        try {
            v.animate().alpha(0f).setDuration(200)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            try {
                                android.view.ViewGroup p =
                                        (android.view.ViewGroup) v.getParent();
                                if (p != null) p.removeView(v);
                                Log.i(TAG, "[CF:overlay] removed src=" + src);
                            } catch (Throwable ignored) {}
                        }
                    }).start();
        } catch (Throwable t) {
            // animate() not available (View not attached?), remove directly
            try {
                android.view.ViewGroup p = (android.view.ViewGroup) v.getParent();
                if (p != null) p.removeView(v);
            } catch (Throwable ignored) {}
            Log.w(TAG, "[CF:overlay] remove fallback src=" + src);
        }
    }

    /**
     * Walk the Activity's DecorView to find the live ConversationListView and its
     * current adapter. Updates sConvListView + sConvAdapterRef in place so that
     * cleanConvData/restoreCachedItems/notifyConvAdapter can work without a recreate().
     *
     * Called on the main thread from pendingHide / pendingRestore Handler.post blocks.
     * Cost: O(view-count) tree walk, done at most once per state-change transition.
     */
    

    

    

    /**
     * Step 2 (apply after Step 1 confirms crack is gone):
     * Safe warm-attach: clean + notify only when LauncherUI is in foreground.
     * Replace scheduleWarmAttach's postDelayed body with this call.
     */
    @SuppressWarnings("unused")
    private static void safeWarmCleanAndNotify(Object adapter, Object mvvmList) {
        Log.i(TAG, "[CF:warm] start adapter=" + adapter.getClass().getSimpleName());
        int cleaned = cleanMvvmList(mvvmList);
        Log.i(TAG, "[CF:warm] clean removed=" + cleaned);
        if (cleaned == 0) return;

        if (!isLauncherUiActive()) {
            Log.i(TAG, "[CF:warm] skip not launcher/conversation");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - gLastCleanMs < 1500L) {
            Log.i(TAG, "[CF:warm] cooldown skip");
            return;
        }
        gLastCleanMs = now;
        ConvHotReload.notifyConvAdapter("warm");
        Log.i(TAG, "[CF:warm] notify full");
    }

    /** True only when WeChat's main conv-list Activity is in the foreground. */
    private static boolean isLauncherUiActive() {
        try {
            android.app.Activity act =
                    com.ghost.assist.debug.UiContextTracker.getCurrentActivity();
            if (act == null) return false;
            String cn = act.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            return cn.contains("launcherui") || cn.contains("mainui");
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =========================================================================
    // Core filter logic
    // =========================================================================

    /**
     * VISIBLE 模式专用：把 ConvHotReload.sConvCache 里的密友/密群注入到 WeChat 即将写入 MvvmConvList 的 list 中。
     * WeChat 自己的 reload（n() 调用）不包含未活跃的密友（分页/不在前30条），
     * 不注入则 BUS-V restore 会被 WeChat 下一次 n() 覆盖消失。
     * 只在 MvvmConvList.n/m 的 beforeHook 调用，不在 La/L1（MvvmList 基类）调用。
     */
    

    /** Delegating wrapper — called by UiContextTracker / B-module triggers. */
    public static void triggerHideIfNeeded() {
        ConvHotReload.triggerHideIfNeeded();
    }
    static void filterConvList(List<Object> list, String label) {
        // Phase 1: unconditionally collect wxids for debug UI (no hidden-mode check)
        for (Object item : list) {
            if (item == null) continue;
            String cn = item.getClass().getName();
            if (sDiagSeen.add("CF_" + cn)) {
                Log.i(TAG, "[CF:diag] item class=" + cn);
                Bridge.getInstance().addRawFeedLine("[CF:diag] item class=" + cn);
            }
            String wxid = extractWxid(item);
            if (wxid != null && !wxid.startsWith("gh_") && !wxid.startsWith("notifymessage")) {
                // 顺手提取群名（kc5.y.f 字段 = 群成员名拼接）
                String nick = null;
                if (wxid.endsWith("@chatroom")) {
                    nick = getStrField(item, "f");
                    if (nick == null) nick = getStrField(item, "e");
                    if (nick != null && nick.length() > 20) nick = nick.substring(0, 20) + "…";
                    Log.i(TAG, "[CF:group] chatroom=" + wxid + " name=" + nick);
                }
                Bridge.getInstance().addConvWxid(wxid, nick);
            }
        }

        // Phase 2: filter (hidden mode only)
        boolean active = StateMachine.getInstance().isActive();
        Log.i(TAG, "[CF:filter] label=" + label + " active=" + active + " items=" + list.size());
        if (!active) return;
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        if (hidden.isEmpty()) return;

        int before = list.size();
        int idx = 0;
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) { idx++; continue; }
            String wxid = extractWxid(item);
            if (wxid == null) { idx++; continue; }
            if ("weixin".equals(wxid) && hasUnread(item)) { idx++; continue; }
            if (hidden.contains(wxid)) {
                it.remove();
                // fieldName=null: item 被拦截在 MvvmList 写入前，restore 时注入主字段
                ConvHotReload.putCache(wxid, item, null, idx);
                Log.i(TAG, "[CF] " + label + " removed wxid=" + wxid);
                InterceptCounter.getInstance().incF04(wxid);
                DebugTelemetry dt = DebugTelemetry.getInstance();
                dt.emit("conv", "conv_blocked",
                        DebugTelemetry.fields("wxid", wxid, "label", label));
                dt.addBlocked("conv");
                // idx not incremented: removed item, next slides in
            } else {
                idx++;
            }
        }
        int removed = before - list.size();
        if (removed > 0) {
            Bridge.getInstance().addRawFeedLine("[CF:" + label + "] removed=" + removed);
            DebugTelemetry.getInstance().addRemoved("conv", removed);
        }
    }

    private static void cleanAdapterMvvmList(Object adapter) {
        Object mvvmList = findMvvmListOnAdapter(adapter);
        if (mvvmList == null) mvvmList = getLiveMvvmList();
        if (mvvmList == null) {
            Log.w(TAG, "[CF:clean] mvvmList=null adapter="
                    + (adapter != null ? adapter.getClass().getSimpleName() : "null"));
            if (adapter != null && sDiagSeen.add("L4_no_mvvm_" + adapter.getClass().getName()))
                Log.w(TAG, "[CF] L4 MvvmList not found on " + adapter.getClass().getName());
            return;
        }
        cleanLiveMvvmList(mvvmList, "adapter="
                + (adapter != null ? adapter.getClass().getSimpleName() : "?"));
    }

    /** 8.0.71 主路径：h0 adapter 与 MvvmConvList 解耦，优先用 sMvvmListRef。 */
    static void cleanConvData(String source) {
        Object mvvmList = getLiveMvvmList();
        if (mvvmList != null) {
            cleanLiveMvvmList(mvvmList, source);
            return;
        }
        Object adapter = getConvAdapter();
        if (adapter != null) cleanAdapterMvvmList(adapter);
    }

    private static void cleanLiveMvvmList(Object mvvmList, String source) {
        rememberMvvmList(mvvmList);
        int hiddenCount = Bridge.getInstance().allHiddenIds().size();
        Log.i(TAG, "[CF:clean] start ids=" + hiddenCount
                + " src=" + source
                + " mvvm=" + mvvmList.getClass().getSimpleName());

        if (sDiagSeen.add("mvvmfields_" + mvvmList.getClass().getSimpleName())) {
            dumpMvvmListFields(mvvmList);
        }
        collectFromMvvmList(mvvmList);

        if (!StateMachine.getInstance().isActive()) return;
        if (hiddenCount == 0) return;
        gCleaning = true;
        try {
            int total = cleanMvvmList(mvvmList);
            Log.i(TAG, "[CF:clean] removed=" + total + " src=" + source);
        } finally {
            gCleaning = false;
        }
    }

    private static void rememberMvvmList(Object mvvmList) {
        if (mvvmList == null || !isMvvmListInstance(mvvmList)) return;
        sMvvmListRef = new WeakReference<>(mvvmList);
        if (sDiagSeen.add("mvvmref_" + mvvmList.getClass().getName())) {
            Log.i(TAG, "[CF:mvvmref] remember " + mvvmList.getClass().getName());
        }
    }

    static Object getLiveMvvmList() {
        Object m = sMvvmListRef != null ? sMvvmListRef.get() : null;
        if (m != null) return m;
        Object adapter = getConvAdapter();
        if (adapter != null) return findMvvmListOnAdapter(adapter);
        return null;
    }

    

    /** One-time: scan ALL declared fields on MvvmList to find which hold a List. */
    private static void dumpMvvmListFields(Object mvvmList) {
        try {
            StringBuilder sb = new StringBuilder(
                    "[CF:mvvmdump cls=" + mvvmList.getClass().getName() + "]");
            Class<?> cls = mvvmList.getClass();
            int depth = 0;
            while (cls != null && cls != Object.class && depth++ < 6) {
                for (Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(mvvmList);
                        if (v == null) continue;
                        if (v instanceof List) {
                            List<?> lst = (List<?>) v;
                            String firstCls = lst.isEmpty() ? "empty"
                                    : lst.get(0) == null ? "null"
                                    : lst.get(0).getClass().getName();
                            sb.append(" .").append(f.getName())
                              .append("[List sz=").append(lst.size())
                              .append(" item=").append(firstCls).append("]");
                        } else {
                            sb.append(" .").append(f.getName())
                              .append(":").append(f.getType().getSimpleName());
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
            Log.i(TAG, sb.toString());
            Bridge.getInstance().addRawFeedLine(sb.toString());
        } catch (Throwable e) {
            Log.w(TAG, "[CF:mvvmdump] fail: " + e);
        }
    }

    /** Unconditional scan — populates convSeen + diag, no filtering. */
    @SuppressWarnings("unchecked")
    private static void collectFromMvvmList(Object mvvmList) {
        boolean found = false;
        for (String fieldName : MVVMLIST_ARRAY_FIELDS) {
            try {
                Field f = findFieldRecursive(mvvmList.getClass(), fieldName);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(mvvmList);
                if (!(arr instanceof List)) continue;
                List<Object> list = (List<Object>) arr;
                if (list.isEmpty()) continue;
                // Log first-seen item class
                Object first = list.get(0);
                if (first == null) continue;
                String cn = first.getClass().getName();
                if (sDiagSeen.add("L4item_" + cn)) {
                    Log.i(TAG, "[CF:L4diag] field=" + fieldName + " sz=" + list.size()
                            + " itemCls=" + cn);
                    dumpMainUiFields(first);
                }
                // Collect wxids for debug UI
                filterConvList(list, "L4collect");  // Phase 1 only (hidden off → no removal)
                found = true;
                break; // first non-empty array is enough
            } catch (Throwable ignored) {}
        }
        if (!found) {
            if (sDiagSeen.add("L4collect_miss_" + mvvmList.getClass().getSimpleName())) {
                Log.w(TAG, "[CF:collect] no array hit tried="
                        + java.util.Arrays.toString(MVVMLIST_ARRAY_FIELDS)
                        + " cls=" + mvvmList.getClass().getName()
                        + "  ← see [CF:mvvmdump] above for real field names");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static int cleanMvvmList(Object mvvmList) {
        rememberMvvmList(mvvmList);
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        int total = 0;
        // Lazy-clear cache: only wipe when we actually find items to remove.
        // If MvvmList is already clean (removed=0), old cache is preserved so
        // the next H→V restore can still inject items back. Clearing before
        // the loop (or unconditionally in BUS-H) was the root cause of
        // "cache=0 death loop" where restore had no data to inject (F-34).
        boolean cacheCleared = false;
        // Diagnostic: fire once per clean pass when removed=0 to expose wxid extraction failures.
        // Tag "diag-clean" fires at most once per MvvmList pass (sDiagSeen guards log volume).
        boolean diagFired = false;
        for (String fn : MVVMLIST_ARRAY_FIELDS) {
            try {
                Field f = findFieldRecursive(mvvmList.getClass(), fn);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(mvvmList);
                if (!(arr instanceof List)) continue;
                List<Object> list = (List<Object>) arr;
                // Diagnostic snapshot: first non-empty field, first 3 items, only logged once per session
                if (!list.isEmpty() && !diagFired && sDiagSeen.add("clean_diag_" + fn)) {
                    diagFired = true;
                    StringBuilder sb = new StringBuilder("[CF:diag-clean] field=").append(fn)
                            .append(" sz=").append(list.size())
                            .append(" hidden=").append(hidden.size());
                    int dumpCnt = 0;
                    for (Object di : list) {
                        if (dumpCnt++ >= 3) break;
                        if (di == null) { sb.append(" [null]"); continue; }
                        String dw = extractWxid(di);
                        sb.append(" [").append(di.getClass().getSimpleName())
                          .append(" wxid=").append(dw)
                          .append(" inHidden=").append(dw != null && hidden.contains(dw))
                          .append("]");
                    }
                    Log.w(TAG, sb.toString());
                }
                int idx = 0;
                Iterator<Object> it = list.iterator();
                while (it.hasNext()) {
                    Object item = it.next();
                    if (item == null) { idx++; continue; }
                    String wxid = extractWxid(item);
                    if (wxid == null) { idx++; continue; }
                    if ("weixin".equals(wxid) && hasUnread(item)) { idx++; continue; }
                    if (hidden.contains(wxid)) {
                        it.remove();
                        // putCache already de-dupes by wxid (replaces existing entry).
                        // We never clear the whole cache here — clearing on each BUS-H
                        // was the root cause of "add 2nd friend → 1st friend lost" (F-34).
                        ConvHotReload.putCache(wxid, item, fn, idx);
                        total++;
                        // idx not incremented: item removed, next item slides into same slot
                    } else {
                        idx++;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return total;
    }

    

    /**
     * H→V 热恢复：把缓存 item 注回当前 MvvmList 内部数组。
     * 策略：
     *   1. 找当前 adapter 的 MvvmList。
     *   2. 确定目标数组字段（优先 fieldName 匹配，否则取首个含 conv item 的 List 字段）。
     *   3. 按 originalIndex 插入（clamp 到实际 size），跳过已存在 wxid（去重）。
     *   4. WeChat 下一次 Flow 刷新后 item 会被自然重新推入，缓存随下一次 H 态自动重建。
     */
    

    /**
     * In-place injection of cached hidden items into MvvmConvList backing array.
     * Mirrors cleanMvvmList (in-place remove) — finds the FIRST non-empty conv-item
     * List field (o/p/h), adds any missing cached items, returns injected count.
     *
     * Why in-place instead of n():
     *   n(newList) REPLACES the backing field with a new List object. The adapter
     *   still holds a reference to the OLD List → no visual change on rebind.
     *   In-place add modifies the SAME List the adapter already references, so the
     *   next notifyDataSetChanged immediately shows the injected items (same as how
     *   cleanMvvmList in-place remove hides items).
     */
    

    // =========================================================================
    // wxid extraction
    // =========================================================================

    static String extractWxid(Object item) {
        if (item == null) return null;
        for (String contactFieldName : CONTACT_FIELD_NAMES) {
            Object contact = getFieldSafe(item, contactFieldName);
            if (contact == null || contact instanceof String) continue;
            String ccn = contact.getClass().getName();
            if (ccn.startsWith("java.") || ccn.startsWith("android.") || ccn.startsWith("kotlin.")) continue;
            // fast path: try known getter names first (C0 for 8.0.71, j1 for 8.0.66)
            for (String getter : WXID_GETTER_NAMES) {
                String wxid = callStrMethod(contact, getter);
                if (isWxid(wxid)) return wxid;
            }
            for (String fn : WXID_FIELD_NAMES) {
                String wxid = getStrField(contact, fn);
                if (isWxid(wxid)) return wxid;
            }
            // broad-scan fallback: scan ALL 0-param String methods on contact class hierarchy
            try {
                Class<?> c = contact.getClass();
                while (c != null && c != Object.class) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (m.getParameterTypes().length != 0) continue;
                        if (!m.getReturnType().equals(String.class)) continue;
                        try {
                            m.setAccessible(true);
                            String r = (String) m.invoke(contact);
                            if (isWxid(r)) {
                                Log.i(TAG, "[CF] wxid found via broad-scan: " + m.getName() + "()=" + r);
                                return r;
                            }
                        } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable ignored) {}
        }
        for (String fn : WXID_FIELD_NAMES) {
            String wxid = getStrField(item, fn);
            if (isWxid(wxid)) return wxid;
        }
        return null;
    }

    private static boolean hasUnread(Object item) {
        for (String contactFieldName : CONTACT_FIELD_NAMES) {
            Object contact = getFieldSafe(item, contactFieldName);
            if (contact == null) continue;
            try {
                Field f = findFieldRecursive(contact.getClass(), UNREAD_FIELD);
                if (f == null) continue;
                f.setAccessible(true);
                Object v = f.get(contact);
                if (v instanceof Integer && (Integer) v > 0) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    static Object findMvvmListOnAdapter(Object adapter) {
        // Priority: try known field name first
        for (String fn : MVVMLIST_HOLDER_FIELDS) {
            Object v = getFieldSafe(adapter, fn);
            if (v != null && isMvvmListInstance(v)) return v;
        }
        // Fallback: scan all fields by superclass hierarchy
        try {
            Class<?> cls = adapter.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(adapter);
                    if (v != null && isMvvmListInstance(v)) {
                        if (sDiagSeen.add("mvvmfield_" + f.getName()))
                            Log.i(TAG, "[CF] MvvmList on adapter field=" + f.getName()
                                    + " type=" + v.getClass().getName());
                        return v;
                    }
                    // 8.0.71 部分 adapter 把 MvvmList 包在 holder 里（一层嵌套）
                    if (v != null && isTencentHolder(v)) {
                        Object nested = findMvvmListInObject(v, 1);
                        if (nested != null) {
                            if (sDiagSeen.add("mvvmnested_" + f.getName()))
                                Log.i(TAG, "[CF] MvvmList nested via adapter."
                                        + f.getName() + " holder=" + v.getClass().getName());
                            return nested;
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 取会话列表 adapter；仅 ConversationListView.setAdapter 写入的 ref 有效。
     *  不 fallback 到 sAdapterRef：sAdapterRef 会被任何 h0 实例（含搜索页 q2）覆盖。 */
    static Object getConvAdapter() {
        return sConvAdapterRef != null ? sConvAdapterRef.get() : null;
    }

    

    /** 判断 adapter 是否属于会话列表 UI（8.0.71 = preference.h0）。 */
    private static boolean isConvListAdapter(Object adapter) {
        if (adapter == null) return false;
        Object conv = sConvAdapterRef != null ? sConvAdapterRef.get() : null;
        if (conv == adapter) return true;
        String cn = adapter.getClass().getName();
        if (ADAPTER_CLASS_71_H0.equals(cn) || ADAPTER_CLASS_71.equals(cn)
                || ADAPTER_CLASS_66.equals(cn)) return true;
        return findMvvmListOnAdapter(adapter) != null;
    }

    static boolean isTencentHolder(Object obj) {
        String cn = obj.getClass().getName();
        return cn.startsWith("com.tencent.mm") || cn.startsWith("kc5.")
                || cn.startsWith("f45.") || cn.startsWith("ik3.");
    }

    static Object findMvvmListInObject(Object root, int depth) {
        if (root == null || depth > 2) return null;
        try {
            Class<?> cls = root.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(root);
                    if (v == null) continue;
                    if (isMvvmListInstance(v)) return v;
                    if (depth < 2 && isTencentHolder(v)) {
                        Object nested = findMvvmListInObject(v, depth + 1);
                        if (nested != null) return nested;
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Returns true if obj is MvvmList or any subclass of it. */
    static boolean isMvvmListInstance(Object obj) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            if (MVVMLIST_CLASS.equals(c.getName())) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    // =========================================================================
    // List extraction - handles both direct List and wrapped interface (ik3.d)
    // =========================================================================

    // =========================================================================
    // List extraction helper
    // =========================================================================

    /**
     * 8.0.66: arg is java.util.List directly.
     * 8.0.71: arg is interface ik3.d (a Kotlin Flow wrapper), actual List is a field inside.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractListFromArg(Object arg) {
        if (arg == null) return null;
        if (arg instanceof List) return (List<Object>) arg;
        // Scan fields for a List instance
        try {
            Class<?> cls = arg.getClass();
            for (int depth = 0; cls != null && cls != Object.class && depth < 3; depth++) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(arg);
                    if (v instanceof List) {
                        String key = "L_arg_" + arg.getClass().getName() + "#" + f.getName();
                        if (sDiagSeen.add(key))
                            Log.i(TAG, "[CF] L1 list via field=" + f.getName()
                                    + " in " + arg.getClass().getName());
                        return (List<Object>) v;
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // =========================================================================
    // Reflection helpers
    // =========================================================================

    static Object getFieldSafe(Object obj, String name) {
        try {
            Field f = findFieldRecursive(obj.getClass(), name);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) { return null; }
    }

    static String getStrField(Object obj, String name) {
        Object v = getFieldSafe(obj, name);
        return (v instanceof String) ? (String) v : null;
    }

    static String callStrMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            Object r = m.invoke(obj);
            return (r instanceof String) ? (String) r : null;
        } catch (Throwable ignored) { return null; }
    }

    static Object callMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            return m.invoke(obj);
        } catch (Throwable ignored) { return null; }
    }

    static Field findFieldRecursive(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try { return cls.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Hook all methods named `name` with `paramCount` params on cls and its parents.
     * Returns number of methods hooked.
     */
    private static int hookMethodsByName(Class<?> cls, String name, int paramCount, XC_MethodHook hook) {
        int count = 0;
        Set<String> seen = new HashSet<String>();
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!name.equals(m.getName())) continue;
                if (m.getParameterTypes().length != paramCount) continue;
                String sig = m.toGenericString();
                if (seen.add(sig)) {
                    XposedBridge.hookMethod(m, hook);
                    count++;
                }
            }
            cur = cur.getSuperclass();
        }
        return count;
    }

    static boolean isWxid(String s) {
        if (s == null || s.length() < 4 || s.contains(" ")) return false;
        return s.startsWith("wxid_") || s.startsWith("gh_")
                || s.endsWith("@chatroom")              // 密群 ID
                || "weixin".equals(s) || "filehelper".equals(s)
                || "notifymessage".equals(s) || "floatbottle".equals(s)
                || s.startsWith("qqmail_") || s.startsWith("newsapp");
    }
}
