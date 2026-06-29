package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.BuildConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.GuardRuntime;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.RegistryFallback;
import com.ghost.assist.core.StateMachine;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Contact list (通讯录 tab) filter — v4 (8.0.71 static-analysis verified 2026-05-20)
 *
 * Data chain (8.0.71):
 *   Adapter:   ik3.t0  (WxRecyclerAdapter)
 *   LiveList:  com.tencent.mm.ui.contact.address.AddressLiveList (extends MvvmList<fc5.g>)
 *   Item:      fc5.g   — DEX field "d" → z3, DEX field "e" → type (2=contact, 1=header)
 *   wxid:      z3.c1() → field_username
 *   List fld:  MvvmList 基类 o/p/h (ArrayList<fc5.g>)
 *
 * Hook layers:
 *   L4:   ik3.t0.notifyDataSetChanged — clean-before gate
 *   INIT: AddressLiveList constructor  — warm-attach (1 s delay, data loaded async)
 */
public class ContactFilter {

    static final String TAG = "NCL";

    // P1E Step2: class-name anchors sourced from the encrypted SO registry
    // (contact.address) via GuardRuntime.getRecipe(). C5a 归一: the DEBUG fallback
    // is no longer hand-written here — it comes from RegistryFallback, generated
    // at build time from native_core/registry_8071.json (debug = literal, release
    // = ""). resolveRecipes() (called first thing in install()) overrides each
    // when the registry returns a non-empty value; on scatter / SO-unavailable it
    // keeps the generated debug fallback → behaviour unchanged. NON-FINAL on
    // purpose so the resolved value can replace the fallback. ADDR_ITEM_CLS stays
    // package-visible because ContactHotReload / ContactDiscoveryHook read it.
    private static String ADDR_ADAPTER    = RegistryFallback.CONTACT_ADDRESS__ADAPTER_CLASS;
    private static String ADDR_LIVE_LIST  = RegistryFallback.CONTACT_ADDRESS__LIVE_LIST;
    private static String MVVMLIST_CLASS  = RegistryFallback.CONTACT_ADDRESS__MVVMLIST_CLASS;
    // AddressLiveList 的 MvvmList 基类真实 backing 字段 = o/p/h（与会话 tab MvvmConvList 同），元素 fc5.g（P_CV1 2026-05-29 L1 实证）。
    // 注：o/p/h 不在 registry，本轮保持硬编码（P1E Step2 范围外）。
    static final String[] MVVMLIST_FIELDS = {"o", "p", "h"};
    static String ADDR_ITEM_CLS   = RegistryFallback.CONTACT_ADDRESS__ITEM_CLASS;
    private static String ADDR_Z3_CLS     = RegistryFallback.CONTACT_ADDRESS__CONTACT_CLASS;
    // z3 wxid getter method name (was inline "c1"); now registry-sourced w/ fallback.
    private static String WXID_GETTER     = RegistryFallback.CONTACT_ADDRESS__WXID_GETTER;

    private static volatile boolean sRecipesResolved = false;

    /** Resolve one recipe field from registry contact.address; release+PROD has no fallback. */
    private static String recipe(String key, String fallback) {
        return GuardRuntime.getRecipeOrFallback("contact.address", key, fallback);
    }

    /**
     * P1E Step2: pull the contact.address anchors from the encrypted registry,
     * falling back to the embedded literals when the registry is unavailable /
     * scattered. Idempotent; called once at install() before any hook fires.
     */
    private static boolean resolveRecipes() {
        if (sRecipesResolved) return true;
        ADDR_ADAPTER   = recipe("adapter_class", ADDR_ADAPTER);
        ADDR_LIVE_LIST = recipe("live_list", ADDR_LIVE_LIST);
        MVVMLIST_CLASS = recipe("mvvmlist_class", MVVMLIST_CLASS);
        ADDR_ITEM_CLS  = recipe("item_class", ADDR_ITEM_CLS);
        ADDR_Z3_CLS    = recipe("contact_class", ADDR_Z3_CLS);
        WXID_GETTER    = recipe("wxid_getter", WXID_GETTER);
        ITEM_CONTACT_FIELD = recipe("contact_field", ITEM_CONTACT_FIELD);
        ITEM_TYPE_FIELD    = recipe("type_field", ITEM_TYPE_FIELD);
        sRecipesResolved = true;
        // fallback self-proof: an unknown key must return the supplied fallback
        // (proves the registry-miss path keeps old behaviour, no regression).
        boolean ready = !ADDR_ADAPTER.isEmpty()
                && !ADDR_LIVE_LIST.isEmpty()
                && !MVVMLIST_CLASS.isEmpty()
                && !ADDR_ITEM_CLS.isEmpty()
                && !ADDR_Z3_CLS.isEmpty()
                && !WXID_GETTER.isEmpty();
        boolean fbOk = BuildConfig.DEBUG && "ik3.t0".equals(recipe("__no_such_key__", "ik3.t0"));
        Log.i(TAG, "[CTF] recipes adapter=" + ADDR_ADAPTER + " item=" + ADDR_ITEM_CLS
                + " contact=" + ADDR_Z3_CLS + " getter=" + WXID_GETTER
                + " live=" + ADDR_LIVE_LIST + " fallbackSelfTest=" + (fbOk ? "ok" : "FAIL")
                + " ready=" + ready);
        return ready;
    }

    // DEX field names (JADX prefix stripped):  f238409d → "d",  f238410e → "e"
    // C7-接6 归一: 从 registry contact.address 取，fallback 走 RegistryFallback（debug=值/release=""）。NON-FINAL 供 resolveRecipes 覆盖。
    private static String ITEM_CONTACT_FIELD = RegistryFallback.CONTACT_ADDRESS__CONTACT_FIELD; // → z3
    private static String ITEM_TYPE_FIELD    = RegistryFallback.CONTACT_ADDRESS__TYPE_FIELD;    // int, 2 = contact

    // Cached reflection refs
    private static volatile Method  sZ3C1      = null;
    private static volatile Field   sItemD     = null;
    private static volatile Field   sItemE     = null;

    // ===================================================================
    // V3: backing list captured directly from addAll hook (no fragment lifecycle needed)

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        // P1E Step2: resolve class-name anchors from registry (fallback = literals)
        // BEFORE any hook installs, so ContactHotReload / ContactDiscoveryHook see
        // the resolved ADDR_ITEM_CLS too.
        if (!resolveRecipes()) {
            Log.w(TAG, "[CTF] skip install: registry not ready");
            return;
        }
        // 密友（主通讯录）热切的真正驱动 = installAddAllHook（H 态过滤 + cache）
        //   + ContactDiscoveryHook（扫 live AddressLiveList/ik3.t0 → 写 sLiveListRef/sAdapterRef）
        //   + RefreshBus → ContactHotReload（H 清 / V 注 o/p/h）。
        // V0-V4 那一波诊断探针（fragMethodProbe / rvSetAdapterProbe / warmAttach）与影子类死 hook
        //   （adapterCtorHook / adapterHook / fragResumeHook / tabFragmentHook，lpparam classloader
        //   分裂导致永不触发）已于 2026-05-29 收口时清除。
        installAddAllHook();           // 8.0.71 通讯录入口：ArrayList.addAll(fc5.g×30)
        // 群聊页隐藏：classloader 分裂下不能 lpparam.loadClass(s0) 后 hook（影子类零命中），
        // 改由 ContactDiscoveryHook 从 live adapter 实例回调 hookGroupAdapterFromLive() 装 hook。

        // V↔H 热切：H 态清理 + V 态注回（P_CV1，对标 ConvHotReload v28）
        ContactHotReload.install(lpparam);

        // Hot-reload: state listener (registration log) + RefreshBus callback.
        StateMachine.getInstance().addListener("ContactFilter",
                (oldState, newState) -> { /* log only — RefreshBus driven by StateMachine */ });
        RefreshBus.getInstance().register("ContactFilter", hidden -> {
            StateMachine.State state = StateMachine.getInstance().getState();
            if (state == StateMachine.State.HIDDEN) {
                ContactHotReload.handleBusHidden();
            } else if (state == StateMachine.State.VISIBLE) {
                ContactHotReload.handleBusVisible();
            } else {
                Log.i(TAG, "[BUS] ContactFilter skipped state=" + state + " hidden=" + hidden);
            }
        });

        Log.i(TAG, "[CTF] ContactFilter installed");
    }

    // ------------------------------------------------------------------
    // Adapter notify helper — 给 ContactHotReload 用，封装 ik3.t0.notifyDataSetChanged
    // ------------------------------------------------------------------
    static void notifyContactAdapter(String tag) {
        Object adapter = sAdapterRef != null ? sAdapterRef.get() : null;
        if (adapter == null) {
            Log.i(TAG, "[CTHR:notify:" + tag + "] no-adapter; forceNotify");
            // sAdapterRef was GC'd — recover via sAdapterHostRef (View) or current-activity scan.
            ContactDiscoveryHook.forceNotify(tag);
            return;
        }
        try {
            adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
            Log.i(TAG, "[CTHR:notify:" + tag + "] done");
        } catch (Throwable t) {
            Log.w(TAG, "[CTHR:notify:" + tag + "] err: " + t);
        }
    }

    // ------------------------------------------------------------------
    // P_CV1-G 群聊隐藏已抽到 ContactGroupHide.java（2026-05-29 模块拆分）。
    //   ChatroomContactUI 是独立 cursor adapter，与主通讯录两套机制；
    //   由 ContactDiscoveryHook 从 live s0 实例回调 ContactGroupHide.hookGroupAdapterFromLive()。
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 8.0.71 通讯录数据入口（L1）：ArrayList.addAll(Collection)
    // fc5.g × ~30 条分段虚拟滚动，每段 beforeHook 过滤
    // ------------------------------------------------------------------
    private static void installAddAllHook() {
        try {
            XposedBridge.hookMethod(
                    ArrayList.class.getMethod("addAll", java.util.Collection.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            java.util.Collection coll = (java.util.Collection) param.args[0];
                            if (coll == null || coll.isEmpty()) return;
                            Object first = coll.iterator().next();
                            if (first == null) return;
                            String cn = first.getClass().getName();
                            if (!ADDR_ITEM_CLS.equals(cn)) return; // 只处理 fc5.g

                            // addAll(fc5.g) 的 thisObject 是分段临时 list（非 RV 持久 backing）；
                            // 仅在 sBackingListRef 还没被 CDH 发现填上时作为 fallback 记一下。
                            if (sBackingListRef == null || sBackingListRef.get() == null) {
                                @SuppressWarnings("unchecked")
                                java.util.ArrayList<Object> _bl =
                                        (java.util.ArrayList<Object>) param.thisObject;
                                sBackingListRef = new WeakReference<>(_bl);
                            }

                            // P_CV1 V1.2：fc5.g 首次触达 = 通讯录数据真实加载信号
                            // 通知 CDH 200ms 后扫前台 Activity 抓 RecyclerView/ListView（不影响 H 态过滤主流程）
                            try { ContactDiscoveryHook.scheduleScanFromAddAll(); } catch (Throwable ignored) {}

                            if (!StateMachine.getInstance().isActive()) return;
                            Set<String> hidden = Bridge.getInstance().allHiddenIds();
                            if (hidden.isEmpty()) return;

                            int before = coll.size();
                            java.util.Iterator it = coll.iterator();
                            int removed = 0;
                            int idx = 0;
                            while (it.hasNext()) {
                                Object item = it.next();
                                if (item == null) { idx++; continue; }
                                if (!isContactItem(item)) { idx++; continue; } // 跳过分组头
                                String wxid = extractWxid(item);
                                if (wxid == null) { idx++; continue; }
                                if (hidden.contains(wxid)) {
                                    try {
                                        ContactHotReload.putCache(wxid, item, null, idx);
                                        it.remove();
                                        removed++;
                                        // idx not incremented: next slides in
                                    } catch (UnsupportedOperationException ignored) {
                                        idx++;
                                    }
                                } else {
                                    idx++;
                                }
                            }
                            if (removed > 0) {
                                Log.i(TAG, "[CTF:addAll] removed=" + removed + "/" + before);
                                Bridge.getInstance().addRawFeedLine(
                                        "[CTF:addAll] fc5.g removed=" + removed + "/" + before);
                            }
                        }
                    });
            Log.i(TAG, "[CTF] ArrayList.addAll(fc5.g) hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[CTF] addAll hook fail: " + t);
        }
    }

    // ------------------------------------------------------------------
    // 补线：onResume / onHiddenChanged → 找 AddressLiveList 实例 → 强制清理
    // 使用 getMethods()（含继承），只 hook 生命周期方法
    // ------------------------------------------------------------------
    static volatile Object sLiveListRef = null; // 缓存最近见到的 AddressLiveList
    static volatile WeakReference<Object> sAdapterRef; // 通讯录 Adapter 弱引用
    /** V3: backing ArrayList captured directly from addAll hook. */
    @SuppressWarnings("unchecked")
    static volatile WeakReference<java.util.ArrayList<Object>> sBackingListRef = null;

    /**
     * P_CV1 V2.0（2026-05-28，jadx 验证）：8.0.71 真实 tab fragment 生命周期 hook。
     *
     * 背景：MvvmAddressUIFragment extends BaseAddressUIFragment extends
     *      AbstractTabChildActivity.AbStractTabFragment（微信自定义 tab fragment）。
     * 不是标准 androidx Fragment，所以 onResume/onHiddenChanged 永远不点火。
     * 微信自定义 lifecycle：q0(Bundle)=onTabCreate / t0()=onResume / r0()=onDestroy / s0()=onPause
     *
     * jadx 已验：MvvmAddressUIFragment.F0() 直接返回 AddressLiveList；
     *           f188251p 字段是 WxRecyclerView 实例（line 96）；
     *           q0() 内 setAdapter(E0()) 给 WxRecyclerView 装 adapter（line 366）
     *
     * 本 hook 在 q0(Bundle) afterHook 时反射调 F0() 拿 AddressLiveList → 写 sLiveListRef；
     *           反射读 WxRecyclerView 字段 → getAdapter() → 写 sAdapterRef。
     * 在 t0() afterHook 时复用同样逻辑（确保切 tab 回来时引用仍新鲜）。
     */
    // ------------------------------------------------------------------
    // Core clean: 过滤 AddressLiveList 的 o/p/h（密友主通讯录 H 态隐藏）
    // ------------------------------------------------------------------

    static void cleanLiveList(Object liveList, String tag) {
        boolean filterOn = StateMachine.getInstance().isActive();
        Log.i(TAG, "[CTF:" + tag + "] cleanLiveList on=" + filterOn);
        if (!filterOn) return;
        // P_CV1（2026-05-29 L1 实证）：AddressLiveList 真 backing = MvvmList 基类 o/p/h。
        // 逐字段遍历 o/p/h，对每个装 fc5.g 的 List 跑过滤，与 restoreToLiveList 对称。
        int totalRemoved = 0;
        for (String fn : MVVMLIST_FIELDS) {
            try {
                Field f = findFieldInHierarchy(liveList.getClass(), fn);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(liveList);
                if (!(arr instanceof java.util.List)) continue;
                java.util.List<?> items = (java.util.List<?>) arr;
                if (items.isEmpty()) continue;
                Object first = items.get(0);
                if (first == null || !ADDR_ITEM_CLS.equals(first.getClass().getName())) continue;
                int sz = items.size();
                int removed = filterContactList(items, tag + ":" + fn);
                if (removed > 0) {
                    Log.i(TAG, "[CTF:" + tag + "] field=" + fn + " filtered=" + removed + "/" + sz);
                    totalRemoved += removed;
                }
            } catch (Throwable e) {
                Log.w(TAG, "[CTF] cleanLiveList field=" + fn + " err: " + e);
            }
        }
        Log.i(TAG, "[CTF:" + tag + "] cleanLiveList totalRemoved=" + totalRemoved);
        if (totalRemoved > 0) Bridge.getInstance().addRawFeedLine("[CTF:" + tag + "] filtered=" + totalRemoved);
    }

    /**
     * V3: 直接对 backing ArrayList 做过滤（BUS-H 时 sLiveListRef 仍为 null 的兜底）。
     * 与 cleanLiveList 等价，但直接对传入的 backing List 过滤（无需反射 o/p/h）。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void cleanBackingList(java.util.List backing, String tag) {
        if (backing == null || backing.isEmpty()) return;
        boolean filterOn = StateMachine.getInstance().isActive();
        Log.i(TAG, "[CTF:" + tag + "] cleanBackingList on=" + filterOn + " sz=" + backing.size());
        if (!filterOn) return;
        int removed = filterContactList(backing, tag);
        Log.i(TAG, "[CTF:" + tag + "] filtered=" + removed);
        if (removed > 0) Bridge.getInstance().addRawFeedLine("[CTF:" + tag + "] filtered=" + removed);
    }

    // ------------------------------------------------------------------
    // Filter logic
    // ------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int filterContactList(java.util.List items, String tag) {
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        if (hidden.isEmpty()) return 0;

        int removed = 0;
        int idx = 0;
        Iterator it = items.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) { idx++; continue; }
            if (!isContactItem(item)) { idx++; continue; }
            String wxid = extractWxid(item);
            if (wxid == null) { idx++; continue; }
            if (hidden.contains(wxid)) {
                ContactHotReload.putCache(wxid, item, null, idx);
                it.remove();
                removed++;
                Log.d(TAG, "[CTF:" + tag + "] removed id=" + wxid + " idx=" + idx);
                // idx not incremented: next item slides into same slot
            } else {
                idx++;
            }
        }
        return removed;
    }

    /** fc5.g.e == 2 → real contact (not section header) */
    private static boolean isContactItem(Object item) {
        try {
            if (sItemE == null) {
                Field f = item.getClass().getDeclaredField(ITEM_TYPE_FIELD);
                f.setAccessible(true);
                sItemE = f;
            }
            Object val = sItemE.get(item);
            return val != null && ((Integer) val) == 2;
        } catch (Throwable e) { return false; }
    }

    /** fc5.g.d → z3 → z3.c1() → wxid（或 *@chatroom，经 isWxid 短路放行） */
    static String extractWxid(Object item) {
        try {
            if (sItemD == null) {
                Field f = item.getClass().getDeclaredField(ITEM_CONTACT_FIELD);
                f.setAccessible(true);
                sItemD = f;
            }
            Object z3 = sItemD.get(item);
            if (z3 == null) return null;
            return callC1(z3);
        } catch (Throwable e) { return null; }
    }

    /** z3.c1() → field_username = wxid */
    private static String callC1(Object z3) {
        try {
            if (sZ3C1 == null) {
                Method m = z3.getClass().getMethod(WXID_GETTER);
                sZ3C1 = m;
            }
            Object res = sZ3C1.invoke(z3);
            if (res == null) return null;
            String s = res.toString();
            return isWxid(s) ? s : null;
        } catch (Throwable e) {
            // fallback: scan all 0-param String methods
            return scanForWxid(z3);
        }
    }

    private static String scanForWxid(Object obj) {
        try {
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (!m.getReturnType().getName().equals("java.lang.String")) continue;
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(obj);
                    String s = r != null ? r.toString() : null;
                    if (isWxid(s)) return s;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static Field findFieldInHierarchy(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && !c.getName().equals("java.lang.Object")) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    static boolean isWxid(String s) {
        if (s == null || s.length() < 4 || s.contains(" ")) return false;
        if (Bridge.isGroupId(s)) return true; // 密群 *@chatroom 也作为 hidden id 候选放行（P_CV1 补丁-2）
        return s.startsWith("wxid_") || s.startsWith("gh_")
                || "weixin".equals(s) || "filehelper".equals(s)
                || s.startsWith("qqmail_") || s.startsWith("newsapp");
    }
}
