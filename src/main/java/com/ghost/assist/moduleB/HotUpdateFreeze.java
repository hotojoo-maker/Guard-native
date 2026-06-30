package com.ghost.assist.moduleB;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.ghost.assist.core.AppConfig;

import java.io.File;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 官方热更新通道冻结（libcso 主 / Tinker 次）。
 *
 * 与 UpdateGuard(B7) 分工：UpdateGuard = 设置页更新红点 UI；本类 = 热更新通道层。
 *
 * 模式（AppConfig.isHotFreezeEnabled）：
 *   false（默认）= 观测：hook 挂上，只 log 命中，不改行为（冒烟验崩溃 + 看通道是否触发）。
 *   true         = 冻结：命中即 no-op，断「查更 / 下载 / apply」。
 *
 * 全程只 hook Java 方法、不碰 native（与 F-23 无关）；libcso native(mprotect) 禁。
 *
 * 锚点（jadx 8.0.71 L2 实读，详 brief.md）：
 *   libcso: ip.g.a(Application,String) / com.tencent.mm.sdk.platformtools.z.f176124s
 *   Tinker: p53.j.b(Map) / m53.d0.j(boolean) / m53.d0.d(File) / 官方自带闸 g45.c.f246162e
 *
 * 类名为 8.0.71 混淆名，随版本会变；每个 hook 独立 catch(Throwable)（F-25），缺一不影响其余。
 */
public class HotUpdateFreeze {

    private static final String TAG = "NCL";
    private static final String P = "[HUF]";

    private static boolean sInstalled = false;

    private static boolean freeze() { return AppConfig.getInstance().isHotFreezeEnabled(); }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader appCl) {
        if (sInstalled) return;
        sInstalled = true;
        ClassLoader cl = (appCl != null) ? appCl : lpparam.classLoader;

        Log.i(TAG, P + " install begin (mode=" + (freeze() ? "FREEZE" : "OBSERVE") + ")");

        hookLibcsoStartup(cl);
        hookTinkerCheckUpdate(cl);
        hookTinkerProcessResponse(cl);
        hookTinkerApply(cl);
        hookFullApkUpdate(cl);

        Log.i(TAG, P + " install done");
    }

    // ── libcso ───────────────────────────────────────────────────────────────

    /** C2：ip.g.a(Application,String) = CsoStartup 入口。命中即整条 CSO 启动不注册。 */
    private static void hookLibcsoStartup(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("ip.g", cl, "a",
                    Application.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // libcso 兼正常 SO 加载，整条冻结会误伤 → 这里只观测不拦；
                            // 真正要冻的是「远程下载那一段」（G3 未定位），不在此 no-op。
                            Log.i(TAG, P + " libcso ip.g.a fired (observe-only)");
                        }
                    });
            Log.i(TAG, P + " hook ip.g.a installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook ip.g.a fail: " + t);
        }
    }

    // ── Tinker ─────────────────────────────────────────────────────────────────

    /** C1：p53.j.b(Map) = checkAvailableUpdate，命中即不发查更 netscene。 */
    private static void hookTinkerCheckUpdate(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("p53.j", cl, "b", Map.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (freeze()) {
                        param.setResult(null);
                        Log.i(TAG, P + " tinker p53.j.b → blocked (checkAvailableUpdate)");
                    } else {
                        Log.i(TAG, P + " tinker p53.j.b fired (observe)");
                    }
                }
            });
            Log.i(TAG, P + " hook p53.j.b installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook p53.j.b fail: " + t);
        }
    }

    /** C4：m53.d0.j(boolean) = 处理 syncResponse（调度下载），命中即 return false。 */
    private static void hookTinkerProcessResponse(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("m53.d0", cl, "j", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (freeze()) {
                        param.setResult(false);
                        Log.i(TAG, P + " tinker m53.d0.j → blocked (process response)");
                    } else {
                        Log.i(TAG, P + " tinker m53.d0.j fired (observe)");
                    }
                }
            });
            Log.i(TAG, P + " hook m53.d0.j installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook m53.d0.j fail: " + t);
        }
    }

    /** C5：m53.d0.d(File) = 下载后验签 + apply，命中即不验不装。 */
    private static void hookTinkerApply(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("m53.d0", cl, "d", File.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (freeze()) {
                        param.setResult(null);
                        Log.i(TAG, P + " tinker m53.d0.d → blocked (apply)");
                    } else {
                        Log.i(TAG, P + " tinker m53.d0.d fired (observe)");
                    }
                }
            });
            Log.i(TAG, P + " hook m53.d0.d installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook m53.d0.d fail: " + t);
        }
    }

    // ── 整包/Hdiff 版本更新（fl4.o = MicroMsg.Updater，与 B7 UpdateGuard 红点同类）──────────

    /**
     * 第3条线：整包客户端版本更新（「关于微信 → 检查更新」点了会后台下载新 APK）。
     *   Wg(boolean,boolean,boolean) = checkMMdiffUpdatePatchPkgVersion = 查更入口（自动+手动都走它）
     *   Bg(Context,String)         = checkAndShowInstallPatchDialog = 下载完弹「安装」对话框
     * 冻结=两处 no-op：源头不查更（无新版/无红点/不下载），兜底不弹装包框。只动 updater，不碰通用下载器。
     */
    private static void hookFullApkUpdate(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("fl4.o", cl, "Wg",
                    boolean.class, boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (freeze()) {
                                param.setResult(null);
                                Log.i(TAG, P + " fullapk fl4.o.Wg -> blocked (checkUpdate)");
                            } else {
                                Log.i(TAG, P + " fullapk fl4.o.Wg fired (observe)");
                            }
                        }
                    });
            Log.i(TAG, P + " hook fl4.o.Wg installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook fl4.o.Wg fail: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod("fl4.o", cl, "Bg",
                    Context.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (freeze()) {
                                param.setResult(false);
                                Log.i(TAG, P + " fullapk fl4.o.Bg -> blocked (install dialog)");
                            } else {
                                Log.i(TAG, P + " fullapk fl4.o.Bg fired (observe)");
                            }
                        }
                    });
            Log.i(TAG, P + " hook fl4.o.Bg installed");
        } catch (Throwable t) {
            Log.w(TAG, P + " hook fl4.o.Bg fail: " + t);
        }
    }
}
