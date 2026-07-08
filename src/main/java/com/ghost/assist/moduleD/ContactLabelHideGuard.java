package com.ghost.assist.moduleD;

import android.app.Activity;
import android.util.Log;

import com.ghost.assist.core.Bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P19B 隐私补洞 — 隐藏通讯录「标签」入口（可开关，Bridge hcl）。
 * 非独家功能：独家 = 隐藏标签列表里「指定的某个标签」（见 P26C，待实现）。
 *
 * 8.0.71 类名（P19B 探针 + 装机）：
 *   通讯录列表 item     fc5.g  — 移除 e=8 的标签入口行
 *   标签管理列表 item   com.tencent.mm.storage.d4 — 清空 addAll
 *   标签成员/搜索 item  ye5.j  — 与 P19B 密友过滤独立；本 Guard 拦 Activity
 *   Activity            ContactLabelManagerUI / MvvmContactListUI / LabelSearchUI
 */
public class ContactLabelHideGuard {

    private static final String TAG = "NCL";

    private static final String ADDR_ITEM_CLS  = "fc5.g";
    private static final String LABEL_STORE_CLS = "com.tencent.mm.storage.d4";
    private static final String ITEM_TYPE_FIELD = "e";
    private static final String ITEM_Z3_FIELD  = "d";
    private static final String Z3_CLS         = "com.tencent.mm.storage.z3";

    /** 8.0.71 fc5.g.e — 标签入口行（L2 jadx + P19B 探针；非联系人 e=2、非字母头 e=1） */
    private static final int LABEL_ENTRY_TYPE = 8;

    private static volatile Field sItemE;
    private static volatile Field sItemD;
    private static volatile Method sZ3C1;

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installHideLabelEntryHook();
        installHideLabelStoreHook();
        installBlockLabelActivities(lpparam);
        Log.i(TAG, "[CLH] ContactLabelHideGuard installed");
    }

    static boolean shouldHideContactLabel() {
        Bridge br = Bridge.getInstance();
        return br.isFeatureEnabled() && br.isHideContactLabelEnabled();
    }

    // ------------------------------------------------------------------
    // L1: 通讯录顶部「标签」行 — ArrayList.addAll(fc5.g)
    // ------------------------------------------------------------------
    private static void installHideLabelEntryHook() {
        try {
            XposedBridge.hookMethod(
                    java.util.ArrayList.class.getMethod("addAll", Collection.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!shouldHideContactLabel()) return;
                            Collection coll = (Collection) param.args[0];
                            if (coll == null || coll.isEmpty()) return;
                            Object first = coll.iterator().next();
                            if (first == null) return;
                            if (!ADDR_ITEM_CLS.equals(first.getClass().getName())) return;

                            int before = coll.size();
                            Iterator it = coll.iterator();
                            int removed = 0;
                            while (it.hasNext()) {
                                Object item = it.next();
                                if (item != null && isContactLabelEntry(item)) {
                                    try { it.remove(); removed++; } catch (UnsupportedOperationException ignored) {}
                                }
                            }
                            if (removed > 0) {
                                Log.i(TAG, "[CLH:entry] removed=" + removed + "/" + before);
                                Bridge.getInstance().addRawFeedLine(
                                        "[CLH:entry] fc5.g label-row removed=" + removed);
                            }
                        }
                    });
            Log.i(TAG, "[CLH] ArrayList.addAll(fc5.g) label-entry hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[CLH] label-entry hook fail: " + t);
        }
    }

    // ------------------------------------------------------------------
    // L2: 标签管理页列表 — ArrayList.addAll(d4)
    // ------------------------------------------------------------------
    private static void installHideLabelStoreHook() {
        try {
            XposedBridge.hookMethod(
                    java.util.ArrayList.class.getMethod("addAll", Collection.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!shouldHideContactLabel()) return;
                            Collection coll = (Collection) param.args[0];
                            if (coll == null || coll.isEmpty()) return;
                            Object first = coll.iterator().next();
                            if (first == null) return;
                            if (!LABEL_STORE_CLS.equals(first.getClass().getName())) return;

                            int before = coll.size();
                            Iterator it = coll.iterator();
                            while (it.hasNext()) {
                                it.next();
                                try { it.remove(); } catch (UnsupportedOperationException ignored) {}
                            }
                            Log.i(TAG, "[CLH:store] cleared d4 batch sz=" + before);
                            Bridge.getInstance().addRawFeedLine(
                                    "[CLH:store] d4 cleared=" + before);
                        }
                    });
            Log.i(TAG, "[CLH] ArrayList.addAll(d4) hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[CLH] d4 hook fail: " + t);
        }
    }

    // ------------------------------------------------------------------
    // L3: 深链兜底 — 标签相关 Activity 直接 finish
    // ------------------------------------------------------------------
    private static void installBlockLabelActivities(XC_LoadPackage.LoadPackageParam lpparam) {
        final java.util.Set<String> targets = new java.util.HashSet<>(java.util.Arrays.asList(
                "com.tencent.mm.plugin.label.ui.ContactLabelManagerUI",
                "com.tencent.mm.ui.contact.MvvmContactListUI",
                "com.tencent.mm.plugin.label.ui.searchLabel.LabelSearchUI"
        ));
        int hooked = 0;
        for (String clsName : targets) {
            try {
                Class<?> actCls = lpparam.classLoader.loadClass(clsName);
                XposedBridge.hookMethod(
                        actCls.getMethod("onResume"),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (!shouldHideContactLabel()) return;
                                try {
                                    Activity act = (Activity) param.thisObject;
                                    // getMethod("onResume") 解析到共同父类(MMActivity)的 onResume，该 hook
                                    // 会命中所有未重写 onResume 的微信页面(搜索/设置等)。必须按真实类名精确放行，
                                    // 只 finish 目标标签页，否则误杀 FTSMainUI/MainSettingsUI 等导致页面进不去。
                                    if (!targets.contains(act.getClass().getName())) return;
                                    Log.i(TAG, "[CLH:act] finish " + act.getClass().getSimpleName());
                                    act.finish();
                                } catch (Throwable t) {
                                    Log.w(TAG, "[CLH:act] finish err: " + t);
                                }
                            }
                        });
                hooked++;
            } catch (Throwable t) {
                Log.w(TAG, "[CLH] skip activity " + clsName + ": " + t);
            }
        }
        Log.i(TAG, "[CLH] label activity block hooked=" + hooked);
    }

    /** fc5.g 标签入口行判定：type=8 或 z3 用户名含 label 标记 */
    static boolean isContactLabelEntry(Object item) {
        try {
            int type = readItemType(item);
            if (type == LABEL_ENTRY_TYPE) return true;
            String raw = readZ3UsernameRaw(item);
            if (raw != null) {
                String lower = raw.toLowerCase();
                if (lower.contains("label") || lower.contains("contactlabel")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** 从已加载的通讯录 MvvmList 数组移除标签行（RefreshBus / L4 兜底） */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static int purgeLabelEntriesFromList(java.util.List items) {
        if (!shouldHideContactLabel() || items == null || items.isEmpty()) return 0;
        int removed = 0;
        Iterator it = items.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) continue;
            if (!ADDR_ITEM_CLS.equals(item.getClass().getName())) continue;
            if (isContactLabelEntry(item)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            Log.i(TAG, "[CLH:purge] removed=" + removed);
            Bridge.getInstance().addRawFeedLine("[CLH:purge] label-row=" + removed);
        }
        return removed;
    }

    private static int readItemType(Object item) {
        try {
            if (sItemE == null) {
                Field f = item.getClass().getDeclaredField(ITEM_TYPE_FIELD);
                f.setAccessible(true);
                sItemE = f;
            }
            Object val = sItemE.get(item);
            return val instanceof Integer ? (Integer) val : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String readZ3UsernameRaw(Object item) {
        try {
            if (sItemD == null) {
                Field f = item.getClass().getDeclaredField(ITEM_Z3_FIELD);
                f.setAccessible(true);
                sItemD = f;
            }
            Object z3 = sItemD.get(item);
            if (z3 == null || !Z3_CLS.equals(z3.getClass().getName())) return null;
            if (sZ3C1 == null) {
                sZ3C1 = z3.getClass().getMethod("c1");
            }
            Object res = sZ3C1.invoke(z3);
            return res != null ? res.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
