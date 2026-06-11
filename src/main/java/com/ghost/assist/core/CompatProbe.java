package com.ghost.assist.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.security.MessageDigest;

/**
 * CompatProbe — 启动期兼容性自检（蜜罐绊线，刻意取不起眼的名字、与诱饵 PromoConfig 分开放）。
 *
 * ⚠️ 内部说明（注释不进编译产物）：
 *   段2 蜜罐「绊线检测」。比对诱饵 PromoConfig 当前值与【编译期 canary 基线】：
 *     • 一致 → 没人动过诱饵 → 安静返回。
 *     • 不一致 → 有人改/NOP 了诱饵 → RiskState.markTampered() → 进影子期(10天)
 *       → 之后才由 RiskPromptController 引流弹窗。
 *   设计要点（安全官 §10.8 / 蜜罐三原则）：
 *     • 与诱饵【分开放、名字无关联】，破解者改了诱饵不会顺藤摸到本检测器。
 *     • 不当场翻脸：只 markTampered（记时间戳进影子期），不立即弹/不关功能。
 *     • 本地不自洗白：清缓存/改时间不能消风险态（RiskState 持久化 tamper_first_seen）。
 *   canary 基线由 tools 按 PromoConfig 真值算出；改诱饵必须同步重算（否则正版误报）。
 */
public final class CompatProbe {

    private static final String TAG = "NCL";

    // 编译期算出的诱饵真值指纹。fp(PROMO_URL) ^ fp(PROMO_TOKEN) ^ (enabled?GR:0)。
    // 诱饵任一字面量被改 → 运行时指纹 ≠ 此基线 → 命中篡改。
    private static final int BASELINE = 0xF3C1CAB3;
    private static final int GR = 0x9E3779B9;

    // 预期签名证书 SHA-256（= 固定 keystore signing/guard-native-debug.keystore 的证书，
    // 与 registry 加密绑定的 _CERT_SHA256=ca421ec3... 同源；打包前已知）。重签名 = 不符。
    // 官替/共存若用不同 keystore，发版时按包档案改这里（同 registry 一套机制）。
    private static final String EXPECTED_CERT =
            "ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0";

    private CompatProbe() {}

    /** 冷启动调用一次（ModuleMain §6.5，RiskState.evaluate 之前）。 */
    public static void check(Context ctx) {
        try {
            int now = fp(PromoConfig.PROMO_URL)
                    ^ fp(PromoConfig.PROMO_TOKEN)
                    ^ (PromoConfig.PROMO_ENABLED ? GR : 0);
            if (now != BASELINE) {
                Log.i(TAG, "[cp] baseline mismatch");
                RiskState.markTampered(ctx);   // 进影子期，不当场弹
            }
        } catch (Throwable t) {
            Log.w(TAG, "[cp] probe err: " + t.getClass().getSimpleName());
        }
    }

    private static int fp(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 131 + s.charAt(i);
        return h;
    }

    /**
     * 签名校验绊线（破解者甩不掉：改代码必重签 → 证书 SHA-256 变）。
     * 读【模块自身 APK】的签名证书（modulePath 来自 ModuleMain.initZygote），
     * 与预期证书比对；不符 = 被重签 = 改过代码 → markTampered → 影子期 → 之后散沙+引流。
     * 读不到签名（modulePath 空/异常）→ 不判篡改（避免误报正版）。
     *
     * @param ctx        app context（取 PackageManager）
     * @param modulePath 模块 APK 路径（ModuleMain sModulePath）
     */
    public static void checkSignature(Context ctx, String modulePath) {
        if (ctx == null || modulePath == null || modulePath.isEmpty()) return;
        try {
            PackageManager pm = ctx.getPackageManager();
            @SuppressWarnings("deprecation")
            PackageInfo pi = pm.getPackageArchiveInfo(modulePath, PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null || pi.signatures.length == 0) {
                return;  // 读不到 → 不误报
            }
            byte[] der = pi.signatures[0].toByteArray();
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(der);
            String hex = toHex(dig);
            if (!hex.equalsIgnoreCase(EXPECTED_CERT)) {
                Log.i(TAG, "[cp] cert mismatch");
                RiskState.markTampered(ctx);   // 进影子期，不当场翻脸
            }
        } catch (Throwable t) {
            Log.w(TAG, "[cp] sig probe err: " + t.getClass().getSimpleName());
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16))
                           .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
