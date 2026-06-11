package com.ghost.assist.net;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AuthEnvelopeVerifier — S2 信封解析 + 客户端 sanity 校验。
 *
 * ┌─ [GUARD-TRAP] 安全官锁定，动这个文件前先读 PROTECTION_MAP §7 + 安全官 skill ─┐
 * │ 本类【故意不做 HMAC 验签】。原因：                                          │
 * │   • HMAC 验签需要共享密钥；密钥放客户端 = 被逆向抠出来就能伪造信封。           │
 * │     按安全官硬红线「不放可伪造 secret 进客户端」，这里不放。                   │
 * │   • 真锁的"牙"不在验签，在「服务器短命 key 材料 k → 折进 SO key →             │
 * │     解不开 registry = 散沙」（S3a）。伪造信封的 k 是错的，registry 根本解不开。 │
 * │   • 完整密码学验签 = S4 上 Ed25519（客户端只存公钥，伪造不了）。              │
 * │ 所以：谁都不要"顺手补一个 HMAC 校验"把共享密钥塞进来 —— 那是降级不是加固。     │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * 本类只做确定性 sanity（不依赖密钥）：拿错包/重打包/版本不符直接判废，
 * 交上层 fail-closed（registry 维持 cert-only 派生，不开全功能）。
 */
public final class AuthEnvelopeVerifier {

    private static final String TAG = "NCL";

    /** 期望的 registry schema / 微信版本（与服务器 release_lines android_8071 对齐）。 */
    public static final String EXPECTED_SCHEMA = "r8071_v1";
    public static final String EXPECTED_WX_VERSION = "8.0.71";

    private AuthEnvelopeVerifier() {}

    /** 解析后的信封内容；上层（S3a key 折入 / S3b LeaseClock·RiskState）按需取用。 */
    public static final class Envelope {
        public int status;          // s  : 1=active 0=inactive
        public long serverNow;      // sn : 服务器时间（LeaseClock 授时源）
        public long issuedAt;       // iat
        public long leaseExpire;    // exp: 本期租约到期
        public long licenseExpire;  // le : license 到期
        public int tier;            // q  : 0=stable 1=probe 2=suspicious 3=shadow 4=notice
        public int risk;            // r  : 0-255
        public String schema;       // z
        public String wxVersion;    // w
        public byte[] keyMaterial;  // k  : 短命 key 材料（S3a 折进 SO key）
        public byte[] keyNonce;     // n
        public long graceWarnSec;   // g.w
        public long graceDegradeSec;// g.d
        public long graceLockoutSec;// g.l
        public String rawSignedBlob;// 整份签名信封（离线缓存复用）
    }

    /**
     * 解析 + sanity 校验签名信封。
     *
     * @param signedEnvelopeJson EnvelopeClient.fetchEnvelope 返回的外层 JSON
     * @param deviceId           与请求时同一个设备标识（用于校验 d 绑定）
     * @return 通过返回 Envelope；任一 sanity 不过返回 null（上层 fail-closed）
     */
    public static Envelope verifyAndParse(String signedEnvelopeJson, String deviceId) {
        if (signedEnvelopeJson == null || signedEnvelopeJson.isEmpty()) return null;
        try {
            JSONObject outer = new JSONObject(signedEnvelopeJson);
            String payloadB64 = outer.optString("payload", "");
            if (payloadB64.isEmpty()) {
                Log.w(TAG, "[env] no payload");
                return null;
            }
            byte[] payloadBytes = Base64.decode(payloadB64, Base64.DEFAULT);
            JSONObject p = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));

            // ① 设备绑定：payload.d == sha256(deviceId)[:32]
            String boundD = p.optString("d", "");
            String myD = sha256Hex(deviceId);
            if (myD == null || boundD.isEmpty()
                    || !boundD.equals(myD.substring(0, Math.min(32, myD.length())))) {
                Log.w(TAG, "[env] device binding mismatch");
                return null;
            }

            // ② schema / 微信版本 必须对上当前发行线
            String z = p.optString("z", "");
            String w = p.optString("w", "");
            if (!EXPECTED_SCHEMA.equals(z) || !EXPECTED_WX_VERSION.equals(w)) {
                Log.w(TAG, "[env] schema/version mismatch z=" + z + " w=" + w);
                return null;
            }

            // ③ 必须带 key 材料（真锁靠它；没有就当废包）
            String kB64 = p.optString("k", "");
            String nB64 = p.optString("n", "");
            if (kB64.isEmpty() || nB64.isEmpty()) {
                Log.w(TAG, "[env] missing key material");
                return null;
            }

            byte[] keyMaterial = Base64.decode(kB64, Base64.DEFAULT);
            byte[] keyNonce = Base64.decode(nB64, Base64.DEFAULT);
            if (keyMaterial == null || keyMaterial.length != 48
                    || keyNonce == null || keyNonce.length < 12) {
                Log.w(TAG, "[env] bad key material length");
                return null;
            }

            Envelope e = new Envelope();
            e.status = p.optInt("s", 0);
            if (e.status != 1) {
                Log.w(TAG, "[env] inactive status");
                return null;
            }
            e.serverNow = p.optLong("sn", 0);
            e.issuedAt = p.optLong("iat", 0);
            e.leaseExpire = p.optLong("exp", 0);
            e.licenseExpire = p.optLong("le", 0);
            e.tier = p.optInt("q", 1);
            e.risk = p.optInt("r", 0);
            e.schema = z;
            e.wxVersion = w;
            e.keyMaterial = keyMaterial;
            e.keyNonce = keyNonce;
            JSONObject g = p.optJSONObject("g");
            if (g != null) {
                e.graceWarnSec = g.optLong("w", 0);
                e.graceDegradeSec = g.optLong("d", 0);
                e.graceLockoutSec = g.optLong("l", 0);
            }
            e.rawSignedBlob = signedEnvelopeJson;

            // ④ 基础 sanity：租约不能一拿到就已过期（相对服务器时间 sn）
            if (e.leaseExpire > 0 && e.serverNow > 0 && e.leaseExpire <= e.serverNow) {
                Log.w(TAG, "[env] pre-expired lease");
                return null;
            }
            return e;
        } catch (Throwable t) {
            Log.w(TAG, "[env] parse err: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private static String sha256Hex(String s) {
        if (s == null) s = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
