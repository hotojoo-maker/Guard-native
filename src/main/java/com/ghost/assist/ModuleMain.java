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
import com.ghost.assist.net.EnvelopeStore;
import com.ghost.assist.net.GuardHeartbeat;

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
    private static final String WX_PKG = BuildConfig.GUARD_WX_PKG;
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
     * 段1：前台触发引流弹窗。hook 主进程任意 Activity.onResume，回前台时调唯一
     * 弹窗出口 RiskPromptController.maybeShow(activity)。
     *
     * 为什么 hook onResume 而不是只靠 cold-start：
     *   • cold-start（Application init）阶段没有 Activity，application context 画
     *     AlertDialog 会 token null 失败；onResume 提供有效 Activity context。
     *   • 顺带满足用户「关掉后回前台又弹」：每次回前台都 maybeShow，30s 冷却内不重复。
     * 开销可忽略：maybeShow 先查 RiskState 等级，非 funnel 直接 return（绝大多数情况）。
     * 铁律 25：findAndHookMethod 必须 catch(Throwable)。
     */
    private void installForegroundFunnelTrigger(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity", lpparam.classLoader, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                android.app.Activity act = (android.app.Activity) param.thisObject;
                                RiskPromptController.maybeShow(act, "foreground");
                            } catch (Throwable ignored) {
                                // 单个 Activity 异常不影响微信本体（铁律 25/19）
                            }
                        }
                    });
            Log.i(TAG, "[funnel] foreground trigger installed");
        } catch (Throwable t) {
            Log.w(TAG, "[funnel] fg trigger install fail: " + t.getClass().getSimpleName());
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

        // 0b. Batch 1 (牙③ W_dev): push per-device material D_mat = SHA-256(ANDROID_ID)
        //     to the SO BEFORE the seed is unwrapped (step 2), so unwrap_server_seed can
        //     try the per-device wrap key. null (android_id missing) → not set → the
        //     double-try falls back to global W (no block). A2 同源隔离: only reads
        //     AuthManager.rawAndroidId, never the official SSAID A2 feeds the host.
        try {
            byte[] dmat = com.ghost.assist.core.AuthManager.computeDeviceMaterial(app);
            if (dmat != null) NativeBridge.setDeviceMaterial(dmat);
        } catch (Throwable t) {
            Log.w(TAG, "[init] setDeviceMaterial skip: " + t.getClass().getSimpleName());
        }

        // 0. NativeBridge — Batch 1 Phase 1 verification (before any hook registration)
        //    Iron rule 27: nativeInit must complete before business hooks.
        runNativeBridgeVerification(lpparam.processName);

        // 1. Init config (DEV/PROD/HONEY)
        AppConfig.getInstance().init(app);

        // 2. Init MMKV bridge
        Bridge.getInstance().init(app);
        EnvelopeStore.init(app);
        boolean cachedSeedOk = EnvelopeStore.applyCachedEnvelopeSeed();
        Log.i(TAG, "[hb] cached envelope seed=" + (cachedSeedOk ? "ok" : "miss"));
        startAuthHeartbeatIfNeeded(app);

        // 3. Init state machine
        StateMachine.getInstance().init(app);

        // 4. Init intercept counter
        InterceptCounter.getInstance().init();

        // 5. 「整线停用」走服务器 envelope 错误码 RELEASE_KILLED（EnvelopeClient
        //    authErrorText → "该版本已停用"），不再走本地 kill_switch stub
        //    (原 v1 placeholder, 永远 false 的孤儿代码已删, 文档同步)。
        //    单卡封停走 envelope.cardRevoked (rf=1, SPEC §4)。

        // 6. Restore state from persistence
        StateMachine.getInstance().restoreState();

        // 6.5. 引流闸（funnel）= P4-1 + P1F: auth evaluate (wxid + device) → RiskState (唯一风险出口)
        //      → RiskPromptController (唯一弹窗)。
        //      v1: RECORD ONLY, NO gating — NO_LICENSE / MISMATCH still pass
        //      (GUARD_GATE_TRUTH §4)。RiskGate 是与 isActive() 三层【并联】的第四道门，
        //      本段只「评估 + 记录 + 决定弹不弹」，绝不改 isActive、不关功能、不清数据。
        //      弹窗只在 RiskState.shouldFunnel()（确认篡改超影子期）时由 RiskPromptController 决定。
        try {
            int authResult = AuthManager.evaluate(app);
            NativeBridge.setAuthState(authResult);
            Log.i(TAG, "[auth] evaluate=" + authResult + " (v1 record-only, not gating)");
            com.ghost.assist.core.CompatProbe.check(app);  // 段2 蜜罐绊线：诱饵被改→markTampered→影子期
            com.ghost.assist.core.CompatProbe.checkSignature(app, hostApkPath(app));  // 段2 签名绊线：重签→markTampered→影子期 (2026-06-30 改读宿主整包, 详见 bindSigningCert 注释)
            RiskState.Level riskLevel = RiskState.evaluate(app);
            Log.i(TAG, "[risk] level=" + riskLevel.label + " (v1 record-only, not gating)");
            RiskPromptController.maybeShow(app, "cold-start");
        } catch (Throwable t) {
            Log.e(TAG, "[auth] wire crash: " + t);
        }

        // 6.55. 官方对时第二源（D-020）：借官方包 hd.b()（抗改表 · L1 2026-06-27）喂 LeaseClock，
        //       抬可信时间水位 + 记首装 72h 起算锚。observe-only / 纯读 / 不注入 JNI（铁律23）/
        //       零新增检测面（铁律5）。异常未来值（>可信+2年）= 时间被 hook 铁证 → markTampered（D-019）。
        //       必须在 A2 闸（§6.6/§6.7）判定前，让时间闸用上最新官方授时基准。
        try {
            long officialMs = com.ghost.assist.core.OfficialClock.readOfficialNowMs(app.getClassLoader());
            if (com.ghost.assist.core.LeaseClock.noteOfficialTime(officialMs)) {
                RiskState.markTampered(app);   // 官方授时异常未来值 → D-019 不可逆影子期
                Log.w(TAG, "[oclk] official time abnormal future → markTampered");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[oclk] note official time skip: " + t.getClass().getSimpleName());
        }

        // 6.6. A2 防封授权闸（isAntiBanReady）self-test — DEBUG-only。
        //      Route B/D-020：闸 = 本地模块证书完整性 + 时间闸（首装72h/失效7天，fail-open）；
        //      只读闸出口 + 各子信号（ANTIBAN-GATE tag），不门控隐私、不改 isActive。
        if (AppConfig.isDevBuild()) {   // 2026-06-29 debug-gate-unify：BuildConfig.DEBUG 归一到 AppConfig 三门面
            com.ghost.assist.core.AntiBanGate.antiBanGateSelfTest(TAG, app, hostApkPath(app));   // 2026-06-30 改读宿主整包；AntiBanGate 拆类(2026-06-29)
            com.ghost.assist.core.AntiBanGate.antiBanBranchSelfTest(TAG);   // D-020 时间闸全分支纯函数自测（②新装/④封停超时）
        }

        // 6.7. A2 防封签名轴安装（Route B / D-020）：门控 = 本地 cert 完整性 + 时间闸。
        //      isAntiBanReady(app, hostApkPath(app))——cert 完整 + 在时间窗内（授权中 / 首装72h内 / 失效7天内 /
        //      封停72h内 / 官方授时无值 fail-open）即装、保号；重签散沙、超窗撤。逆序线 fail-open（拿不准=装）。
        //      A2 是独立加法，只动自身包签名返回，不连坐隐私 isActive()、不进已验证 hook。
        try {
            if (com.ghost.assist.core.AntiBanGate.isAntiBanReady(app, hostApkPath(app))) {   // 2026-06-30 改读宿主整包；AntiBanGate 拆类(2026-06-29)
                com.ghost.assist.core.A2SignatureSpoof.install(lpparam);
                com.ghost.assist.core.A2PkgPathSpoof.install(lpparam);   // A2 包名/路径轴（仅共存，internal self!=官方包名 判定，官替自动跳过）
            } else {
                Log.i(TAG, "[A2SIG] not installed: cert mismatch (re-signed → scatter)");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[A2SIG] gate/install crash: " + t.getClass().getSimpleName());
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
        com.ghost.assist.moduleD.SelectContactFilter.install(lpparam, app.getClassLoader()); // P_SelectFilter 发圈选人器隐私过滤（hook h0.s 注入 a5.m 排除密友/密群）
        // 微信 8.0.71 带 Tinker 热补丁：运行时 UI/插件类由 app 的 DelegateLastClassLoader 加载，
        // 与 lpparam.classLoader（base.apk）不是同一份。hook 这类类必须用 app.getClassLoader()。
        PushFilter.install(lpparam, app.getClassLoader());

        // P21: moments red-dot guard. Main path is verified; keep installed without
        // changing the validated D1/D2/D3 moments filters.
        MomentsRedDotGuard.install(lpparam);
        // M6a: 隐藏自己受限帖子的「可见分组」图标（app:id/pt），独立于密友过滤链
        com.ghost.assist.moduleD.MomentsGroupIconFilter.install(lpparam);
        // 隐藏微信「设置」页「存储空间」入口行（授权 + 隐身态；只读 StateMachine）
        com.ghost.assist.moduleD.SettingsStorageHideGuard.install(lpparam);
        UpdateGuard.install(lpparam);
        // 官方热更新通道（libcso/Tinker/整包更新）。Tinker 类走 app classloader（DelegateLastClassLoader）。
        // D-033（2026-07-05）：远程自动热更新(Tinker)默认放行(observe)；整包/手动点「检查更新」仍默认冻结。
        com.ghost.assist.moduleB.HotUpdateFreeze.install(lpparam, app.getClassLoader());
        installForegroundFunnelTrigger(lpparam);  // 段1: 前台(onResume)触发引流弹窗（唯一出口 RiskPromptController）
        TriggerGuard.install(app);  // B1/B2/B5，Android API，不吃 lpparam
        com.ghost.assist.moduleD.ContactDiscoveryHook.install(app); // P_CV1 V1：动态发现通讯录 LiveList/Adapter

        // E2 伪装订位 — 全局伪造定位（pz0.h 是 tinker 运行时类 → 必须用 app classloader）
        com.ghost.assist.moduleE.FakeLocation.install(lpparam, app.getClassLoader());

        // E3 改余额 — 全局伪造钱包/零钱余额显示（wallet_core/kinda 是 tinker 运行时类 → app classloader）
        com.ghost.assist.moduleE.FakeBalance.install(lpparam, app.getClassLoader());

        // 设置入口 — 微信「我→设置」顶部注入"密友设置 ›"行（仅 VISIBLE 态可见）
        SettingsEntry.install(lpparam);

        // 8a. HTTP debug server — DEBUG build always; RELEASE only in HONEY mode.
        //     RELEASE + PROD (customer build) → never starts, so /api/hidden etc. are not exposed.
        if (AppConfig.isDebugSurface()) {
            DebugServer.start();
            Log.i(TAG, "[init] debug server started port=" + AppConfig.getInstance().getServerPort());
        } else {
            Log.i(TAG, "[init] debug server skipped (release+prod)");
        }

        // 8b. UiContextTracker must run in PROD too — SearchUnlock/SearchFilter use
        //     getCurrentActivity() to finish() the search page after unlock.
        UiContextTracker.install(lpparam);

        // 8b2. B 装后预注册：设备画像 best-effort 上报一次（免授权，看「装了没激活」漏斗）。
        //      仅 MAIN 进程（本方法天然隔离 :push）；后台线程；dr 标志焊死（至少一次·最终一致）。
        reportDeviceCheckinIfNeeded(app);

        // 8c. UI debug tools (overlay + notification) only in DEV/HONEY.
        if (AppConfig.isDiagnostics()) {
            StatusNotification.show(app);
            OverlayWindow.attach(app);
            Log.i(TAG, "[init] debug UI tools started");
        }

        Log.i(TAG, "[init] ready — state=" + StateMachine.getInstance().getStateName());
    }

    /**
     * B 装后预注册：设备画像 checkin「只发一次」。
     *
     * 逻辑（对齐用户拍板「首次冷启发、传成焊死、没传成下次冷启再试」）：
     *   • 已成功上报过（EnvelopeStore.dr）→ 直接跳过。
     *   • 否则后台线程 best-effort checkin；成功 → markDeviceReported()（此后永不再发）；
     *     失败（没网 / 发一半被杀 / 服务器未接口）→ dr 仍空 → 下次微信冷启重试。
     * 采集不依赖登录（Build.* + android_id 派生），故「打开没登录被杀」也能在下次冷启补上。
     * catch(Throwable) 全兜，绝不崩宿主（铁律 19/25）。
     */
    private void reportDeviceCheckinIfNeeded(Application app) {
        try {
            if (EnvelopeStore.isDeviceReported()) return;
            final String deviceId = AuthManager.computeDeviceHash(app);
            com.ghost.assist.net.EnvelopeClient.runAsync(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (com.ghost.assist.net.EnvelopeClient.checkin(deviceId)) {
                            EnvelopeStore.markDeviceReported();
                            Log.i(TAG, "[checkin] device profile reported (dr set)");
                        } else {
                            Log.i(TAG, "[checkin] device report deferred (retry next cold-start)");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "[checkin] report crash: " + t.getClass().getSimpleName());
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "[checkin] arm skip: " + t.getClass().getSimpleName());
        }
    }

    private void startAuthHeartbeatIfNeeded(Application app) {
        try {
            if (!EnvelopeStore.hasToken()) return;
            String deviceId = AuthManager.computeDeviceHash(app);
            GuardHeartbeat.start(deviceId, "", AppConfig.GUARD_PRODUCT_VERSION);
            Log.i(TAG, "[hb] cold-start heartbeat armed");
        } catch (Throwable t) {
            Log.w(TAG, "[hb] cold-start heartbeat skipped: " + t.getClass().getSimpleName());
        }
    }

    private static volatile boolean sCertBound = false;

    /**
     * Phase 1D-local A-step2 — read this module's own signing-cert SHA-256 and
     * push it into the SO as the registry-key binding material. A re-signed /
     * repackaged APK has a different cert → wrong key → registry scatters.
     * Logs only the first 4 bytes (so a release log doesn't hand out the full
     * bound value).
     */
    /**
     * 宿主整包 APK 路径 = 当前进程包名对应的 sourceDir。
     *
     * 用途: cert binding (NativeBridge.setBindingMaterial) / A2 闸 / 重签蜜罐都用这个,
     * 不再用 sModulePath (LSPatch metaloader 重打包模块时换了 keystore -> 签名漂移)。
     * sModulePath 保留语义不变, 仅 DebugServer 等"模块自验"场景继续用。
     *
     * 同进程查自己包名不受 Android 11+ package visibility 限制 (限制只对查别人的包)。
     */
    private static String hostApkPath(Application app) {
        try {
            return app.getApplicationInfo().sourceDir;
        } catch (Throwable t) {
            return "";
        }
    }

    private void bindSigningCert(Application app) {
        try {
            // 2026-06-30: 改读宿主整包 (sourceDir) 签名, 不再读 sModulePath。
            // 历史读模块路径的设计在 LSPatch 形态下踩坑: LSPatch metaloader 在 extract
            // 模块时会把内嵌的 modules/com.ghost.assist.apk 重打包到 cache/lspatch/...
            // 那个新 APK 用 LSPatch 内置 debug keystore (ca421ec3) 重签, 与发版 release
            // keystore (e3e13a49) 不一致 -> registry 派生 key mismatch -> 散沙。
            // 宿主 sourceDir = LSPatch -k 用的 keystore (我方可控), 与生成 cipher 时使
            // 用的 cert 同源, 跨 Debug/Release/LSPatch 形态都稳。
            // 注: 同进程查自己包名 (app.getPackageName()) 不受 Android 11+ package
            // visibility 限制 (老注释那条限制只针对查别人的包)。
            String path = hostApkPath(app);
            if (path == null || path.isEmpty()) {
                Log.w(TAG, "[native] certBind skipped: no host apk path");
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
                    && testRegistry.contains("test_r8071");
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
                    && scatter.contains("scatter");
            Log.i(TAG, "[native] decryptScatter=" + scatterOk);

            // (12) Phase 1B: registry parse self-test (no business wiring)
            boolean registrySelfTest = NativeBridge.registrySelfTest();
            Log.i(TAG, "[native] registrySelfTest=" + registrySelfTest + " (expect true)");
            Log.i(TAG, "[native] registrySummary=" + NativeBridge.registrySummary());

            // (12b) DEBUG-only KDF vector cross-check (guard::derive_* vs the
            //       Python kdf_common vectors). BuildConfig.DEBUG-gated so the
            //       release build never references nativeKdfSelfTest (which is
            //       compiled out of the release SO) — easy to strip at ship time.
            if (AppConfig.isDevBuild()) {
                Log.i(TAG, "[native] KDF_VECTOR_VERIFY "
                        + (NativeBridge.kdfSelfTest() ? "PASS" : "FAIL"));
            }

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
            boolean recipeOk = !convAdapter.isEmpty()
                    && !searchGw.isEmpty()
                    && missEntry.isEmpty() && missField.isEmpty();
            Log.i(TAG, "[native] recipeGet conv.list/adapter_class=" + convAdapter);
            Log.i(TAG, "[native] PHASE1E_VERIFY "
                    + (recipeOk && configReady && sCertBound ? "PASS" : "FAIL"));
            // C2: cert-only bootstrap endpoint blob decrypts to the AUTH server
            // list (domain hidden in SO; AppConfig reads it via NativeBridge).
            String[] endpoints = com.ghost.assist.core.AppConfig.guardServerList();
            String epPrimary = com.ghost.assist.core.NativeBridge.getEndpoint("primary");
            Log.i(TAG, "[native] bootstrapEndpoints count=" + endpoints.length
                    + " primary=" + epPrimary);
            boolean c2Ok = endpoints.length > 0 && epPrimary.startsWith("https://");
            Log.i(TAG, "[native] C2_VERIFY " + (c2Ok && sCertBound ? "PASS" : "FAIL"));
        } catch (Throwable t) {
            Log.e(TAG, "[native] verification crash: " + t);
        }
    }

}
