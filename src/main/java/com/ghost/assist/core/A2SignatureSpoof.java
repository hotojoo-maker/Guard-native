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
 * A2SignatureSpoof — Phase A2-1 防封签名轴。
 *
 * 中间程序「掐 getPackageInfo 咽喉、灌官方值」：afterHook 自身包
 * getPackageInfo 的签名读取，把 signatures[] + signingInfo 都喂成官方 DER，
 * 让官方包自检读到「官方」→ 我方号不被判非官方 / 账号异常。
 *
 * 边界（设计稿 §3/§7 红线）：
 *   • 只动自身包 BuildConfig.GUARD_WX_PKG 的返回；他包原样返回（红线#1）。
 *   • 不全局 hook Signature.toByteArray / getPackageName（红线#2）。
 *   • 官方 DER = 本地常量 OFFICIAL_DER_HEX（公开值；Route B/D-018，不再走加密
 *     registry）；hex 解不出（不应发生）→ 不装 hook（防御性兜底）。
 *   • 零环境读取（不读 ro.boot.*、不枚举包）（红线#3）。
 *   • 独立新类，不进任何已验证隐私 hook 回调体（红线#5）。
 *
 * 安装：由 ModuleMain 在 GuardRuntime.isAntiBanReady(ctx, modulePath) 为 true 时
 * 调用（Route B：闸 = 本地模块证书完整性）。实现对齐研究线已 L1 验证的 dimcollect
 * SPOOF 路径（spoofSigningInfo / replaceSignatureArrays），不另发明。
 */
public final class A2SignatureSpoof {

    private static final String TAG = "NCL";
    private static final int GET_SIGNATURES = 0x40;
    private static final int GET_SIGNING_CERTIFICATES = 0x08000000;

    // A2-x 借官方眼睛（弱信号）：官方自身在 c$p.aa 链路用 getPackageInfo 查 RE/提权工具包
    // （防封权威账 §169）。本 hook 本就在官方那次调用里，afterHook 命中「他包 + 已装」时折一个
    // 弱信号位回传服务器（非封因，仅服务器侧弱权重）。我方不发起任何枚举（守红线#3）、零 ro.boot
    // （守#5）、零 native（守铁律23）。包名只存 SHA-256[:16] 哈希，不写明文/敏感词（兼反逆向）。
    private static volatile int sBorrowedEnv = 0;
    private static final String[] BORROW_HASH = {
            "1d61da52b0cccbc4", "1df24c805ec076c7", "bf49dde2b81210bd", // 提权框架主线 / 变体
            "74e305e64c319375",                                          // RE 文件工具
    };
    private static final int[] BORROW_BIT = { 0x1, 0x1, 0x1, 0x2 };

    /** 「借官方眼睛」弱信号位（0=未观测到）；服务器作弱权重、非封因。回传走 EnvelopeClient `re`。 */
    public static int getBorrowedEnvSignal() { return sBorrowedEnv; }

    private A2SignatureSpoof() {}

    /**
     * Install the signature-axis spoof. The caller (ModuleMain) MUST gate this
     * with GuardRuntime.isAntiBanReady(ctx, modulePath) (Route B: local
     * module-cert integrity). The official DER is now a local constant
     * (OFFICIAL_DER_HEX, a public value); the null guard below is purely
     * defensive (a malformed hex would skip install rather than crash).
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        final byte[] der = resolveOfficialDer();
        if (der == null) {
            Log.w(TAG, "[A2SIG] skip install: official DER unavailable (scatter/fail-closed)");
            return;
        }
        final String self = BuildConfig.GUARD_WX_PKG;
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", lpparam.classLoader,
                    "getPackageInfo", String.class, int.class,
                    new XC_MethodHook() {
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
                    });
            Log.i(TAG, "[A2SIG] installed (self=" + self + ", der=" + der.length + "B)");
        } catch (Throwable t) {
            Log.w(TAG, "[A2SIG] install fail: " + t.getClass().getSimpleName());
        }
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
        int flags = (param.args[1] instanceof Integer) ? (Integer) param.args[1] : 0;
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
                sBorrowedEnv |= BORROW_BIT[i];
                return;
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
