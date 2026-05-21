package com.ghost.assist;

import android.app.Application;
import android.os.Process;
import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.DebugServer;
import com.ghost.assist.debug.OverlayWindow;
import com.ghost.assist.debug.StatusNotification;
import com.ghost.assist.debug.UiContextTracker;
import com.ghost.assist.moduleB.SearchFilter;
import com.ghost.assist.moduleB.SearchUnlock;
import com.ghost.assist.moduleB.TriggerGuard;
import com.ghost.assist.moduleB.UpdateGuard;
import com.ghost.assist.moduleD.ContactFilter;
import com.ghost.assist.moduleD.ConvFilter;
import com.ghost.assist.moduleD.MomentsFilter;
// MomentsRedDotGuard — 朋友圈小红点，LSPosed 方案卡关中，暂不注册（见 P21_MomentsRedDot/worklog.md）
// import com.ghost.assist.moduleD.MomentsRedDotGuard;

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
        // F-16: process whitelist — MUST be first line of business logic
        if (!WX_PKG.equals(lpparam.processName)) {
            return;
        }

        // Hook Application.onCreate to get the app context
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

    private void onApplicationCreated(Application app, XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInitialized) return;
        sInitialized = true;

        Log.i(TAG, "[init] pid=" + Process.myPid() + " proc=" + lpparam.processName);

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
        SearchUnlock.install(lpparam);
        SearchFilter.install(lpparam);
        MomentsFilter.install(lpparam);
        ConvFilter.install(lpparam);
        ContactFilter.install(lpparam);

        // P21: 更新小红点 + 状态机自动触发器（代码已写，待装机验证）
        // MomentsRedDotGuard.install(lpparam); // 朋友圈小红点卡关，LSPosed 路径未通，暂不注册
        UpdateGuard.install(lpparam);
        TriggerGuard.install(app);  // B1/B2/B5，Android API，不吃 lpparam

        // 8. Start debug tools (DEV/HONEY modes only)
        if (AppConfig.getInstance().isDebugEnabled()) {
            UiContextTracker.install(lpparam);
            startDebugTools(app);
        }

        Log.i(TAG, "[init] ready — state=" + StateMachine.getInstance().getStateName());
    }

    private void startDebugTools(Application app) {
        // Notification — direct NotificationManager (no Service)
        StatusNotification.show(app);

        // Overlay — direct WindowManager.addView (no Service)
        OverlayWindow.attach(app);

        // HTTP debug server
        DebugServer.start();

        Log.i(TAG, "[init] debug tools started");
    }
}
