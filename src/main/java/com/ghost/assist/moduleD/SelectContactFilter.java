package com.ghost.assist.moduleD;

import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P_SelectFilter —— 发朋友圈选人器隐私过滤（2026-06-30，方案 C2；探查 + jadx + L1 实证）。
 *
 * 选人器（`SelectContactUI`「谁可以看 / 不给谁看 → 选择朋友」，与 `SnsAddressUI` /
 * `SnsSelectConversationAddressUI` 共用）的 adapter = `com.tencent.mm.ui.contact.h0`；其字段 `m`
 * （ArrayList&lt;String&gt;）= 排除名单（构造时灌系统账号 / gh_ 公众号），`h0.s()` 重查时按 `m` 排除。
 *
 * 本 Filter hook `h0.s()` beforeHook：隐藏态（isActive）把密友 ∪ 密群（allHiddenIds）注入 `m`
 * → s() 自身按 m 排除 → 选人器不显示密友 / 密群。每次重查自动生效，无需手动触发。
 *
 * 【门控】只读 `StateMachine.isActive()`；显形态 `isActive()=false` 不注入（加密友 `ContactImportGuard`
 *   在显形态拉起 `SelectContactUI`，不受影响）。不碰状态机 / 授权 / cursor 数据，纯排除名单注入。
 */
public final class SelectContactFilter {

    private static final String TAG = "NCL";
    private static final String ADAPTER_CLASS = "com.tencent.mm.ui.contact.h0";
    private static final String EXCLUDE_FIELD = "m";

    private static volatile Field sExcludeField;

    private SelectContactFilter() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader cl) {
        try {
            Class<?> adapter = XposedHelpers.findClass(ADAPTER_CLASS, cl);
            XposedBridge.hookMethod(adapter.getDeclaredMethod("s"), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        injectHidden(param.thisObject);
                    } catch (Throwable t) {
                        Log.w(TAG, "[SCF] inject err: " + t);
                    }
                }
            });
            Log.i(TAG, "[SCF] SelectContactFilter installed (hook h0.s)");
        } catch (Throwable t) {
            Log.w(TAG, "[SCF] install fail: " + t);
        }
    }

    /** 隐藏态把密友 ∪ 密群注入 adapter 排除名单 m；s() 自身据 m 排除。 */
    private static void injectHidden(Object adapter) {
        if (adapter == null) return;
        if (!StateMachine.getInstance().isActive()) return; // 显形态不过滤（加密友不受影响）
        Set<String> hidden = Bridge.getInstance().allHiddenIds();
        if (hidden.isEmpty()) return;

        List<String> excl = resolveExcludeList(adapter);
        if (excl == null) return;

        int added = 0;
        for (String id : hidden) {
            if (id != null && !id.isEmpty() && !excl.contains(id)) {
                excl.add(id);
                added++;
            }
        }
        if (added > 0) {
            Log.i(TAG, "[SCF] excluded +" + added + " (list=" + excl.size() + ")");
        }
    }

    /** 反射 adapter 类层级里名为 m 的 ArrayList（= a5.m 排除名单），缓存 Field。 */
    @SuppressWarnings("unchecked")
    private static List<String> resolveExcludeList(Object adapter) {
        try {
            Field f = sExcludeField;
            if (f == null) {
                Class<?> c = adapter.getClass();
                while (c != null && c != Object.class) {
                    try {
                        Field cand = c.getDeclaredField(EXCLUDE_FIELD);
                        cand.setAccessible(true);
                        Object v = cand.get(adapter);
                        if (v instanceof java.util.ArrayList) {
                            f = cand;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                    c = c.getSuperclass();
                }
                if (f == null) return null;
                sExcludeField = f;
            }
            Object v = f.get(adapter);
            return (v instanceof List) ? (List<String>) v : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
