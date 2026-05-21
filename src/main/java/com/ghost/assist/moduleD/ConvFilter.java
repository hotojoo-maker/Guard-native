package com.ghost.assist.moduleD;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.DebugTelemetry;

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

    private static final String TAG = "NCL";

    private static final String MVVMLIST_CLASS      = "com.tencent.mm.plugin.mvvmlist.MvvmList";
    // 8.0.71: MvvmList subclass for conversation list (static analysis confirmed)
    private static final String MVVMCONV_CLASS      = "com.tencent.mm.ui.conversation.adapter.MvvmConvList";
    private static final String CONV_LIST_VIEW      = "com.tencent.mm.ui.conversation.ConversationListView";
    private static final String ADAPTER_CLASS_71    = "kc5.v0";   // confirmed 8.0.71
    private static final String ADAPTER_CLASS_66    = "f45.s0";

    // MvvmList internal ArrayList field names
    private static final String[] MVVMLIST_ARRAY_FIELDS = {"o", "p", "h"};
    // Contact field on conversation item (8.0.66: item.d = m3 contact obj)
    private static final String[] CONTACT_FIELD_NAMES = {"d", "e", "f", "a", "b", "c"};
    // Methods on contact obj that return wxid (8.0.71: C0(); 8.0.66: j1())
    // h1 = 8.0.71 l4.h1() → field_username (wxid)  C0()=field_digestUser ≠ wxid
    private static final String[] WXID_GETTER_NAMES = {"h1", "j1", "i1", "k1", "getUsername", "getUserName"};
    // Fields on contact obj that hold wxid directly
    private static final String[] WXID_FIELD_NAMES = {"field_userName", "username", "d", "e"};
    private static final String UNREAD_FIELD = "field_unReadCount";
    // Adapter fields that may hold MvvmList — f286278p confirmed 8.0.71 kc5.v0
    private static final String[] MVVMLIST_HOLDER_FIELDS = {"f286278p", "p", "q", "o", "r", "a", "b"};

    private static volatile boolean sInstalled = false;
    private static volatile boolean gCleaning = false;
    private static volatile long gLastCleanMs = 0L;
    private static final long COOLDOWN_MS = 200L;

    private static final Set<String> sDiagSeen = Collections.synchronizedSet(new HashSet<String>());
    private static volatile String sAdapterClassName = null;
    private static final Set<String> sHookedAdapters = Collections.synchronizedSet(new HashSet<String>());

    // -------------------------------------------------------------------------

    // 8.0.71: conversation item class (full qualified, stable)
    private static final String CONV_MAIN_UI = "com.tencent.mm.ui.conversation.MainUI";

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        installMvvmListHooks(lpparam);
        installMvvmListL3Hooks(lpparam); // L3: single-item event channel (w/v/u etc.)
        installMvvmConvHooks(lpparam);
        installAddAllHook();             // 8.0.71 actual data path
        installAdapterDiscovery(lpparam);
        installAdapterHook(lpparam);
        installWarmAttach(lpparam);

        Log.i(TAG, "[CF] ConvFilter installed");
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
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        List<Object> list = extractListFromArg(param.args[0]);
                        if (list == null || list.isEmpty()) return;
                        // Broad diagnostic: log first item class for any MvvmList caller
                        String thisCls = param.thisObject.getClass().getName();
                        String itemCls = list.get(0).getClass().getName();
                        if (!sDiagSeen.contains(thisCls + label)) {
                            sDiagSeen.add(thisCls + label);
                            Log.i(TAG, "[CF:L1broad] " + label + " thisCls=" + thisCls
                                    + " sz=" + list.size() + " itemCls=" + itemCls);
                            Bridge.getInstance().addRawFeedLine(
                                    "[CF:L1broad] " + label + " " + thisCls + " item=" + itemCls);
                        }
                        filterConvList(list, label);
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
                    Object adapter = param.args[0];
                    if (adapter == null) return;
                    String cn = adapter.getClass().getName();
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
        for (String cn : new String[]{ADAPTER_CLASS_71, ADAPTER_CLASS_66}) {
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
                        if (gCleaning) return;
                        long now = System.currentTimeMillis();
                        if (now - gLastCleanMs < COOLDOWN_MS) return;
                        gLastCleanMs = now;
                        cleanAdapterMvvmList(param.thisObject);
                    }
                });
            if (n > 0) {
                Log.i(TAG, "[CF] L4 notifyDataSetChanged hooked: " + adapterCls.getName());
            }
        } catch (Throwable e) {
            Log.w(TAG, "[CF] L4 hook fail on " + adapterCls.getName() + ": " + e.getMessage());
        }
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
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Object adapter = callMethod(convListView, "getAdapter");
                    if (adapter == null) return;
                    Object mvvmList = findMvvmListOnAdapter(adapter);
                    if (mvvmList == null) return;
                    int cleaned = cleanMvvmList(mvvmList);
                    if (cleaned > 0) {
                        Log.i(TAG, "[CF] INIT warm-attach cleaned=" + cleaned);
                        callMethod(adapter, "notifyDataSetChanged");
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "[CF] INIT warm-attach run fail: " + e.getMessage());
                }
            }
        }, 2000);
    }

    // =========================================================================
    // Core filter logic
    // =========================================================================

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
            if (wxid != null && !wxid.startsWith("gh_")) {
                Bridge.getInstance().addConvWxid(wxid, null);
            }
        }

        // Phase 2: filter (hidden mode only)
        if (!StateMachine.getInstance().isActive()) return;
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        if (hidden.isEmpty()) return;

        int before = list.size();
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) continue;
            String wxid = extractWxid(item);
            if (wxid == null) continue;
            if ("weixin".equals(wxid) && hasUnread(item)) continue;
            if (hidden.contains(wxid)) {
                it.remove();
                Log.i(TAG, "[CF] " + label + " removed wxid=" + wxid);
                InterceptCounter.getInstance().incF04(wxid);
                DebugTelemetry dt = DebugTelemetry.getInstance();
                dt.emit("conv", "conv_blocked",
                        DebugTelemetry.fields("wxid", wxid, "label", label));
                dt.addBlocked("conv");
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
        if (mvvmList == null) {
            if (sDiagSeen.add("L4_no_mvvm_" + adapter.getClass().getName()))
                Log.w(TAG, "[CF] L4 MvvmList not found on " + adapter.getClass().getName());
            return;
        }

        // Phase 1: unconditional — collect wxids for debug UI + diag
        collectFromMvvmList(mvvmList);

        // Phase 2: filter only in hidden mode
        if (!StateMachine.getInstance().isActive()) return;
        if (Bridge.getInstance().allHiddenIds().isEmpty()) return;
        gCleaning = true;
        try {
            int total = cleanMvvmList(mvvmList);
            if (total > 0) Log.i(TAG, "[CF] L4 clean total=" + total);
        } finally {
            gCleaning = false;
        }
    }

    /** Unconditional scan — populates convSeen + diag, no filtering. */
    @SuppressWarnings("unchecked")
    private static void collectFromMvvmList(Object mvvmList) {
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
                break; // first non-empty array is enough
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static int cleanMvvmList(Object mvvmList) {
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        int total = 0;
        for (String fieldName : MVVMLIST_ARRAY_FIELDS) {
            try {
                Field f = findFieldRecursive(mvvmList.getClass(), fieldName);
                if (f == null) continue;
                f.setAccessible(true);
                Object arr = f.get(mvvmList);
                if (!(arr instanceof List)) continue;
                List<Object> list = (List<Object>) arr;
                Iterator<Object> it = list.iterator();
                while (it.hasNext()) {
                    Object item = it.next();
                    if (item == null) continue;
                    String wxid = extractWxid(item);
                    if (wxid == null) continue;
                    if ("weixin".equals(wxid) && hasUnread(item)) continue;
                    if (hidden.contains(wxid)) { it.remove(); total++; }
                }
            } catch (Throwable ignored) {}
        }
        return total;
    }

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

    private static Object findMvvmListOnAdapter(Object adapter) {
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
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Returns true if obj is MvvmList or any subclass of it. */
    private static boolean isMvvmListInstance(Object obj) {
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

    @SuppressWarnings("unchecked")
    private static List<Object> extractListFromArg(Object arg) {
        if (arg == null) return null;
        if (arg instanceof List) return (List<Object>) arg;
        try {
            Class<?> cls = arg.getClass();
            for (int depth = 0; cls != null && cls != Object.class && depth < 3; depth++) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(arg);
                    if (v instanceof List) {
                        if (sDiagSeen.add("Larg_" + arg.getClass().getName() + "#" + f.getName()))
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

    private static Object getFieldSafe(Object obj, String name) {
        try {
            Field f = findFieldRecursive(obj.getClass(), name);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) { return null; }
    }

    private static String getStrField(Object obj, String name) {
        Object v = getFieldSafe(obj, name);
        return (v instanceof String) ? (String) v : null;
    }

    private static String callStrMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            Object r = m.invoke(obj);
            return (r instanceof String) ? (String) r : null;
        } catch (Throwable ignored) { return null; }
    }

    private static Object callMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            return m.invoke(obj);
        } catch (Throwable ignored) { return null; }
    }

    private static Field findFieldRecursive(Class<?> cls, String name) {
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
                || "weixin".equals(s) || "filehelper".equals(s)
                || "notifymessage".equals(s) || "floatbottle".equals(s)
                || s.startsWith("qqmail_") || s.startsWith("newsapp");
    }
}
