package com.ghost.assist.core;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.util.Log;

import com.ghost.assist.BuildConfig;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * A2SignatureSpoof — Phase A2 防封签名轴 + android_id 轴。
 *
 * 中间程序「掐 getPackageInfo / Settings.Secure 咽喉、灌官方值」：
 *   • 签名轴：afterHook 自身包 getPackageInfo，把 signatures[] + signingInfo 喂成官方 DER。
 *   • android_id 轴：afterHook Settings.Secure.getString(_, "android_id")，喂官方签名本机 SSAID，
 *     与签名轴连带保「签名↔android_id」自洽（对齐 recon/A2_FEED_DIFF L1 已验证的 dimcollect 路径）。
 * 让官方包自检读到熟料（官方态）→ 数据读出来尽量是官方正常态 = 保号（官方怎么判我方读不到、不预测）。
 *
 * 边界（设计稿 §3/§7 红线）：
 *   • 只动自身包 BuildConfig.GUARD_WX_PKG 的返回；他包原样返回（红线#1）。
 *   • 不全局 hook Signature.toByteArray / getPackageName（红线#2）。
 *   • 官方 DER = 本地常量 OFFICIAL_DER_HEX（公开值；Route B/D-018，不再走加密
 *     registry）；hex 解不出（不应发生）→ 不装 hook（防御性兜底）。
 *   • 零环境读取（不读 ro.boot.*、不枚举包）（红线#3）。
 *   • 独立新类，不进任何已验证隐私 hook 回调体（红线#5）。
 *
 * 安装：由 ModuleMain 在 AntiBanGate.isAntiBanReady(ctx, modulePath) 为 true 时
 * 调用（Route B：闸 = 本地模块证书完整性）。实现对齐研究线已 L1 验证的 dimcollect
 * SPOOF 路径（spoofSigningInfo / replaceSignatureArrays），不另发明。
 */
public final class A2SignatureSpoof {

    private static final String TAG = "NCL";
    private static final int GET_SIGNATURES = 0x40;
    private static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    private static final String KEY_ANDROID_ID = "android_id";

    /**
     * 官方签名在本机派生的 SSAID（android_id）。A2-3 android_id 轴与签名轴连带喂，保
     * 「签名↔android_id」自洽（喂官方签名对应的 SSAID，而非重打包 DER 对应的那个）。
     *
     * 算法已静态 + 动态验证（已证，非推测）：
     *   • 静态 L1：防封线 证据/SSAID_ANDROID_ID_ALGO_20260619.md ——
     *     SSAID = HMAC-SHA256(user_key, BE32(len)||官方DER)[:8]，本机自校 MATCH=True。
     *   • 动态 L1：recon/A2_FEED_DIFF_KPI_实验_20260625.md —— 喂此值 → 检测大血管
     *     c$p.ad/aa/ea 全读官方、0 残漏、KPI 零增量（真机 8.0.71）。
     *   ⇒ 此常量 = 已验证算法算出的官方 SSAID，直接喂。
     *
     * 每机一值（算法性质，非不确定）：user_key 每机随机、root-only；此常量是参考机
     * (MI9) 的官方 SSAID。换机需用「那台机的官方 SSAID」（root 机 ssaid_calc.py 现算入库），
     * 不能把此常量当全局值硬喂给别的机器。
     */
    private static final String OFFICIAL_SSAID = "05f894e8e1e260fa";

    // A2-x 借官方眼睛（弱信号）：官方自身在 c$p.aa 链路用 getPackageInfo 枚举 root/RE/hook 工具包
    // （防封权威账 §九；L1 实捕见 pkgname_scan / dimcollect 日志）。本 hook 本就在官方那次调用里，
    // afterHook 命中「他包 + 已装」时折一个弱信号位回传服务器（非封因，仅服务器侧弱权重、供反破解可疑判断）。
    // 我方不发起任何枚举（守红线#3）、零 ro.boot（守#5）、零 native（守铁律23）。
    // 包名只存 SHA-256[:16] 哈希、不写明文/敏感词（兼反逆向）；hash→包名 映射见下方注释（注释不进 APK 字符串）。
    //
    // 位语义（bitmask，服务器按位判类别）：
    //   0x1 = root/提权框架   0x2 = RE/重打包工具   0x4 = hook 框架(xposed/lsposed)
    //   0x8 = RE/root 工具已注册无障碍服务（更强可疑信号，来自 accessibility 借用点）
    private static volatile int sBorrowedEnv = 0;

    // A16 验证日志限流（DEBUG-only）：新/老 getPackageInfo 重载各首次灌值时打一行，
    // 供无 root A16 真机 adb logcat 确认「微信真走新重载读签名 + 已灌官方」。release R8 剔除。
    private static volatile boolean sLoggedOldOv = false;
    private static volatile boolean sLoggedNewOv = false;
    static final int BIT_ROOT = 0x1;
    static final int BIT_RE   = 0x2;
    static final int BIT_HOOK = 0x4;
    static final int BIT_ACC  = 0x8;
    private static final String KEY_ACCESSIBILITY = "enabled_accessibility_services";
    private static final String[] BORROW_HASH = {
            // root/提权框架 (0x1)
            "1d61da52b0cccbc4", // com.topjohnwu.magisk
            "1df24c805ec076c7", // io.github.huskydg.magisk
            "bf49dde2b81210bd", // 提权变体（历史 L1 备哈希，包名未定档）
            "33705e23421ece9d", // me.weishu.kernelsu
            "696f99689b5fe175", // eu.chainfire.supersu
            "2fd3717b78f7cc3b", // com.kingroot.kinguser
            "6bedf66f89f1faaa", // com.koushikdutta.superuser
            "fc2ce238c4486b9c", // com.noshufou.android.su
            "9c25d388e1e4bea8", // com.thirdparty.superuser
            "b9406bc1c0aa1ce1", // com.kingouser.com
            // RE/重打包工具 (0x2)
            "74e305e64c319375", // bin.mt.plus
            "fa89392171505ca5", // bin.mt.plus.canary
            // hook 框架 (0x4)
            "69cbef8a3e1f331b", // de.robv.android.xposed.installer
            "bffb93b728ef5d74", // org.lsposed.manager
            "c1a66ed4de5cb4ce", // io.github.lsposed.manager
            "8b554a8c940eb796", // org.meowcat.edxposed.manager
            "241adecc432bca8b", // com.solohsu.android.edxp.manager
    };
    private static final int[] BORROW_BIT = {
            BIT_ROOT, BIT_ROOT, BIT_ROOT, BIT_ROOT, BIT_ROOT,
            BIT_ROOT, BIT_ROOT, BIT_ROOT, BIT_ROOT, BIT_ROOT,
            BIT_RE, BIT_RE,
            BIT_HOOK, BIT_HOOK, BIT_HOOK, BIT_HOOK, BIT_HOOK,
    };

    /** 「借官方眼睛」弱信号位（0=未观测到）；服务器作弱权重、非封因。回传走 EnvelopeClient `re`。 */
    public static int getBorrowedEnvSignal() { return sBorrowedEnv; }

    private A2SignatureSpoof() {}

    /**
     * Install the signature axis + android_id axis spoofs. The caller
     * (ModuleMain) MUST gate this with AntiBanGate.isAntiBanReady(ctx,
     * modulePath) (Route B: local module-cert integrity) and MUST pre-heat
     * AuthManager.rawAndroidId (step 0b) BEFORE this call, so the android_id
     * hook never poisons our own device material. The official DER is a local
     * constant (OFFICIAL_DER_HEX, a public value); the null guard below is
     * purely defensive (a malformed hex would skip install rather than crash).
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        final byte[] der = resolveOfficialDer();
        if (der == null) {
            Log.w(TAG, "[A2SIG] skip install: official DER unavailable (scatter/fail-closed)");
            return;
        }
        final String self = BuildConfig.GUARD_WX_PKG;
        // 共用 afterHook 体：被动观测「借官方眼睛」+ 灌官方签名（只动自身包，红线#1）。
        final XC_MethodHook sigHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    observeBorrowed(param, self);   // 借官方眼睛：被动观测，绝不改他包返回
                } catch (Throwable ignored) {
                }
                try {
                    feedOfficial(param, self, der);
                } catch (Throwable ignored) {
                    // 单次失败不影响官方包本体（铁律 19/25）
                }
            }
        };
        // 减法（2026-07-08 A11 栈追踪 L1，官方原版 8.0.71）：c$p.ad/aa/af + t8.c0 + oy5.d + bu5.a.a
        // 读自身签名的对象**唯一源** = getPackageInfo(String,int)；getPackageInfoAsUser 只是 framework
        // 内部转调（栈实证无一条独立调用）、getPackageArchiveInfo / getInstalledPackages 零命中 → hook
        // AsUser 纯冗余、只增暴露面（守红线#2 精神），不做加法。只保两入口：
        //   ① getPackageInfo(String,int)   —— A11 唯一源、老 hook 已验证覆盖 c$p（不动语义，铁律29）
        //   ② getPackageInfo(String,PackageInfoFlags)（A13+）—— A16 唯一真缺口候选（本机 A11 无此重载→计0）
        // ⚠️ 防破解命门（禁改）：绝不 hook getPackageArchiveInfo / getInstalledPackages —— 防破解读点
        //   CompatProbe.certOfModule + ModuleMain.bindSigningCert 走 getPackageArchiveInfo 读【真实签名】
        //   算 registry key / 抓重签；灌了官方值 → 盗版重签也算出真 key → 散沙失效、反白嫖破功。
        int installed = 0;
        installed += hookSig(lpparam, "getPackageInfo",
                new Object[]{ String.class, int.class }, sigHook);
        installed += hookSig(lpparam, "getPackageInfo",
                new Object[]{ String.class, "android.content.pm.PackageManager$PackageInfoFlags" }, sigHook);
        Log.i(TAG, "[A2SIG] installed sig hooks=" + installed + "/2 (self=" + self
                + ", der=" + der.length + "B)");

        // A2-3 android_id 轴：afterHook Settings.Secure.getString(_, "android_id")，把返回喂成
        // 官方签名本机 SSAID，让检测大血管 c$p.aa/ea 读到「签名↔android_id」自洽的官方态。
        // A2 同源隔离：这里只改「问系统要 android_id」的返回；我方设备材料仍走
        // AuthManager.rawAndroidId 的独立读点（ContentResolver 直读，不经此 hook），不被污染。
        try {
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings$Secure", lpparam.classLoader,
                    "getString", "android.content.ContentResolver", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                observeAccessibility(param);   // 借官方眼睛：被动读微信自己拿的无障碍列表
                            } catch (Throwable ignored) {
                            }
                            try {
                                feedOfficialSsaid(param);
                            } catch (Throwable ignored) {
                                // 单次失败不影响官方包本体（铁律 19/25）
                            }
                        }
                    });
            Log.i(TAG, "[A2SIG] android_id axis installed");
        } catch (Throwable t) {
            Log.w(TAG, "[A2SIG] android_id hook fail: " + t.getClass().getSimpleName());
        }
    }

    /**
     * afterHook body: when {@code Settings.Secure.getString(_, "android_id")} was
     * requested, replace the result with the official-signature SSAID so the
     * signature axis and the android_id axis stay consistent (mirrors the
     * L1-verified dimcollect K_SSEC path). Only touches the {@code android_id}
     * key; every other Settings key returns unchanged.
     */
    private static void feedOfficialSsaid(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length < 2) return;
        Object keyArg = param.args[1];
        if (keyArg == null || !KEY_ANDROID_ID.equals(keyArg.toString())) return;
        // 停喂：非 root 无法逐机算出本机官方 SSAID，全局硬喂参考机(MI9)值会让所有客户
        // 上报同一 android_id（撞车）。留自然值（系统按本包签名派生、每机唯一且稳定；
        // 服务器无 user_key 无法核真伪、只看一致）。root 机需精确值时改由 ssaid_calc 逐机喂。
        // param.setResult(OFFICIAL_SSAID);
    }

    /**
     * afterHook body: when our own package's signatures were requested, replace
     * both the signatures[] (GET_SIGNATURES path, c$p reads this) and the
     * signingInfo (GET_SIGNING_CERTIFICATES path, Flutter bu5.a.a reads this —
     * omitting it leaves a NON-OFFICIAL residue, see recon A2_FEED_DIFF).
     */
    private static void feedOfficial(XC_MethodHook.MethodHookParam param, String self, byte[] der) {
        if (param.args == null || param.args.length < 2) return;
        Object pkgArg = param.args[0];
        if (pkgArg == null || !self.equals(pkgArg.toString())) return;   // 只动自身包
        long flags = extractFlags(param.args[1]);
        boolean wantSig = (flags & GET_SIGNATURES) != 0
                || (flags & GET_SIGNING_CERTIFICATES) != 0;
        if (!wantSig) return;
        Object r = param.getResult();
        if (!(r instanceof PackageInfo)) return;
        PackageInfo pi = (PackageInfo) r;
        Signature official = new Signature(der);
        if (pi.signatures != null && pi.signatures.length > 0) {
            pi.signatures = new Signature[]{ official };
        }
        if (pi.signingInfo != null) {
            spoofSigningInfo(pi.signingInfo, official);
        }
        // A16 无 root 验证锚点（DEBUG-only，新/老重载各首次一行，不刷屏）：arg[1] 是 int =
        // 老重载 getPackageInfo(String,int)；是 PackageInfoFlags 对象 = A13+ 新重载。A16 上若见
        // NEW-overload 行 = 微信真经新重载读签名且已灌官方（A11 只会出 OLD-overload）。
        if (BuildConfig.DEBUG) {
            boolean isNew = !(param.args[1] instanceof Integer);
            if (isNew && !sLoggedNewOv) {
                sLoggedNewOv = true;
                Log.i(TAG, "[A2SIG] fed OFFICIAL via NEW-overload(PackageInfoFlags) flags=0x"
                        + Long.toHexString(flags) + " (A13+/A16 走新重载读签名, 已灌官方)");
            } else if (!isNew && !sLoggedOldOv) {
                sLoggedOldOv = true;
                Log.i(TAG, "[A2SIG] fed OFFICIAL via OLD-overload(String,int) flags=0x"
                        + Long.toHexString(flags));
            }
        }
    }

    /**
     * Hook one getPackageInfo overload with the shared signature-feeding
     * afterHook. Uses the XposedHelpers varargs form (parameter types may be
     * Class or a String class-name resolved via classLoader). Returns 1 on
     * success, 0 when the overload is absent on this Android version — A11 has
     * no PackageInfoFlags overload, so a missing one must be swallowed (铁律25),
     * never crash init.
     */
    private static int hookSig(XC_LoadPackage.LoadPackageParam lpparam,
                               String method, Object[] sig, XC_MethodHook hook) {
        try {
            Object[] params = new Object[sig.length + 1];
            System.arraycopy(sig, 0, params, 0, sig.length);
            params[sig.length] = hook;
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", lpparam.classLoader, method, params);
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Extract the flags value from arg[1]. Legacy overloads pass an int; the
     * A13+ overloads pass a {@code PackageManager.PackageInfoFlags} object whose
     * {@code getValue()} returns the long flags. Reflect getValue() so we cover
     * the new overloads without a compile-time A13 SDK dependency.
     */
    private static long extractFlags(Object a) {
        if (a instanceof Integer) return ((Integer) a).longValue();
        if (a == null) return 0L;
        try {
            Object v = a.getClass().getMethod("getValue").invoke(a);
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    /**
     * 官方签名 DER（公开值：官方客户端证书，谁都能从官方包抽取）。Route B / D-018：
     * 本地常量化，A2 不再依赖 server seed 解 registry —— 防封惠及所有未被重签的副本。
     * len=751B / md5=18c867f0717aa67b2ab7347505ba07ed（与 registry_8071.json a2.sig
     * / 研究线 dimcollect baseline 同源，落码时已校验）。红线7 经用户 2026-06-25 放宽
     * （私库 + 公开值），故允许本地明文常量。
     */
    private static final String OFFICIAL_DER_HEX =
            "308202eb30820254a00302010202044d36f7a4300d06092a864886f70d01010505003081b9310b300906035504061302383631123010060355040813094775616e67646f6e673111300f060355040713085368656e7a68656e31353033060355040a132c54656e63656e7420546563686e6f6c6f6779285368656e7a68656e2920436f6d70616e79204c696d69746564313a3038060355040b133154656e63656e74204775616e677a686f7520526573656172636820616e6420446576656c6f706d656e742043656e7465723110300e0603550403130754656e63656e74301e170d3131303131393134333933325a170d3431303131313134333933325a3081b9310b300906035504061302383631123010060355040813094775616e67646f6e673111300f060355040713085368656e7a68656e31353033060355040a132c54656e63656e7420546563686e6f6c6f6779285368656e7a68656e2920436f6d70616e79204c696d69746564313a3038060355040b133154656e63656e74204775616e677a686f7520526573656172636820616e6420446576656c6f706d656e742043656e7465723110300e0603550403130754656e63656e7430819f300d06092a864886f70d010101050003818d0030818902818100c05f34b231b083fb1323670bfbe7bdab40c0c0a6efc87ef2072a1ff0d60cc67c8edb0d0847f210bea6cbfaa241be70c86daf56be08b723c859e52428a064555d80db448cdcacc1aea2501eba06f8bad12a4fa49d85cacd7abeb68945a5cb5e061629b52e3254c373550ee4e40cb7c8ae6f7a8151ccd8df582d446f39ae0c5e930203010001300d06092a864886f70d0101050500038181009c8d9d7f2f908c42081b4c764c377109a8b2c70582422125ce545842d5f520aea69550b6bd8bfd94e987b75a3077eb04ad341f481aac266e89d3864456e69fba13df018acdc168b9a19dfd7ad9d9cc6f6ace57c746515f71234df3a053e33ba93ece5cd0fc15f3e389a3f365588a9fcb439e069d3629cd7732a13fff7b891499";

    private static byte[] resolveOfficialDer() {
        return hexToBytes(OFFICIAL_DER_HEX);
    }

    /**
     * Cover the GET_SIGNING_CERTIFICATES path: replace every Signature[] field
     * inside the SigningInfo (the inner field name shifts across Android
     * versions, so reflect over all declared fields). Mirrors the L1-verified
     * dimcollect implementation.
     */
    private static void spoofSigningInfo(Object signingInfo, Signature official) {
        try {
            Signature[] off = new Signature[]{ official };
            Object details = getField(signingInfo, "mSigningDetails");
            replaceSignatureArrays(details != null ? details : signingInfo, off);
        } catch (Throwable ignored) {
        }
    }

    private static void replaceSignatureArrays(Object obj, Signature[] off) {
        if (obj == null) return;
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.getType() == Signature[].class) {
                try {
                    f.setAccessible(true);
                    f.set(obj, off.clone());
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static Object getField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 借官方眼睛（被动观测）：绝不修改他包返回（守红线#1），不发起任何枚举（守红线#3）。
     * 官方自身在 c$p.aa 链路调 getPackageInfo 查 RE/提权工具包（防封权威账 §169）；本 hook 本就
     * 在官方那次调用里，afterHook 命中「他包 + result 非空(=该包已装)」且哈希匹配时折一个弱信号位。
     * 零 ro.boot、零 native、零我方枚举；提权/环境为非封因，仅供服务器侧弱权重。
     */
    private static void observeBorrowed(XC_MethodHook.MethodHookParam param, String self) {
        if (param.args == null || param.args.length < 1) return;
        Object pkgArg = param.args[0];
        if (pkgArg == null) return;
        String pkg = pkgArg.toString();
        if (self.equals(pkg)) return;                              // 自身包不算
        if (!(param.getResult() instanceof PackageInfo)) return;   // result 非空 = 该他包已装
        String h = sha256Prefix16(pkg);
        if (h == null) return;
        for (int i = 0; i < BORROW_HASH.length; i++) {
            if (BORROW_HASH[i].equals(h)) {
                foldSignal(BORROW_BIT[i]);
                return;
            }
        }
    }

    /** 折信号位；仅在「新位首次置起」时打一行低频日志（供 L1 验证；不含明文包名）。 */
    private static void foldSignal(int bit) {
        if ((sBorrowedEnv & bit) == bit) return;   // 已置过 → 不重复
        sBorrowedEnv |= bit;
        Log.i(TAG, "[A2SIG] borrowed env=0x" + Integer.toHexString(sBorrowedEnv));
    }

    /**
     * 借官方眼睛（无障碍轴，被动观测）：微信自身在 c$p 链路读 enabled_accessibility_services
     * （L1：dimcollect 实捕，含 bin.mt.plus 等 RE 工具注册无障碍）。本 hook 本就在官方那次
     * Settings.Secure.getString 调用里，afterHook 解析微信已拿到的列表值，命中 root/RE/hook
     * 包时折「该类别位 | BIT_ACC」。绝不修改返回（只读官方拿到的值）、不发起任何我方读取。
     * 值形如 "pkg/serviceA:pkg2/serviceB"；只按包名哈希匹配，不写明文包名。
     */
    private static void observeAccessibility(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length < 2) return;
        Object keyArg = param.args[1];
        if (keyArg == null || !KEY_ACCESSIBILITY.equals(keyArg.toString())) return;
        Object r = param.getResult();
        if (!(r instanceof String)) return;
        String v = (String) r;
        if (v.isEmpty()) return;
        for (String comp : v.split(":")) {
            int slash = comp.indexOf('/');
            String pkg = slash > 0 ? comp.substring(0, slash) : comp;
            String h = sha256Prefix16(pkg);
            if (h == null) continue;
            for (int i = 0; i < BORROW_HASH.length; i++) {
                if (BORROW_HASH[i].equals(h)) {
                    foldSignal(BORROW_BIT[i] | BIT_ACC);
                    break;
                }
            }
        }
    }

    private static String sha256Prefix16(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xff));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] hexToBytes(String s) {
        if (s == null) return null;
        int len = s.length();
        if (len == 0 || (len & 1) != 0) return null;
        try {
            byte[] b = new byte[len / 2];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
