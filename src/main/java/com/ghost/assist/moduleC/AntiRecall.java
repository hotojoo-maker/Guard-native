package com.ghost.assist.moduleC;

import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AntiRecall — 防撤回（P23，C1 模块）
 *
 * Hook chain（8.0.71 探针实证，2026-05-25）：
 *   a2.b(com.tencent.mm.modelbase.p0 msg, z15.ut4 envelope, ge3.z4 cb) → q0
 *
 * 方案：在 beforeHookedMethod 直接 setResult(null) 跳过整个 a2.b() 执行，
 *       从根本上阻止 DB 写入（撤回标记写 SQLite 在 a2.b() 内部，skip 后不发生）。
 *       之前的 snapshot+restore 方案无效：DB 写入在 afterHook 之前完成，
 *       恢复内存字段对已持久化的撤回状态无用（见 worklog P23）。
 *
 * 授权门（不依赖密友 f1，全局生效）：
 *   isVipAuthorized() && Bridge.isAntiRecallEnabled()
 *
 * 版本历史：
 *   v2  2026-05-25  改为 skip 方案（setResult null），移除无效 snapshot
 *   v1  2026-05-25  snapshot+restore（DB 写入后失效，已废弃）
 *
 * 参数版本对照（勿删，升版本核对用）：
 *   8.0.70 Catfish: b(m05.ys4, sc3.z4, ...)
 *   8.0.71 本版本:  b(z15.ut4, ge3.z4, ...)
 */
public class AntiRecall {

    private static final String TAG = "NCL";

    private static final String WX_CLASS  = "com.tencent.mm.plugin.messenger.foundation.a2";
    private static final String WX_METHOD = "b";
    private static final String PARAM_MSG = "com.tencent.mm.modelbase.p0";
    private static final String PARAM_ENV = "z15.ut4";
    private static final String PARAM_CB  = "ge3.z4";

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
                            // Skip a2.b() entirely — prevents DB revoke-mark write
                            param.setResult(null);
                            Log.i(TAG, "[AR] revoke blocked (skip a2.b)");
                        } catch (Throwable t) {
                            Log.e(TAG, "[AR:before] err: " + t);
                        }
                    }
                }
            );
            Log.i(TAG, "[AR] hook installed: " + WX_CLASS + "#" + WX_METHOD);
        } catch (Throwable t) {
            Log.e(TAG, "[AR] install failed: " + t);
        }
    }
}
