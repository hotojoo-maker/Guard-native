package com.ghost.assist;

import android.app.Application;
import android.os.Process;
import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.AuthManager;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.RiskPromptController;
import com.ghost.assist.core.RiskState;
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
    private static volatile String sModulePath = null;

    // --- Zygote init: pre-load config ---
    @Override
    public void initZygote(StartupParam param) {
        sModulePath = param.modulePath;       // A-step2: read our own cert from this APK
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

        // 0a. A-step2: bind the registry key to our own signing cert before the
        //     registry is decrypted (anti-repackage). Must run before step 0.
        bindSigningCert(app);

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

        // 6.5. P4-1 + P1F: auth evaluate (wxid + device) → RiskState (唯一风险出口)
        //      → RiskPromptController (唯一弹窗)。
        //      v1: RECORD ONLY, NO gating — NO_LICENSE / MISMATCH still pass
        //      (GUARD_GATE_TRUTH §4)。RiskGate 是与 isActive() 三层【并联】的第四道门，
        //      本段只「评估 + 记录 + 决定弹不弹」，绝不改 isActive、不关功能、不清数据。
        //      弹窗只在 RiskState.shouldFunnel()（确认篡改超影子期）时由 RiskPromptController 决定。
        try {
            int authResult = AuthManager.evaluate(app);
            NativeBridge.setAuthState(authResult);
            Log.i(TAG, "[auth] evaluate=" + authResult + " (v1 record-only, not gating)");
            RiskState.Level riskLevel = RiskState.evaluate(app);
            Log.i(TAG, "[risk] level=" + riskLevel.label + " (v1 record-only, not gating)");
            RiskPromptController.maybeShow(app, "cold-start");
        } catch (Throwable t) {
            Log.e(TAG, "[auth] wire crash: " + t);
        }

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

        // P21: moments red-dot guard. Main path is verified; keep installed without
        // changing the validated D1/D2/D3 moments filters.
        MomentsRedDotGuard.install(lpparam);
        UpdateGuard.install(lpparam);
        TriggerGuard.install(app);  // B1/B2/B5，Android API，不吃 lpparam
        com.ghost.assist.moduleD.ContactDiscoveryHook.install(app); // P_CV1 V1：动态发现通讯录 LiveList/Adapter

        // E2 伪装订位 — 全局伪造定位（pz0.h 是 tinker 运行时类 → 必须用 app classloader）
        com.ghost.assist.moduleE.FakeLocation.install(lpparam, app.getClassLoader());

        // 设置入口 — 微信「我→设置」顶部注入"密友设置 ›"行（仅 VISIBLE 态可见）
        SettingsEntry.install(lpparam);

        // 8a. HTTP debug server — DEBUG build always; RELEASE only in HONEY mode.
        //     RELEASE + PROD (customer build) → never starts, so /api/hidden etc. are not exposed.
        if (BuildConfig.DEBUG || AppConfig.getInstance().isDebugEnabled()) {
            DebugServer.start();
            Log.i(TAG, "[init] debug server started port=" + AppConfig.getInstance().getServerPort());
        } else {
            Log.i(TAG, "[init] debug server skipped (release+prod)");
        }

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

    private static volatile boolean sCertBound = false;

    /**
     * Phase 1D-local A-step2 — read this module's own signing-cert SHA-256 and
     * push it into the SO as the registry-key binding material. A re-signed /
     * repackaged APK has a different cert → wrong key → registry scatters.
     * Logs only the first 4 bytes (so a release log doesn't hand out the full
     * bound value).
     */
    private void bindSigningCert(Application app) {
        try {
            // Read our OWN cert from the module APK file (sModulePath), NOT by
            // package name: querying com.ghost.assist from inside com.tencent.mm
            // is blocked by Android 11+ package visibility.
            String path = sModulePath;
            if (path == null || path.isEmpty()) {
                Log.w(TAG, "[native] certBind skipped: no module path");
                return;
            }
            android.content.pm.PackageManager pm = app.getPackageManager();
            android.content.pm.Signature sig = null;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(
                        path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi != null && pi.signingInfo != null) {
                    android.content.pm.Signature[] s = pi.signingInfo.getApkContentsSigners();
                    if (s != null && s.length > 0) sig = s[0];
                }
            }
            if (sig == null) {
                @SuppressWarnings("deprecation")
                android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(
                        path, android.content.pm.PackageManager.GET_SIGNATURES);
                if (pi != null && pi.signatures != null && pi.signatures.length > 0) {
                    sig = pi.signatures[0];
                }
            }
            if (sig == null) {
                Log.w(TAG, "[native] certBind skipped: no signatures in " + path);
                return;
            }
            byte[] sha = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray());
            NativeBridge.setBindingMaterial(sha);
            com.ghost.assist.core.GuardRuntime.resetConfigCache();
            sCertBound = true;
            Log.i(TAG, "[native] certBind set sha256[0..3]="
                    + String.format("%02x%02x%02x%02x", sha[0], sha[1], sha[2], sha[3]));
        } catch (Throwable t) {
            Log.e(TAG, "[native] certBind crash: " + t);
        }
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

            // (9) Phase 1A: AES-GCM self-test
            boolean decryptSelfTest = NativeBridge.decryptConfigSelfTest();
            Log.i(TAG, "[native] decryptSelfTest=" + decryptSelfTest + " (expect true)");

            // (10) Phase 1A: test registry roundtrip
            String testRegistry = NativeBridge.decryptConfigTestRegistry();
            boolean registryOk = testRegistry != null
                    && testRegistry.contains("test_r8071")
                    && testRegistry.contains("kc5.v0");
            Log.i(TAG, "[native] decryptTestRegistry=" + registryOk);

            // (11) Phase 1A: tampered tag must scatter (no real class names)
            byte[] badTag = new byte[]{
                    0x16, (byte) 0x85, 0x56, (byte) 0xad, 0x2d, 0x0b, 0x25, (byte) 0xab,
                    0x2a, (byte) 0xc7, (byte) 0x9f, 0x54, 0x34, (byte) 0xeb, 0x6a, 0x7a};
            String scatter = NativeBridge.decryptConfig(
                    new byte[]{0x67, 0x75, 0x61, 0x72, 0x64, 0x5f, 0x70, 0x31,
                               0x61, 0x5f, 0x6b, 0x65, 0x79, 0x21, 0x00, 0x00},
                    new byte[]{0x67, 0x75, 0x61, 0x72, 0x64, 0x6e, 0x6f, 0x6e,
                               0x63, 0x65, 0x30, 0x31},
                    new byte[]{0x01},
                    badTag);
            boolean scatterOk = scatter != null
                    && scatter.contains("scatter")
                    && !scatter.contains("kc5.v0");
            Log.i(TAG, "[native] decryptScatter=" + scatterOk);

            // (12) Phase 1B: registry parse self-test (no business wiring)
            boolean registrySelfTest = NativeBridge.registrySelfTest();
            Log.i(TAG, "[native] registrySelfTest=" + registrySelfTest + " (expect true)");
            Log.i(TAG, "[native] registrySummary=" + NativeBridge.registrySummary());

            // Summary line for quick grep
            boolean allPass = ok && role == NativeBridge.ROLE_MAIN && hidden
                    && !hiddenAfterFalse && testWxidTrue && !testWxidFalse && cfgVer == 1
                    && decryptSelfTest && registryOk && scatterOk;
            Log.i(TAG, "[native] BATCH1_VERIFY " + (allPass ? "PASS" : "FAIL"));
            Log.i(TAG, "[native] PHASE1A_VERIFY " + (decryptSelfTest && registryOk && scatterOk ? "PASS" : "FAIL"));
            Log.i(TAG, "[native] PHASE1B_VERIFY " + (registrySelfTest ? "PASS" : "FAIL"));
            // Phase 1C: encrypted registry + search.gateway coarse-grained entry.
            // registrySelfTest now also verifies the search.gateway semantics.
            boolean hasGateway = NativeBridge.registrySummary().contains("search.gateway");
            Log.i(TAG, "[native] PHASE1C_VERIFY " + (registrySelfTest && hasGateway ? "PASS" : "FAIL"));
            // Phase 1D-local A-step2: registry key is bound to our signing cert.
            // registrySelfTest passing while certBound proves the cert-folded key
            // decrypts the embedded blob (a wrong cert would scatter).
            Log.i(TAG, "[native] PHASE1D_VERIFY "
                    + (registrySelfTest && hasGateway && sCertBound ? "PASS" : "FAIL"));
            boolean configReady = com.ghost.assist.core.GuardRuntime.isConfigReady();
            Log.i(TAG, "[native] configReady=" + configReady
                    + " summary=" + com.ghost.assist.core.GuardRuntime.getActiveRegistrySummary());
            // Phase 1E/P_SEC1: GuardRuntime → EncryptedConfigLoader →
            // NativeBridge.getRecipe channel works and is fail-closed on miss.
            String convAdapter = com.ghost.assist.core.GuardRuntime.getRecipe("conv.list", "adapter_class");
            String searchGw = com.ghost.assist.core.GuardRuntime.getRecipe("search.gateway", "gateway");
            String missEntry = com.ghost.assist.core.GuardRuntime.getRecipe("no.such.gateway", "adapter_class");
            String missField = com.ghost.assist.core.GuardRuntime.getRecipe("conv.list", "no_such_field");
            boolean recipeOk = "kc5.v0".equals(convAdapter)
                    && "fts_result_view".equals(searchGw)
                    && missEntry.isEmpty() && missField.isEmpty();
            Log.i(TAG, "[native] recipeGet conv.list/adapter_class=" + convAdapter);
            Log.i(TAG, "[native] PHASE1E_VERIFY "
                    + (recipeOk && configReady && sCertBound ? "PASS" : "FAIL"));
        } catch (Throwable t) {
            Log.e(TAG, "[native] verification crash: " + t);
        }
    }

}
