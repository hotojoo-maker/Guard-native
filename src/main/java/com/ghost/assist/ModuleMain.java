package com.ghost.assist;

import android.app.Application;
import android.os.Process;
import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.DebugServer;
import com.ghost.assist.debug.OverlayWindow;
import com.ghost.assist.debug.StatusNotification;
import com.ghost.assist.debug.UiContextTracker;
import com.ghost.assist.moduleB.SearchFilter;
import com.ghost.assist.moduleB.SearchUnlock;
import com.ghost.assist.moduleB.SelfProfileCapture;
import com.ghost.assist.moduleB.SettingsEntry;
import com.ghost.assist.moduleB.TriggerGuard;
import com.ghost.assist.moduleB.UpdateGuard;
import com.ghost.assist.moduleC.AntiRecall;
import com.ghost.assist.moduleC.PushFilter;
import com.ghost.assist.moduleD.ContactFilter;
import com.ghost.assist.moduleD.ContactLabelHideGuard;
import com.ghost.assist.moduleD.ContactLabelMemberFilter;
import com.ghost.assist.moduleD.ConvFilter;
import com.ghost.assist.moduleD.MomentsFilter;
// MomentsRedDotGuard — 朋友圈小红点（P21）；Layer0b/Layer2 证据来源 chatfish 反编译 + frida trace，
// LSPosed 自有装机日志尚未抓到原文（证据级 L3），但代码已注册 install（见下方 L149）
import com.ghost.assist.moduleD.MomentsRedDotGuard;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module entry — F-16: process whitelist enforced first line.
 */
public class ModuleMain implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "NCL"; // seed-based, not "Guard"/"Vip"/etc
    private static final String WX_PKG = "com.tencent.mm";
    private static boolean sInitialized = false;

    // --- Zygote init: pre-load config ---
    @Override
    public void initZygote(StartupParam param) {
        DebugServer.setModulePath(param.modulePath);
    }

    // --- Load package: process whitelist FIRST ---
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // F-16: process whitelist — main process and :push only.
        // All other processes (sandboxed/isolated/appbrand) must be ignored.
        boolean isMain = WX_PKG.equals(lpparam.processName);
        boolean isPush = (WX_PKG + ":push").equals(lpparam.processName);
        if (!isMain && !isPush) return;

        // :push process — native init + push notification hooks
        if (isPush) {
            initPushGuard(lpparam);
            return;
        }

        // Main process — hook Application.onCreate to get app context
        try {
            XposedHelpers.findAndHookMethod(
                Application.class,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        onApplicationCreated((Application) param.thisObject, lpparam);
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "[init] hook Application.onCreate failed: " + t);
        }
    }

    /**
     * :push process entry.
     * F-27: nativeInit must complete before any hook registration.
     * Iron rule 6: only push-gate / badge hooks allowed here.
     */
    private void initPushGuard(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            boolean ok     = NativeBridge.init(lpparam.processName, WX_PKG);
            int role       = NativeBridge.getProcessRole();
            boolean hidden = NativeBridge.isHidden();
            Log.i(TAG, "[native:push] init=" + ok
                    + " role=" + role + "(expect 2=PUSH)"
                    + " hidden=" + hidden);
            // F-27: nativeInit complete — now safe to install push hooks
            PushFilter.installForPush(lpparam);
        } catch (Throwable t) {
            Log.e(TAG, "[native:push] init crash: " + t);
        }
    }

    private void onApplicationCreated(Application app, XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInitialized) return;
        sInitialized = true;

        Log.i(TAG, "[init] pid=" + Process.myPid() + " proc=" + lpparam.processName);

        // 0. NativeBridge — Batch 1 Phase 1 verification (before any hook registration)
        //    Iron rule 27: nativeInit must complete before business hooks.
        runNativeBridgeVerification(lpparam.processName);

        // 1. Init config (DEV/PROD/HONEY)
        AppConfig.getInstance().init(app);

        // 2. Init MMKV bridge
        Bridge.getInstance().init(app);

        // 3. Init state machine
        StateMachine.getInstance().init(app);

        // 4. Init intercept counter
        InterceptCounter.getInstance().init();

        // 5. Kill switch check (v1: placeholder, always false)
        boolean killed = AppConfig.getInstance().isKillSwitch();
        Log.i(TAG, "[init] killSwitch=" + killed);
        if (killed) {
            android.widget.Toast.makeText(app, "已停用，等待更新", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // 6. Restore state from persistence
        StateMachine.getInstance().restoreState();

        // 7. Install module hooks
        // jy0.t(doRevokeMsg) 是 tinker 补丁类 → 必须用 app classloader（见下方 PushFilter 同款注释）
        AntiRecall.install(lpparam, app.getClassLoader());
        SelfProfileCapture.install(lpparam);   // 抓自己 wxid/alias/nick → Bridge（授权评估前置数据）
        SearchUnlock.install(lpparam);
        SearchFilter.install(lpparam);
        MomentsFilter.install(lpparam);
        ConvFilter.install(lpparam);
        ContactFilter.install(lpparam);
        ContactLabelHideGuard.install(lpparam); // P19B 通讯录【标签】入口/管理/Activity 隐藏
        ContactLabelMemberFilter.install(lpparam); // P19B 标签内成员列表 ye5.j 密友过滤（HIDDEN 态，2026-05-31 恢复并独立成模块）
        com.ghost.assist.moduleB.ContactImportGuard.install(lpparam); // P_IMPORT 密友/密群批量导入（复用 SelectContactUI）
        // 微信 8.0.71 带 Tinker 热补丁：运行时 UI/插件类由 app 的 DelegateLastClassLoader 加载，
        // 与 lpparam.classLoader（base.apk）不是同一份。hook 这类类必须用 app.getClassLoader()。
        PushFilter.install(lpparam, app.getClassLoader());

        // P21: 更新小红点 + 状态机自动触发器（代码已写，待装机验证）
        MomentsRedDotGuard.install(lpparam);
        UpdateGuard.install(lpparam);
        TriggerGuard.install(app);  // B1/B2/B5，Android API，不吃 lpparam
        com.ghost.assist.moduleD.ContactDiscoveryHook.install(app); // P_CV1 V1：动态发现通讯录 LiveList/Adapter

        // 设置入口 — 微信「我→设置」顶部注入"密友设置 ›"行（仅 VISIBLE 态可见）
        SettingsEntry.install(lpparam);

        // 8a. HTTP debug server always starts so web dashboard works in PROD mode.
        DebugServer.start();
        Log.i(TAG, "[init] debug server started port=" + AppConfig.getInstance().getServerPort());

        // 8b. UiContextTracker must run in PROD too — SearchUnlock/SearchFilter use
        //     getCurrentActivity() to finish() the search page after unlock.
        UiContextTracker.install(lpparam);

        // 8c. UI debug tools (overlay + notification) only in DEV/HONEY.
        if (AppConfig.getInstance().isDebugEnabled()) {
            StatusNotification.show(app);
            OverlayWindow.attach(app);
            Log.i(TAG, "[init] debug UI tools started");
        }

        Log.i(TAG, "[init] ready — state=" + StateMachine.getInstance().getStateName());
    }

    /**
     * Batch 1 Phase 1 — NativeBridge smoke-test.
     * Logs init result + 7 verification checks to logcat TAG "NCL".
     * Remove or gate behind AppConfig.isDebugEnabled() after Phase 3 full integration.
     */
    private void runNativeBridgeVerification(String processName) {
        try {
            // (1) SO available?
            Log.i(TAG, "[native] available=" + NativeBridge.isAvailable());

            // (2) init
            boolean ok = NativeBridge.init(processName, WX_PKG);
            Log.i(TAG, "[native] init=" + ok);

            // (3) processRole — must be ROLE_MAIN (1)
            int role = NativeBridge.getProcessRole();
            Log.i(TAG, "[native] role=" + role + " (expect 1=MAIN)");

            // (4) isHidden — must be true (cold-start defaults to HIDDEN)
            boolean hidden = NativeBridge.isHidden();
            Log.i(TAG, "[native] isHidden=" + hidden + " (expect true)");

            // (5) setHidden(false) then re-check — must flip to false
            NativeBridge.setHidden(false);
            boolean hiddenAfterFalse = NativeBridge.isHidden();
            Log.i(TAG, "[native] isHidden after setHidden(false)=" + hiddenAfterFalse + " (expect false)");
            NativeBridge.setHidden(true); // restore for real hooks

            // (6) isHiddenWxid — pre-seeded test wxid must return true
            boolean testWxidTrue = NativeBridge.isHiddenWxid("wxid_lzd2va16jd1622");
            Log.i(TAG, "[native] isHiddenWxid(wxid_lzd2va16jd1622)=" + testWxidTrue + " (expect true)");

            // (7) isHiddenWxid — unknown wxid must return false
            boolean testWxidFalse = NativeBridge.isHiddenWxid("wxid_other");
            Log.i(TAG, "[native] isHiddenWxid(wxid_other)=" + testWxidFalse + " (expect false)");

            // (8) configVersion
            int cfgVer = NativeBridge.getConfigVersion();
            Log.i(TAG, "[native] configVersion=" + cfgVer + " (expect 1)");

            // Summary line for quick grep
            boolean allPass = ok && role == NativeBridge.ROLE_MAIN && hidden
                    && !hiddenAfterFalse && testWxidTrue && !testWxidFalse && cfgVer == 1;
            Log.i(TAG, "[native] BATCH1_VERIFY " + (allPass ? "PASS" : "FAIL"));
        } catch (Throwable t) {
            Log.e(TAG, "[native] verification crash: " + t);
        }
    }

}
