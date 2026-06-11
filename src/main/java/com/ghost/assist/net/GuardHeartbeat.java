package com.ghost.assist.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.LeaseClock;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.RiskState;

import java.util.Random;

/**
 * GuardHeartbeat — S2 低频心跳调度骨架（Phase 1D-server）。
 *
 * 节奏（用户 2026-06-11 锁定）：
 *   • 新装/probe (q=1)   : 10 ~ 30 min
 *   • 正常稳定 (q=0)     : 1 ~ 2 h
 *   • 嫌疑/影子 (q>=2)   : 10 min（升频密集观察）
 *   • 硬封顶            : 6 h —— 任何档都不超过，保证封停/危险通告 ≤6h 下发
 *   • 全程随机抖动 ±15%  : 不走固定整点，避免成为反检测指纹
 *
 * 反检测：稳定态 1~6h 对 CONN 密度无压力；只在「冷启动 / 租约将过期」拉，不轮询。
 *
 * 边界：本类只调出站 / 信封 sanity / 缓存 / NativeBridge seed 出口，
 * 不做隐藏/显示决策，不碰 StateMachine / Filter。
 *
 * ⚠️ 未接入 ModuleMain（冷启动触发点 = 保护区，单独走授权检查官审）；
 *    本骨架默认 dormant，wiring 是后续受控步骤。
 */
public final class GuardHeartbeat {

    private static final String TAG = "NCL";

    private static final long MIN_10 = 10L * 60 * 1000;
    private static final long MIN_30 = 30L * 60 * 1000;
    private static final long HOUR_1 = 60L * 60 * 1000;
    private static final long HOUR_2 = 2L * 60 * 60 * 1000;
    /** 硬封顶 6h：任何档位都不得超过。 */
    private static final long CAP_6H = 6L * 60 * 60 * 1000;
    /** S3b-B：进设置页时，断网超过此时长则强制重验（失败=撤销授权）。 */
    private static final long STALE_REVERIFY_MS = 72L * 60 * 60 * 1000; // 72h

    private static final Random RND = new Random();

    private static volatile boolean sStarted = false;
    private static Handler sHandler;

    private GuardHeartbeat() {}

    /**
     * 按风险层算下次心跳间隔（含抖动 + 6h 封顶）。
     *
     * @param tier 信封里的 q：0=stable 1=probe 2=suspicious 3=shadow 4=notice
     */
    public static long nextIntervalMs(int tier) {
        long base;
        if (tier >= 2) {
            base = MIN_10;                              // 嫌疑：升频密集观察
        } else if (tier == 1) {
            base = MIN_10 + (long) (RND.nextDouble() * (MIN_30 - MIN_10)); // 新装 10~30min
        } else {
            base = HOUR_1 + (long) (RND.nextDouble() * (HOUR_2 - HOUR_1)); // 稳定 1~2h
        }
        // 抖动 ±15%
        double jitter = 1.0 + (RND.nextDouble() * 0.30 - 0.15);
        long v = (long) (base * jitter);
        return Math.min(v, CAP_6H);
    }

    /**
     * 跑一次心跳：用缓存 token 取新信封 → 验签 sanity → 存。
     * 失败保留旧缓存（fail-closed，不清数据）。必须后台线程。
     *
     * @return 成功返回新信封的 tier（用于排下次间隔）；失败返回 -1
     */
    public static int syncOnce(String deviceId, String certHex, String appVersion) {
        try {
            String token = EnvelopeStore.getToken();
            if (token == null || token.isEmpty()) {
                Log.i(TAG, "[hb] no token yet — awaiting activation");
                return -1;
            }
            String installId = deviceId; // TODO: 换成 per-install UUID（区分反复卸装）
            String env = EnvelopeClient.fetchEnvelope(token, deviceId, certHex, appVersion, installId);
            AuthEnvelopeVerifier.Envelope e = AuthEnvelopeVerifier.verifyAndParse(env, deviceId);
            if (e == null) {
                if (isHardAuthError(EnvelopeClient.getLastErrorCode())) {
                    EnvelopeStore.clear();
                    EnvelopeStore.saveAuthError(authErrorText(EnvelopeClient.getLastErrorCode()));
                    Log.w(TAG, "[hb] hard auth error — token cleared");
                }
                Log.w(TAG, "[hb] envelope invalid — fail-closed");
                return -1;
            }
            if (!NativeBridge.applyServerSeedAndReset(e.keyMaterial, e.keyNonce)) {
                Log.w(TAG, "[hb] server seed unwrap failed — keep cached, fail-closed");
                return -1;
            }
            EnvelopeStore.saveEnvelope(e);

            // S3b：把已验签的服务器时间 + 租约喂给 LeaseClock（信封是 unix 秒 → 毫秒）。
            // record-only：只建立可信时间基准 + 重算风险等级记日志，不改任何门控（不误伤）。
            LeaseClock.onServerHeartbeat(e.serverNow * 1000L, e.leaseExpire * 1000L);
            RiskState.Level lvl = RiskState.evaluate();
            Log.i(TAG, "[hb] synced tier=" + e.tier + " lease=" + e.leaseExpire
                    + " risk=" + lvl.label);
            return e.tier;
        } catch (Throwable t) {
            Log.w(TAG, "[hb] sync err: " + t.getClass().getSimpleName());
            return -1;
        }
    }

    /**
     * S3b-B 设置页"算账检查点"：断网 > 72h 才强制联网重验。
     *   • 72h 内 / 无离线基准 / 本就未授权 → 不动，返回当前授权态。
     *   • 重验成功 → 保持授权。
     *   • 重验失败（断网/无效）→ 撤销授权（保留 token 自愈）+ 记时间错误文案，返回 false。
     * 内部异常按 fail-open 处理（不因 bug 误杀正版）。必须后台线程调用（含网络）。
     *
     * @return 检查后是否仍授权（false = 已撤销）
     */
    public static boolean reverifyIfStale(String deviceId, String certHex, String appVersion) {
        try {
            if (!EnvelopeStore.isAuthorizedNow()) return false; // 本就未授权，无需算账
            long offline = LeaseClock.offlineMillis();
            if (offline < 0 || offline <= STALE_REVERIFY_MS) return true; // 72h 内或无基准 → 放行
            int tier = syncOnce(deviceId, certHex, appVersion);
            if (tier >= 0) {
                Log.i(TAG, "[hb] stale re-verify ok (offline was " + (offline / 3600000) + "h)");
                return true;
            }
            EnvelopeStore.revokeKeepToken();
            EnvelopeStore.saveAuthError("当前时间错误，授权验证失败，请检查时间");
            Log.w(TAG, "[hb] stale re-verify FAILED — auth revoked (offline " + (offline / 3600000) + "h)");
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "[hb] reverify err (fail-open): " + t.getClass().getSimpleName());
            return true; // 内部错误不误杀
        }
    }

    private static boolean isHardAuthError(String code) {
        return "CARD_BANNED".equals(code)
                || "CARD_DISABLED".equals(code)
                || "CARD_EXPIRED".equals(code)
                || "DEVICE_BANNED".equals(code)
                || "TOKEN_INVALID".equals(code);
    }

    private static String authErrorText(String code) {
        if ("CARD_EXPIRED".equals(code)) return "授权已到期，请联系售后";
        if ("CARD_BANNED".equals(code) || "CARD_DISABLED".equals(code)) return "授权码已停用，请联系售后";
        if ("DEVICE_BANNED".equals(code)) return "设备已封停，请联系售后";
        if ("TOKEN_INVALID".equals(code)) return "授权已失效，请重新激活";
        return "授权异常，请联系售后";
    }

    /**
     * 启动心跳循环：先同步一次，再按风险层自排下次（postDelayed + 后台网络）。
     * 幂等；重复调用只生效一次。
     *
     * ⚠️ 调用点应是 ModuleMain 冷启动（保护区，待审接入）；现作为骨架提供。
     */
    public static synchronized void start(final String deviceId,
                                          final String certHex, final String appVersion) {
        if (sStarted) return;
        sStarted = true;
        sHandler = new Handler(Looper.getMainLooper());
        scheduleTick(deviceId, certHex, appVersion, 0);
    }

    private static void scheduleTick(final String deviceId, final String certHex,
                                     final String appVersion, long delayMs) {
        if (sHandler == null) return;
        sHandler.postDelayed(new Runnable() {
            @Override public void run() {
                EnvelopeClient.runAsync(new Runnable() {
                    @Override public void run() {
                        int tier = syncOnce(deviceId, certHex, appVersion);
                        long next = nextIntervalMs(tier < 0 ? EnvelopeStore.getTier() : tier);
                        scheduleTick(deviceId, certHex, appVersion, next);
                    }
                });
            }
        }, delayMs);
    }
}
