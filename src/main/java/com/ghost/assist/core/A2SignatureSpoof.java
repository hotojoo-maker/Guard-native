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
 *   • 官方 DER 取自加密 registry（getRecipe a2.sig/official_der）；料解不出
 *     → 不装 hook（fail-closed，红线#4）；release 无明文 DER。
 *   • 零环境读取（不读 ro.boot.*、不枚举包）（红线#3）。
 *   • 独立新类，不进任何已验证隐私 hook 回调体（红线#5）。
 *
 * 安装：由 ModuleMain 在 EnvelopeStore/registry 就绪后、且
 * GuardRuntime.isAntiBanReady() 为 true 时调用（设计稿 §3/§5）。实现对齐研究线
 * 已 L1 验证的 dimcollect SPOOF 路径（spoofSigningInfo / replaceSignatureArrays），
 * 不另发明。
 */
public final class A2SignatureSpoof {

    private static final String TAG = "NCL";
    private static final int GET_SIGNATURES = 0x40;
    private static final int GET_SIGNING_CERTIFICATES = 0x08000000;

    private A2SignatureSpoof() {}

    /**
     * Install the signature-axis spoof. The caller (ModuleMain) MUST gate this
     * with GuardRuntime.isAntiBanReady(); we still re-resolve the official DER
     * from the encrypted registry here and bail (fail-closed) when it is
     * unavailable — pirated builds without a server seed scatter the DER, so the
     * hook is never installed.
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

    private static byte[] resolveOfficialDer() {
        String hex = GuardRuntime.getRecipe(
                GuardRuntime.A2_SIG_GATEWAY, GuardRuntime.A2_SIG_OFFICIAL_DER);
        return hexToBytes(hex);
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
