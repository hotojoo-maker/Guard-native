package com.ghost.assist.moduleC;

import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AntiRecall — 防撤回（P23，C1 模块）
 *
 * Hook chain（8.0.71 实证，2026-05-25）：
 *   a2.b(com.tencent.mm.modelbase.p0 msg, z15.ut4 envelope, ge3.z4 cb) → q0
 *
 * 原理：
 *   a2.b 被调用时 args[0] 是原始消息对象 p0；WeChat 在方法体内修改 p0 字段
 *   （type → 撤回类型，content → 清空）并写 DB。
 *   beforeHookedMethod 把 p0 所有可写字段做快照；
 *   afterHookedMethod 恢复快照 → 消息内容保留原样。
 *
 * 授权门（不依赖密友 f1）：
 *   isVipAuthorized() && Bridge.isAntiRecallEnabled()
 *
 * 版本历史：
 *   v1  2026-05-25  类名/方法名已验证；before/after 快照方案
 *
 * 参数版本对照（勿删，方便升版本时核对）：
 *   8.0.70 Catfish: b(m05.ys4, sc3.z4, ...)  a-1=m05.a40  c-0=m05.i4
 *   8.0.71 本版本:  b(z15.ut4, ge3.z4, ...)   a-1=z15.g40  c-0=z15.i4
 */
public class AntiRecall {

    private static final String TAG = "AR";

    // WeChat 8.0.71 撤回入口（2026-05-25 探针实证）
    private static final String WX_CLASS   = "com.tencent.mm.plugin.messenger.foundation.a2";
    private static final String WX_METHOD  = "b";
    // 参数类型全名（按探针输出顺序）
    private static final String PARAM_MSG  = "com.tencent.mm.modelbase.p0";
    private static final String PARAM_ENV  = "z15.ut4";
    private static final String PARAM_CB   = "ge3.z4";

    private static final String EXTRA_SNAP = "ar_snap"; // setObjectExtra key

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                WX_CLASS, lpparam.classLoader,
                WX_METHOD,
                PARAM_MSG, PARAM_ENV, PARAM_CB,
                new XC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (!StateMachine.getInstance().isVipAuthorized()) return;
                            if (!Bridge.getInstance().isAntiRecallEnabled()) return;

                            Object msg = param.args[0];
                            if (msg == null) return;

                            param.setObjectExtra(EXTRA_SNAP, snapshot(msg));
                        } catch (Throwable t) {
                            Log.e(TAG, "[AR:before] err: " + t);
                        }
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (!StateMachine.getInstance().isVipAuthorized()) return;
                            if (!Bridge.getInstance().isAntiRecallEnabled()) return;

                            Object msg = param.args[0];
                            Map<Field, Object> snap =
                                (Map<Field, Object>) param.getObjectExtra(EXTRA_SNAP);
                            if (msg == null || snap == null) return;

                            restore(msg, snap);
                            Log.i(TAG, "[AR:b] revoke blocked, p0 restored");
                        } catch (Throwable t) {
                            Log.e(TAG, "[AR:after] err: " + t);
                        }
                    }
                }
            );
            Log.i(TAG, "[AR] hook installed: " + WX_CLASS + "#" + WX_METHOD);
        } catch (Throwable t) {
            Log.e(TAG, "[AR] install failed: " + t);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** 对 obj 的所有可写字段做浅快照（含继承链，跳过 static/final）。 */
    private static Map<Field, Object> snapshot(Object obj) {
        Map<Field, Object> snap = new HashMap<>();
        for (Field f : allFields(obj.getClass())) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                snap.put(f, f.get(obj));
            } catch (Throwable ignored) {}
        }
        return snap;
    }

    /** 将快照写回 obj（跳过 static/final 字段）。 */
    private static void restore(Object obj, Map<Field, Object> snap) {
        for (Map.Entry<Field, Object> e : snap.entrySet()) {
            Field f = e.getKey();
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (Modifier.isFinal(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                f.set(obj, e.getValue());
            } catch (Throwable ignored) {}
        }
    }

    /** 遍历 cls 及其全部父类的 declaredFields（不含 Object）。 */
    private static List<Field> allFields(Class<?> cls) {
        List<Field> list = new ArrayList<>();
        while (cls != null && !cls.equals(Object.class)) {
            for (Field f : cls.getDeclaredFields()) {
                list.add(f);
            }
            cls = cls.getSuperclass();
        }
        return list;
    }
}
