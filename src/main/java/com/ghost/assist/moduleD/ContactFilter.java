package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.RefreshBus;
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
 *   List fld:  MvvmList.f135087o (ArrayList<fc5.g>)
 *
 * Hook layers:
 *   L4:   ik3.t0.notifyDataSetChanged — clean-before gate
 *   INIT: AddressLiveList constructor  — warm-attach (1 s delay, data loaded async)
 */
public class ContactFilter {

    private static final String TAG = "NCL";

    private static final String ADDR_ADAPTER    = "ik3.t0";
    private static final String ADDR_LIVE_LIST  = "com.tencent.mm.ui.contact.address.AddressLiveList";
    private static final String MVVMLIST_CLASS  = "com.tencent.mm.plugin.mvvmlist.MvvmList";
    private static final String MVVMLIST_DATA   = "f135087o"; // ArrayList<T> in MvvmList
    private static final String ADDR_ITEM_CLS   = "fc5.g";
    private static final String ADDR_Z3_CLS     = "com.tencent.mm.storage.z3";

    // DEX field names (JADX prefix stripped):  f238409d → "d",  f238410e → "e"
    private static final String ITEM_CONTACT_FIELD = "d"; // → z3
    private static final String ITEM_TYPE_FIELD    = "e"; // int, 2 = contact

    // Cached reflection refs
    private static volatile Method  sZ3C1      = null;
    private static volatile Field   sItemD     = null;
    private static volatile Field   sItemE     = null;
    private static volatile Field   sMvvmDataF = null;

    // ===================================================================

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installAddAllHook();           // 8.0.71 通讯录入口：ArrayList.addAll(fc5.g×30)
        installAdapterHook(lpparam);   // 兜底：notifyDataSetChanged clean-before
        installFragResumeHook(lpparam); // 兜底：onResume/onHiddenChanged

        // Hot-reload: state listener (registration log) + RefreshBus callback.
        StateMachine.getInstance().addListener("ContactFilter",
                (oldState, newState) -> { /* log only — RefreshBus driven by StateMachine */ });
        RefreshBus.getInstance().register("ContactFilter", hidden -> {
            Object liveList = sLiveListRef;
            if (liveList == null) {
                Log.i(TAG, "[BUS] refresh ContactFilter skipped no-livelist");
                return;
            }
            // In HIDDEN: cleanLiveList removes items from MvvmList internal array.
            cleanLiveList(liveList, "bus");
            // Notify adapter to re-render.
            Object adapter = sAdapterRef != null ? sAdapterRef.get() : null;
            if (adapter != null) {
                try {
                    adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
                    Log.i(TAG, "[BUS] refresh ContactFilter done hidden=" + hidden);
                } catch (Throwable t) {
                    Log.w(TAG, "[BUS] refresh ContactFilter notify err: " + t);
                }
            } else {
                Log.i(TAG, "[BUS] refresh ContactFilter no-adapter (livelist cleaned)");
            }
        });

        Log.i(TAG, "[CTF] ContactFilter installed");
    }

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

                            if (!StateMachine.getInstance().isActive()) return;
                            Set<String> hidden = Bridge.getInstance().allHiddenIds();
                            if (hidden.isEmpty()) return;

                            int before = coll.size();
                            java.util.Iterator it = coll.iterator();
                            int removed = 0;
                            while (it.hasNext()) {
                                Object item = it.next();
                                if (item == null) continue;
                                if (!isContactItem(item)) continue; // 跳过分组头
                                String wxid = extractWxid(item);
                                if (wxid != null && hidden.contains(wxid)) {
                                    try { it.remove(); removed++; } catch (UnsupportedOperationException ignored) {}
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
    // 主线：hook MvvmList.n(List, boolean) — 与 ConvFilter 一致（可能不走，保留）
    // AddressLiveList.isInstance(this) 区分通讯录 vs 会话
    // ------------------------------------------------------------------
    private static void installMvvmNHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> mvvmCls  = lpparam.classLoader.loadClass(MVVMLIST_CLASS);
            final Class<?> addrCls = lpparam.classLoader.loadClass(ADDR_LIVE_LIST);

            int hooked = 0;
            for (Method m : mvvmCls.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2) continue;
                if (!java.util.List.class.isAssignableFrom(pt[0])) continue;
                if (pt[1] != boolean.class) continue;
                final String mName = m.getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!addrCls.isInstance(param.thisObject)) return;
                        java.util.List list = (java.util.List) param.args[0];
                        if (list == null || list.isEmpty()) return;
                        int before = list.size();
                        int removed = filterContactList(
                                list instanceof java.util.ArrayList
                                        ? (java.util.ArrayList) list
                                        : new java.util.ArrayList(list),
                                "MvvmN." + mName);
                        if (removed > 0)
                            Log.i(TAG, "[CTF:MvvmN] " + mName + " removed=" + removed + "/" + before);
                    }
                });
                Log.i(TAG, "[CTF] MvvmList." + mName + "(List,bool) hooked for AddressLiveList");
                hooked++;
            }
            if (hooked == 0) Log.w(TAG, "[CTF] MvvmList: no n(List,bool) method found");
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] MvvmNHook fail: " + e);
        }
    }

    // ------------------------------------------------------------------
    // 补线：onResume / onHiddenChanged → 找 AddressLiveList 实例 → 强制清理
    // 使用 getMethods()（含继承），只 hook 生命周期方法
    // ------------------------------------------------------------------
    private static volatile Object sLiveListRef = null; // 缓存最近见到的 AddressLiveList
    private static volatile WeakReference<Object> sAdapterRef; // 通讯录 Adapter 弱引用

    private static void installFragResumeHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> fragCls  = lpparam.classLoader.loadClass(
                    "com.tencent.mm.ui.contact.address.MvvmAddressUIFragment");
            final Class<?> addrCls  = lpparam.classLoader.loadClass(ADDR_LIVE_LIST);
            final java.util.Set<String> targetMethods = new java.util.HashSet<>(
                    java.util.Arrays.asList("onResume", "onHiddenChanged", "onStart", "onViewCreated"));

            int hooked = 0;
            for (Method m : fragCls.getMethods()) {  // getMethods() 含继承
                if (!targetMethods.contains(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "[CTF:frag] " + m.getName() + " fired");
                        // 尝试从 Fragment 里找 AddressLiveList 字段
                        Object liveList = findFieldByType(param.thisObject, addrCls);
                        if (liveList != null) {
                            sLiveListRef = liveList;
                            cleanLiveList(liveList, "frag." + m.getName());
                        } else if (sLiveListRef != null) {
                            cleanLiveList(sLiveListRef, "frag.cached");
                        }
                    }
                });
                hooked++;
            }
            Log.i(TAG, "[CTF] fragResume hooked " + hooked + " lifecycle methods");
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] fragResume fail: " + e);
        }
    }

    /** 在 obj 的字段里找第一个 targetClass 类型的实例（含父类字段） */
    private static Object findFieldByType(Object obj, Class<?> targetClass) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && !cls.equals(Object.class)) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null && targetClass.isInstance(v)) return v;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Probe: hook EVERY method on MvvmAddressUIFragment — find what fires on tab switch
    // ------------------------------------------------------------------
    private static void installFragMethodProbe(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> fragCls = lpparam.classLoader.loadClass(
                    "com.tencent.mm.ui.contact.address.MvvmAddressUIFragment");
            int cnt = 0;
            for (java.lang.reflect.Method m : fragCls.getDeclaredMethods()) {
                if (m.getParameterTypes().length > 2) continue; // skip complex methods
                final String mName = m.getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sSeenAdapters.add("frag:" + mName)) {
                            Log.i(TAG, "[CTF:fragMethod] " + mName + "()");
                        }
                    }
                });
                cnt++;
            }
            Log.i(TAG, "[CTF] fragMethod probe: " + cnt + " methods hooked");
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] fragMethod probe fail: " + e);
        }
    }

    // ------------------------------------------------------------------
    // Probe: hook RecyclerView.setAdapter globally — find contacts adapter
    // ------------------------------------------------------------------
    private static final java.util.Set<String> sSeenAdapters =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void installRvSetAdapterProbe(XC_LoadPackage.LoadPackageParam lpparam) {
        // androidx.recyclerview.widget.RecyclerView may be obfuscated.
        // Try both stable name and known obfuscated short names in the package.
        String[] candidates = {
            "androidx.recyclerview.widget.RecyclerView",
            "com.tencent.mm.view.recyclerview.WxRecyclerView",
            "androidx.recyclerview.widget.e2",
            "androidx.recyclerview.widget.d2",
            "androidx.recyclerview.widget.c2",
            "androidx.recyclerview.widget.g2",
        };
        XC_MethodHook probe = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object adapter = param.args[0];
                if (adapter == null) return;
                String cn = adapter.getClass().getName();
                if (sSeenAdapters.add(cn)) {
                    Log.i(TAG, "[CTF:rv] setAdapter cls=" + cn);
                }
            }
        };
        for (String candidate : candidates) {
            try {
                Class<?> rvCls = lpparam.classLoader.loadClass(candidate);
                for (java.lang.reflect.Method m : rvCls.getDeclaredMethods()) {
                    if (!"setAdapter".equals(m.getName())) continue;
                    if (m.getParameterTypes().length != 1) continue;
                    XposedBridge.hookMethod(m, probe);
                    Log.i(TAG, "[CTF] RV.setAdapter hooked on " + candidate);
                    break;
                }
            } catch (ClassNotFoundException ignore) {
            } catch (Throwable e) {
                Log.w(TAG, "[CTF] RV probe fail " + candidate + ": " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    // L4: ik3.t0.notifyDataSetChanged — inherited from RecyclerView.Adapter
    //     Hook the parent class method and filter by instanceof
    // ------------------------------------------------------------------

    private static void installAdapterHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Load ik3.t0 to ensure it's in the classloader
            final Class<?> adapterCls = lpparam.classLoader.loadClass(ADDR_ADAPTER);

            // Walk hierarchy to find the declaring class of notifyDataSetChanged
            Method nds = null;
            Class<?> cur = adapterCls;
            while (cur != null && !cur.getName().equals("java.lang.Object")) {
                try { nds = cur.getDeclaredMethod("notifyDataSetChanged"); break; }
                catch (NoSuchMethodException ignore) { cur = cur.getSuperclass(); }
            }
            if (nds == null) {
                Log.w(TAG, "[CTF] L4: notifyDataSetChanged not found in hierarchy");
                return;
            }

            final Method finalNds = nds;
            XposedBridge.hookMethod(nds, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!adapterCls.isInstance(param.thisObject)) return;
                    // Update weak ref so RefreshBus can drive hot-reload.
                    sAdapterRef = new WeakReference<>(param.thisObject);
                    Log.i(TAG, "[CTF:L4fire] cls=" + param.thisObject.getClass().getName());
                    cleanAdapter(param.thisObject);
                }
            });
            Log.i(TAG, "[CTF] L4 hooked: notifyDataSetChanged on "
                    + finalNds.getDeclaringClass().getName() + " (filter=" + ADDR_ADAPTER + ")");
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] L4 hook fail: " + e);
        }
    }

    // ------------------------------------------------------------------
    // INIT: hook MvvmList base constructor — catches ALL subclasses incl. AddressLiveList
    // ------------------------------------------------------------------

    private static void installWarmAttach(XC_LoadPackage.LoadPackageParam lpparam) {
        int hooked = 0;

        // 1. AddressLiveList directly
        try {
            Class<?> allCls = lpparam.classLoader.loadClass(ADDR_LIVE_LIST);
            for (Constructor<?> ctor : allCls.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) {
                        Log.i(TAG, "[CTF:ctor] AddressLiveList constructed");
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override public void run() {
                                cleanLiveList(param.thisObject, "INIT");
                            }
                        }, 1000);
                    }
                });
                hooked++;
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] AddressLiveList ctor hook fail: " + e);
        }

        // 2. MvvmList base — catches any subclass including contacts lists
        try {
            Class<?> mvvmCls = lpparam.classLoader.loadClass(MVVMLIST_CLASS);
            for (Constructor<?> ctor : mvvmCls.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) {
                        final String subCls = param.thisObject.getClass().getName();
                        // Log ALL MvvmList subclass instantiations (diagnostic)
                        if (sSeenAdapters.add("mvvm:" + subCls)) {
                            Log.i(TAG, "[CTF:mvvmCtor] subCls=" + subCls);
                        }
                        if (!subCls.contains("Address") && !subCls.contains("address")
                                && !subCls.equals(ADDR_LIVE_LIST)) {
                            return;
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override public void run() {
                                cleanLiveList(param.thisObject, "INIT");
                            }
                        }, 1000);
                    }
                });
                hooked++;
            }
            Log.i(TAG, "[CTF] MvvmList base ctors hooked: " + hooked);
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] MvvmList ctor hook fail: " + e);
        }

        Log.i(TAG, "[CTF] INIT warm-attach hooked: " + hooked + " ctors");
    }

    // ------------------------------------------------------------------
    // Clean from adapter — find AddressLiveList via field scan
    // ------------------------------------------------------------------

    private static void cleanAdapter(Object adapter) {
        try {
            Object liveList = findAddressLiveList(adapter);
            if (liveList != null) {
                cleanLiveList(liveList, "L4");
            } else {
                Log.w(TAG, "[CTF] L4: AddressLiveList not found on adapter");
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] cleanAdapter err: " + e);
        }
    }

    /**
     * Walk adapter (and its parent) fields to find an AddressLiveList instance.
     * The WxRecyclerAdapter stores a reference to the MvvmList it drives.
     */
    private static Object findAddressLiveList(Object adapter) {
        Class<?> cls = adapter.getClass();
        while (cls != null && !cls.getName().equals("java.lang.Object")) {
            for (Field f : cls.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(adapter);
                    if (val == null) continue;
                    String vCls = val.getClass().getName();
                    if (ADDR_LIVE_LIST.equals(vCls)) return val;
                    // also accept any MvvmList subclass that carries fc5.g items
                    if (isMvvmListSubtype(val.getClass())) {
                        java.util.List<?> data = getMvvmData(val);
                        if (data != null && !data.isEmpty()
                                && ADDR_ITEM_CLS.equals(data.get(0).getClass().getName())) {
                            return val;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean isMvvmListSubtype(Class<?> cls) {
        Class<?> c = cls;
        while (c != null && !c.getName().equals("java.lang.Object")) {
            if (MVVMLIST_CLASS.equals(c.getName())) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Core clean: filter f135087o of an AddressLiveList
    // ------------------------------------------------------------------

    private static void cleanLiveList(Object liveList, String tag) {
        boolean filterOn = StateMachine.getInstance().isActive();
        Log.i(TAG, "[CTF:" + tag + "] cleanLiveList on=" + filterOn);
        if (!filterOn) return;
        try {
            java.util.List<?> items = getMvvmData(liveList);
            int sz = items != null ? items.size() : -1;
            Log.i(TAG, "[CTF:" + tag + "] sz=" + sz);
            if (items == null || items.isEmpty()) return;
            int removed = filterContactList(items, tag);
            Log.i(TAG, "[CTF:" + tag + "] filtered=" + removed + "/" + sz);
            if (removed > 0) Bridge.getInstance().addRawFeedLine("[CTF:" + tag + "] filtered=" + removed);
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] cleanLiveList err: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<?> getMvvmData(Object liveList) {
        try {
            if (sMvvmDataF == null) {
                Field f = findFieldInHierarchy(liveList.getClass(), MVVMLIST_DATA);
                if (f != null) { f.setAccessible(true); sMvvmDataF = f; }
            }
            if (sMvvmDataF != null) return (ArrayList<?>) sMvvmDataF.get(liveList);
        } catch (Throwable e) {
            Log.w(TAG, "[CTF] getMvvmData err: " + e);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Filter logic
    // ------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int filterContactList(java.util.List items, String tag) {
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        if (hidden.isEmpty()) return 0;

        int removed = 0;
        Iterator it = items.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) continue;
            if (!isContactItem(item)) continue; // skip headers
            String wxid = extractWxid(item);
            if (wxid == null) continue;
            if (hidden.contains(wxid)) {
                it.remove();
                removed++;
                Log.d(TAG, "[CTF:" + tag + "] removed wxid=" + wxid);
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

    /** fc5.g.d → z3 → z3.c1() → wxid */
    private static String extractWxid(Object item) {
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
                Method m = z3.getClass().getMethod("c1");
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

    private static Field findFieldInHierarchy(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && !c.getName().equals("java.lang.Object")) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    static boolean isWxid(String s) {
        if (s == null || s.length() < 4 || s.contains(" ")) return false;
        return s.startsWith("wxid_") || s.startsWith("gh_")
                || "weixin".equals(s) || "filehelper".equals(s)
                || s.startsWith("qqmail_") || s.startsWith("newsapp");
    }
}
