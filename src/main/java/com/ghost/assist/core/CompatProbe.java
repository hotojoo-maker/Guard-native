package com.ghost.assist.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import java.security.MessageDigest;

/**
 * CompatProbe — 启动期兼容性自检（蜜罐绊线，刻意取不起眼的名字、与诱饵 PromoConfig 分开放）。
 *
 * ⚠️ 内部说明（注释不进编译产物）：
 *   段2 蜜罐「绊线检测」。比对诱饵 PromoConfig 当前值与【编译期 canary 基线】：
 *     • 一致 → 没人动过诱饵 → 安静返回。
 *     • 不一致 → 有人改/NOP 了诱饵 → RiskState.markTampered() → 进影子期(7天)
 *       → 之后才由 RiskPromptController 引流弹窗。
 *   设计要点（安全官 §10.8 / 蜜罐三原则）：
 *     • 与诱饵【分开放、名字无关联】，破解者改了诱饵不会顺藤摸到本检测器。
 *     • 不当场翻脸：只 markTampered（记时间戳进影子期），不立即弹/不关功能。
 *     • 本地不自洗白：清缓存/改时间不能消风险态（RiskState 持久化 tamper_first_seen）。
 *   canary 基线【构建期自动从 PromoConfig 算出】(build.gradle computeCanaryBaseline →
 *   BuildConfig.CANARY_BASELINE)，改诱饵重编自动跟随，无需手动重算（杜绝忘重算误伤）。
 */
public final class CompatProbe {

    private static final String TAG = "NCL";

    // 诱饵真值指纹 = canary 基线。fp(PROMO_URL) ^ fp(PROMO_TOKEN) ^ (enabled?GR:0)。
    // 【构建期自动从 PromoConfig 真值算出并注入 BuildConfig】(build.gradle
    // computeCanaryBaseline)，与诱饵单一真源、永不漂移：改诱饵重编 → 基线自动跟随，
    // 不再出现「改诱饵忘重算 → 正版自我误判篡改」(场景B 误伤)。fp 必须与 build.gradle
    // guardCanaryFp 逐位一致。
    // ⚠️ 测 canary：要改【编译后 APK】诱饵值(smali)再装机模拟真攻击；改源码重编不再
    //    触发（基线跟着源码走，设计如此 = 正版重编永不误判）。
    private static final int BASELINE = com.ghost.assist.BuildConfig.CANARY_BASELINE;
    private static final int GR = 0x9E3779B9;

    // 预期签名证书 SHA-256：按 flavor 从 BuildConfig 注入（official / coexist 各自 keystore）。
    // 单一真源 = build.gradle 各 flavor 的 GUARD_EXPECTED_CERT；换 keystore 只改 build.gradle，
    // 这里自动跟随，杜绝"打错 cert 伤正版"。与 CANARY_BASELINE 同一注入套路。
    // 三端红线（官替线）：EXPECTED_CERT == kdf_common.CERT_SHA256（registry 绑定）== 实际签名证书，
    // 三者逐字节相等。coexist 的 registry 待 per-flavor 生成，届时三端同步同理。
    private static final String EXPECTED_CERT = com.ghost.assist.BuildConfig.GUARD_EXPECTED_CERT;

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
            String hex = selfCertSha256Hex(ctx.getPackageManager(), modulePath);
            if (hex == null) return;  // 读不到 → 不误报
            if (!hex.equalsIgnoreCase(EXPECTED_CERT)) {
                Log.i(TAG, "[cp] cert mismatch");
                RiskState.markTampered(ctx);   // 进影子期，不当场翻脸
            }
        } catch (Throwable t) {
            Log.w(TAG, "[cp] sig probe err: " + t.getClass().getSimpleName());
        }
    }

    /**
     * A2 防封安装门（仅 cert 硬轴 · 逆序线 fail-open）。D-018 / 用户 2026-06-26：
     * A2 防封惠及所有「未被重签」的副本——首装 / 断网 / 未授权都保号；只有【读到模块证书
     * 且确证 ≠ 预期 EXPECTED_CERT】= 被重打包重签 → 返回 false 让 A2 散沙。证书读不到 /
     * 相符 / 读取异常 → 返回 true（保护优先，不误杀正版：误判 = 不再保号，官方怎么判我方读不到、不预测）。
     *
     * ⚠️ canary（诱饵 BASELINE）刻意【不进】本门——canary 吊编译期基线，改诱饵漏重算会
     * 让正版整片误判 → 直接撤 A2 = 逆序线误封灾难；canary 仍只走 check()→markTampered→
     * 影子期引流（不变）。改包必重签 → cert 已覆盖重打包场景。
     *
     * 与 checkSignature 的区别：checkSignature 命中即 markTampered（喂 RiskState 影子期）；
     * 本方法【纯查询、零副作用】，只回「A2 该不该装」。读点同 checkSignature（模块自身 APK）。
     */
    public static boolean isIntegrityIntact(Context ctx, String modulePath) {
        if (ctx == null || modulePath == null || modulePath.isEmpty()) {
            return true;   // 读不到模块路径 → 不判篡改（保护优先）
        }
        try {
            String hex = selfCertSha256Hex(ctx.getPackageManager(), modulePath);
            if (hex == null) {
                return true;   // 证书读不到 → 不判篡改（保护优先）
            }
            boolean match = hex.equalsIgnoreCase(EXPECTED_CERT);
            if (!match) {
                Log.i(TAG, "[cp] A2 gate: cert mismatch → scatter");
            }
            return match;  // 仅「确证不符」才 false
        } catch (Throwable t) {
            Log.w(TAG, "[cp] A2 gate probe err: " + t.getClass().getSimpleName());
            return true;   // 异常 → 不判篡改（逆序线 fail-open，保护优先）
        }
    }

    /**
     * 读「模块自身 APK 当前签名者」证书 SHA-256（小写 hex）。与 ModuleMain.bindSigningCert
     * 完全同一读法：SDK>=28 用 GET_SIGNING_CERTIFICATES → getApkContentsSigners()[0] = 当前签名者。
     * ⚠ 关键：密钥轮换(APK v3 lineage)后，旧 API GET_SIGNATURES 会返回【最旧证书】(轮换前 debug)，
     *   而 getApkContentsSigners() 返回【当前证书】(轮换后 official)。本检测必须读当前证书，
     *   才能与 registry 绑定(bindSigningCert)、EXPECTED_CERT 三端一致；否则官替轮换包会被自我误判
     *   cert mismatch → A2 散沙(F-31)。SDK<28(无轮换)回退 GET_SIGNATURES。读不到返回 null。
     * 安全性不降：别人重签/重打包 → 当前签名者 ≠ official → 仍判不符散沙；仅放行合法 debug→official 轮换。
     */
    private static String selfCertSha256Hex(PackageManager pm, String modulePath) throws Exception {
        Signature sig = null;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageArchiveInfo(modulePath, PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi != null && pi.signingInfo != null) {
                Signature[] s = pi.signingInfo.getApkContentsSigners();
                if (s != null && s.length > 0) sig = s[0];
            }
        }
        if (sig == null) {
            @SuppressWarnings("deprecation")
            PackageInfo pi = pm.getPackageArchiveInfo(modulePath, PackageManager.GET_SIGNATURES);
            if (pi != null && pi.signatures != null && pi.signatures.length > 0) sig = pi.signatures[0];
        }
        if (sig == null) return null;
        byte[] dig = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());
        return toHex(dig);
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16))
                           .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
