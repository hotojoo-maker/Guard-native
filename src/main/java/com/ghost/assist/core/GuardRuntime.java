package com.ghost.assist.core;

import android.content.Context;

import com.ghost.assist.BuildConfig;

/**
 * GuardRuntime — single entry point for hook recipes (class/field names).
 *
 * P_SEC1 / P1E: GuardRuntime + encrypted registry access shell.
 *
 * Why this class exists
 * ---------------------
 * The encrypted registry only protects the valuable hook recipes when Java code
 * reads those recipes through one controlled outlet. This class is that outlet:
 * business code asks GuardRuntime for a recipe, and GuardRuntime delegates the
 * readiness / scatter decision to EncryptedConfigLoader.
 *
 * Scope / boundaries (guard-auth-review PASS, 2026-06-09)
 * ------------------------------------------------------
 *   • RiskGate-adjacent (config / key layer) only. It does NOT read or write
 *     the StateMachine, AuthManager, or any AUTH_* state — recipe lookup is
 *     orthogonal to authorization and hide/visible state.
 *   • fail-closed: when the registry is not usable (SO missing, scatter, or a
 *     future LeaseClock / RiskState gate trips) every lookup returns "" so the
 *     caller keeps its fallback or skips its hook install.
 *   • Filters may consume recipes, but hide/show decisions still belong to
 *     StateMachine.isActive() + the hidden-id lists.
 *
 * Server seed (S_rel) is applied before registry decrypt via EnvelopeStore;
 * TODO: fold LeaseClock + RiskState into EncryptedConfigLoader so
 *   expired/tampered envelopes degrade to scatter.
 */
public final class GuardRuntime {

    private GuardRuntime() {}

    /**
     * Whether the encrypted registry is currently usable.
     */
    public static boolean isConfigReady() {
        return EncryptedConfigLoader.isConfigReady();
    }

    /**
     * Customer release builds must not fall back to embedded hook recipes. The
     * switch is build-time only so R8 can strip debug fallback literals.
     */
    public static boolean isStrictRecipeMode() {
        return !BuildConfig.DEBUG;
    }

    /**
     * Sensitive hooks are allowed only when the registry is ready in strict
     * release mode. Debug/dev keeps the old behavior for diagnostics.
     */
    public static boolean isSensitiveConfigReady() {
        return !isStrictRecipeMode() || isConfigReady();
    }

    /** Backward-compatible alias used by early P1E notes. */
    public static boolean isRegistryActive() {
        return isConfigReady();
    }

    /** Debug-only one-line summary of the active registry. */
    public static String getActiveRegistrySummary() {
        return EncryptedConfigLoader.getActiveRegistrySummary();
    }

    /** Clear cached registry readiness after binding material / future refresh. */
    public static void resetConfigCache() {
        EncryptedConfigLoader.reset();
    }

    /**
     * Look up one recipe field: {@code gateway.key} (e.g.
     * {@code getRecipe("conv.list", "adapter_class")} → {@code "kc5.v0"}).
     *
     * @return the recipe value, or "" when the registry is inactive / scattered
     *         or the gateway/field is unknown (fail-closed — callers must treat
     *         an empty result as "skip this hook").
     */
    public static String getRecipe(String gateway, String key) {
        return EncryptedConfigLoader.getRecipe(gateway, key);
    }

    /**
     * Transitional helper for Filter install-time anchors. Release+PROD returns
     * empty on registry miss so callers can skip installing that sensitive hook;
     * debug/dev returns the known literal fallback.
     */
    public static String getRecipeOrFallback(String gateway, String key, String fallback) {
        String v = getRecipe(gateway, key);
        if (v != null && !v.isEmpty()) return v;
        return isStrictRecipeMode() ? "" : fallback;
    }

    public static String[] getRecipeListOrFallback(String gateway, String key, String[] fallback) {
        String v = getRecipe(gateway, key);
        if (v == null || v.isEmpty()) {
            return isStrictRecipeMode() ? new String[0] : fallback;
        }
        String[] parts = v.split(",");
        return parts.length > 0 ? parts : (isStrictRecipeMode() ? new String[0] : fallback);
    }

    public static boolean hasRecipe(String gateway, String key) {
        String v = getRecipe(gateway, key);
        return v != null && !v.isEmpty();
    }

    // ── A2 防封授权闸（Route B · 本地完整性 + 时间闸）─────────────────
    //
    // isAntiBanReady() 是 A2 防封能力（喂官方签名让数据正常）的唯一闸出口，与隐私
    // isActive() / isConfigReady() 两闸独立、互不连坐（SSOT §2 / DESIGN §1 / 授权检查官 §九）。
    // 失败方向相反：A2 = fail-OPEN（拿不准就装，误判=不再保号、不可逆，宁错放勿错杀正版）；
    // 隐私 = fail-CLOSED（拿不准就散，误判可逆、是变现门）。本闸只门控 A2，不读 StateMachine。
    //
    // D-020（用户 2026-06-27 G88 拍板，取代 D-018「未授权永不撤」）：A2 = 本地 cert 完整性 + 时间闸。
    //   撤闸触发（否则 fail-open 装）：
    //     ① 重签篡改（cert ≠ EXPECTED / 蜜罐绊线）→ 立刻散（D-019 不可逆，不等服务器）。
    //     ② 首装未授权 → 官方授时(hd.b)首次有值起算 72h 后撤（全新装无值 fail-open 恒装、不计时）。
    //     ③ 曾授权自然到期 → licenseExpire + 7 天宽限后撤；服务器封停/删卡 → cardRevokedAt + 72h 后撤。
    //   可恢复（除篡改）：重新输入有效授权 → 恢复（latch 由 EnvelopeStore 收有效信封时清，封停不再永久焊死）。
    //   命门 = 可信时间不可冻结：LeaseClock.trustedNow 吊服务器授时 + 官方对时 hd.b（抗改表）+ max 水位只抬不降。
    //   canary 刻意不进本门（吊编译期基线、漏算会整片误封），仍走 CompatProbe.check→markTampered→影子期（不变）。
    //   官方 DER 本地化（公开值，见 A2SignatureSpoof.OFFICIAL_DER_HEX）；隐私 registry 仍 server-seed fail-closed 不动。

    /** A2 官方 DER registry 取件口（D-018 起代码不再读；registry entry 保留、料已常量化）。 */
    public static final String A2_SIG_GATEWAY      = "a2.sig";
    public static final String A2_SIG_OFFICIAL_DER = "official_der";

    // A2 时间闸宽限（D-020 · 常量+TODO，不进配方卡 / 另一路在改）。全局仅两个宽限值：72h / 7 天。
    private static final long ANTIBAN_GRACE_FIRST_MS   = 72L * 3600_000L;        // 首装未授权 + 封停/删卡 复用（防漂）
    private static final long ANTIBAN_GRACE_EXPIRED_MS = 7L * 24L * 3600_000L;   // 曾授权自然到期

    public static boolean isAntiBanReady(Context ctx, String modulePath) {
        // 1. 重签 / 篡改（cert ≠ EXPECTED）→ 立刻散（D-019 篡改线·不可逆，最高优先，逻辑不动）。
        if (!CompatProbe.isIntegrityIntact(ctx, modulePath)) return false;
        // 2. 时间闸（D-020）。逆序线 fail-open：任何异常 → 装（A2 误判=不再保号、不可逆，宁不撤勿误杀正版）。
        try {
            return isWithinAntiBanWindow();
        } catch (Throwable t) {
            android.util.Log.w("NCL", "[antiban] gate err (fail-open): " + t.getClass().getSimpleName());
            return true;
        }
    }

    /**
     * A2 时间闸窗口判定（D-020 · 全 fail-open 兜底）。撤闸触发，否则装：
     *   • 授权中 → 装。
     *   • 封停/删卡 → cardRevokedAt + 72h 后撤（复用首装 72h；可被有效授权恢复，见 EnvelopeStore）。
     *   • 曾授权（有 token）自然到期 → licenseExpire + 7 天后撤；license 已清（断网自愈态）→ fail-open 装。
     *   • 首装未授权 → officialBase + 72h 后撤；无官方授时值（全新装）→ fail-open 装、不计时。
     * 篡改（cert）已在 isAntiBanReady 第 1 步前置拦截，不进本窗口；隐私闸 isAuthorizedNow 封停仍立刻撤（两闸独立）。
     */
    private static boolean isWithinAntiBanWindow() {
        long now = LeaseClock.trustedNow();
        return evalAntiBanWindow(
                com.ghost.assist.net.EnvelopeStore.isAuthorizedNow(),
                com.ghost.assist.net.EnvelopeStore.isCardRevoked(),
                com.ghost.assist.net.EnvelopeStore.getCardRevokedAt(),
                com.ghost.assist.net.EnvelopeStore.hasToken(),
                com.ghost.assist.net.EnvelopeStore.getLicenseExpireSec() * 1000L,
                LeaseClock.getOfficialBaseMs(),
                now);
    }

    /**
     * A2 时间闸撤闸判定（纯函数 · 无 IO / 无持久态读写，便于 DEBUG self-test 喂构造参数全分支验证，
     * 因 hd.b 抗改表、改墙钟测不出「超期撤」）。与 isWithinAntiBanWindow 同逻辑：
     *   授权中→装；封停→cardRevokedAt+72h；曾授权(token)到期→licenseExpire+7天；
     *   首装未授权→officialBase+72h；各「无值 / 无时间戳」→fail-open 装。
     */
    private static boolean evalAntiBanWindow(boolean authorized, boolean cardRevoked, long cardRevokedAtMs,
                                             boolean hasToken, long licenseExpireMs, long officialBaseMs, long now) {
        if (authorized) return true;                                  // 授权中 → 装
        if (cardRevoked) {                                            // 封停/删卡 → 72h
            if (cardRevokedAtMs <= 0L) return true;                  // 异常无时间戳 → fail-open
            return now <= cardRevokedAtMs + ANTIBAN_GRACE_FIRST_MS;
        }
        if (hasToken) {                                              // 曾授权 → 自然到期 7 天
            if (licenseExpireMs <= 0L) return true;                  // license 已清（自愈态）→ fail-open
            return now <= licenseExpireMs + ANTIBAN_GRACE_EXPIRED_MS;
        }
        if (officialBaseMs <= 0L) return true;                       // 全新装无官方授时值 → fail-open
        return now <= officialBaseMs + ANTIBAN_GRACE_FIRST_MS;       // 首装未授权 → 72h
    }

    /**
     * DEBUG-only cold-start self-test for the A2 anti-ban gate. Logs the final
     * gate decision plus every sub-signal (cert integrity / authorized / card
     * revoked / official-time base / trustedNow) under the {@code ANTIBAN-GATE}
     * marker (Route B / D-020: gate = local module-cert integrity + time-gate
     * 72h/7d, fail-open). No JUnit harness exists in this repo; this mirrors the
     * existing native KDF / registry self-tests and is gated by BuildConfig.DEBUG
     * at the call site so release never logs it.
     */
    public static void antiBanGateSelfTest(String tag, Context ctx, String modulePath) {
        boolean integrity = CompatProbe.isIntegrityIntact(ctx, modulePath);
        boolean authorized = com.ghost.assist.net.EnvelopeStore.isAuthorizedNow();
        boolean cardRevoked = com.ghost.assist.net.EnvelopeStore.isCardRevoked();
        long officialBase = LeaseClock.getOfficialBaseMs();
        long now = LeaseClock.trustedNow();
        android.util.Log.i(tag, "[ANTIBAN-GATE] ready=" + isAntiBanReady(ctx, modulePath)
                + " certIntegrityIntact=" + integrity
                + " authorized=" + authorized
                + " cardRevoked=" + cardRevoked
                + " officialBase=" + officialBase
                + " trustedNow=" + now
                + " (D-020: cert + time-gate 72h/7d, fail-open)");
    }

    /**
     * DEBUG-only 时间闸全分支自测（D-020 真机回归）。因 hd.b 抗改表、改墙钟测不出「超期撤」，
     * 用纯函数 {@link #evalAntiBanWindow} 喂构造参数验全分支，打印 [ANTIBAN-BRANCH]
     * expect/actual/PASS-FAIL，**零持久态污染**（不动真实未授权/失效态）。重点覆盖 ②新装超时 /
     * ④封停超时（②③失效另走真服务器端到端）。release 由 BuildConfig.DEBUG 在调用点剔除。
     */
    public static void antiBanBranchSelfTest(String tag) {
        final long now = 1_800_000_000_000L;            // 固定基准(≈2027)，与真实态无关，纯逻辑验证
        final long H = ANTIBAN_GRACE_FIRST_MS;          // 72h（首装 / 封停）
        final long D = ANTIBAN_GRACE_EXPIRED_MS;        // 7 天（自然到期）
        branchCheck(tag, "1_paid",             evalAntiBanWindow(true,  false, 0,           true,  0,           0,           now), true);
        branchCheck(tag, "2a_fresh_in72h",     evalAntiBanWindow(false, false, 0,           false, 0,           now - H / 2, now), true);
        branchCheck(tag, "2b_fresh_over72h",   evalAntiBanWindow(false, false, 0,           false, 0,           now - H - 1, now), false);
        branchCheck(tag, "2c_fresh_noclock",   evalAntiBanWindow(false, false, 0,           false, 0,           0,           now), true);
        branchCheck(tag, "3a_expired_in7d",    evalAntiBanWindow(false, false, 0,           true,  now - D / 2, 0,           now), true);
        branchCheck(tag, "3b_expired_over7d",  evalAntiBanWindow(false, false, 0,           true,  now - D - 1, 0,           now), false);
        branchCheck(tag, "4a_revoked_in72h",   evalAntiBanWindow(false, true,  now - H / 2, true,  0,           0,           now), true);
        branchCheck(tag, "4b_revoked_over72h", evalAntiBanWindow(false, true,  now - H - 1, true,  0,           0,           now), false);
    }

    private static void branchCheck(String tag, String name, boolean actual, boolean expect) {
        android.util.Log.i(tag, "[ANTIBAN-BRANCH] " + name + " expect=" + expect
                + " actual=" + actual + (actual == expect ? " PASS" : " FAIL"));
    }
}
