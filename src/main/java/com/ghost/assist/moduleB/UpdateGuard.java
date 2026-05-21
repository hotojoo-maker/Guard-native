package com.ghost.assist.moduleB;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * B7 屏蔽更新小红点（P21 优先 #3）。
 *
 * 8.0.71 调研结果（用户提供 2026-05-21）:
 *   - 字段类候选: fl4.o (8.0.71 混淆)
 *   - UI 类: com.tencent.mm.ui.setting.SettingsAboutMicroMsgUI
 *   - 主入口判定: SettingsUI.P7() —— 待装机确认是否 boolean 返回值
 *
 * 8.0.66 对应类（参考）: gd4.o (混淆名不同，仅记录不引用)
 *
 * 策略：保守探针 — 同时 hook 候选 UI 类的所有 0-param boolean/int 返回方法，
 *      隐藏态返回 false / 0 + log；装机时观察哪个真的让红点消失。
 */
public class UpdateGuard {

    private static final String TAG = "NCL";

    private static final String[] UPDATE_UI_CANDIDATES = {
            // 8.0.71 实证：fl4.o 是更新红点 DI 容器（实现 gl4.e）
            // Pg/Sh/Th/Wh 4 个 boolean getter 已 hook 返 false → 设置页红点消失
            // SettingsUI 不需要 hook：它只消费 fl4.o 的数据，源头已断
            "fl4.o",
    };

    // SettingsUI.P7() 是用户给的入口；fl4.o 上 Sh/Th/Wh 是真实红点 getter（反编译实证）
    private static final String[] CANDIDATE_GETTER_PREFIXES = {
            // SettingsUI 入口候选（P7 可能在父类，要用 getMethods）
            "P7", "P8", "P9",
            // fl4.o 上的红点状态 getter（反编译 2026-05-21 实证）
            "Sh",   // RedDotAboutWechatTab
            "Th",   // RedCheckManualUpdaterTab
            "Wh",   // 完整 APK 更新
            "Pg",   // 用户日志显示 0-param 候选之一
            // 兜底通用名
            "hasUpdate", "getUpdate", "checkUpdate", "needUpdate",
    };

    // fl4.o 是更新红点 DI 容器（实现 gl4.e），其上**所有** 0-param boolean getter
    // 都是红点状态判定，可以无差别 hook 返 false（不在 CANDIDATE_GETTER_PREFIXES 内的也覆盖）
    private static final String FL4_O = "fl4.o";

    private static boolean sInstalled = false;
    private static final java.util.Set<String> sDiagSeen = new java.util.HashSet<>();

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        for (String candidate : UPDATE_UI_CANDIDATES) {
            tryHookClass(lpparam, candidate);
        }

        Log.i(TAG, "[UG] install done");
    }

    private static void tryHookClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass(className);
            boolean isFl4O = FL4_O.equals(className);

            // 首次加载诊断 — 列出所有方法（含继承）的 0-param bool/int getter
            // 用 getMethods() 而非 getDeclaredMethods()：P7 可能在父类
            StringBuilder diag = new StringBuilder("[UG:diag] " + className + " 0-param bool/int methods: ");
            int diagCount = 0;
            for (Method m : cls.getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> ret = m.getReturnType();
                if (ret != boolean.class && ret != int.class
                        && ret != Boolean.class && ret != Integer.class) continue;
                // 跳过 Object 自带的（hashCode 等）
                if (m.getDeclaringClass() == Object.class) continue;
                diag.append(m.getName()).append("(").append(ret.getSimpleName()).append(") ");
                diagCount++;
            }
            if (diagCount > 0) Log.i(TAG, diag.toString());

            int hooked = 0;
            for (Method m : cls.getMethods()) {
                if (m.getDeclaringClass() == Object.class) continue;
                if (m.getParameterTypes().length != 0) continue;
                Class<?> ret = m.getReturnType();
                if (ret != boolean.class && ret != int.class
                        && ret != Boolean.class && ret != Integer.class) continue;

                // fl4.o: 无差别 hook 所有 0-param boolean getter（已知专用红点容器）
                // 其他类: 仅 hook CANDIDATE_GETTER_PREFIXES 命中的（避免误伤）
                boolean shouldHook = isFl4O
                        ? (ret == boolean.class || ret == Boolean.class)
                        : isCandidateGetter(m.getName());
                if (!shouldHook) continue;

                final String mn = m.getName();
                final boolean isBool = (ret == boolean.class || ret == Boolean.class);
                final String declCls = m.getDeclaringClass().getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!AppConfig.getInstance().isUpdateRedDotEnabled()) return;

                        Object original = param.getResult();
                        if (isBool) param.setResult(false);
                        else        param.setResult(0);

                        if (sDiagSeen.add(declCls + "." + mn)) {
                            Log.i(TAG, "[UG] " + className + "·" + declCls + "." + mn
                                    + " " + original + " → blocked");
                        }
                    }
                });
                hooked++;
            }
            Log.i(TAG, "[UG] " + className + " hooked " + hooked);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "[UG] class not found: " + className);
        } catch (Throwable t) {
            Log.w(TAG, "[UG] " + className + " hook failed: " + t);
        }
    }

    private static boolean isCandidateGetter(String mn) {
        for (String p : CANDIDATE_GETTER_PREFIXES) {
            if (mn.equals(p) || mn.startsWith(p)) return true;
        }
        return false;
    }
}
