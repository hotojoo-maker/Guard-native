package com.ghost.assist.net;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

/**
 * AuthEnvelopeVerifier — S2 信封解析 + 客户端 sanity 校验。
 *
 * ┌─ [GUARD-TRAP] 安全官锁定，动这个文件前先读 PROTECTION_MAP §7/§10.6 + 安全官 skill ─┐
 * │ S4 已落地：本类用 Ed25519 公钥验签（客户端只存公钥，伪造不了信封）。            │
 * │   • 仍【故意不做 HMAC 验签】：HMAC 需共享密钥，放客户端就能伪造信封；            │
 * │     按安全官硬红线「不放可伪造 secret 进客户端」，绝不退回 HMAC。              │
 * │   • alg 只接受 "Ed25519"；HS256/缺签名一律判废（fail-closed）。               │
 * │     ⚠️ 灰度：客户端要 Ed25519，服务器必须已切 Ed25519 签名，否则全部判废=授权中断。│
 * │   • 真锁的"牙"仍在「服务器短命 key 材料 k → 折进 SO key → 解不开=散沙」(S3a)；   │
 * │     Ed25519 防的是"伪造/篡改信封"，与 S3a 互补，不是同一件事。                 │
 * │   • 私钥仅在 miyou-server；客户端只有 ED25519_PUBLIC_B64 公钥。               │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * 本类做：① Ed25519 验签（不可伪造）→ ② 确定性 sanity（设备绑定/版本/过期）。
 * 上层负责 fail-closed：DEV 可保留 cert-only 兼容，PROD server-lock
 * 无有效 seed 必须 registry scatter，不开全功能。
 */
public final class AuthEnvelopeVerifier {

    private static final String TAG = "NCL";

    /** 期望的 registry schema / 微信版本（与服务器 release_lines android_8071 对齐）。 */
    public static final String EXPECTED_SCHEMA = "r8071_v1";
    public static final String EXPECTED_WX_VERSION = "8.0.71";

    /**
     * Ed25519 公钥（S4 验签）。私钥仅在 miyou-server（config.GUARD_ED25519_PRIVATE_B64），
     * 客户端只有公钥 → 伪造不了信封。换密钥对时这里和服务器 config 必须同步更新。
     */
    private static final String ED25519_PUBLIC_B64 = "DZM7IkVLeBXJfq7343lmPSTmN3eOJ57YPq9oq/yFpXU=";
    /** 只接受的签名算法；其余（HS256/无）一律判废。 */
    private static final String EXPECTED_ALG = "Ed25519";

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
        public int refunded;        // rf : 1=已退款（场景#6）→ 客户端立刻撤 A2+隐私（SPEC §4，唯一连坐两闸的非篡改场景）
        public String schema;       // z
        public String wxVersion;    // w
        public String productVersion; // pv
        public byte[] keyMaterial;  // k  : 短命 key 材料（S3a 折进 SO key）
        public byte[] keyNonce;     // n
        public long graceWarnSec;   // g.w
        public long graceDegradeSec;// g.d
        public long graceLockoutSec;// g.l
        public int updateMode = -1; // up.m: 0=contact, 1=download
        public String updateTitle;
        public String updateMessage;
        public String updateUrl;
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

            // ⓪ Ed25519 验签（S4）：先验签名再信 payload。验不过 = 废包（fail-closed）。
            if (!verifySignature(outer, payloadBytes)) {
                Log.w(TAG, "[env] signature verify failed");
                return null;
            }

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
            e.refunded = p.optInt("rf", 0);   // 退款位（块A server push 下发；缺省 0）
            e.schema = z;
            e.wxVersion = w;
            e.productVersion = p.optString("pv", "");
            e.keyMaterial = keyMaterial;
            e.keyNonce = keyNonce;
            JSONObject g = p.optJSONObject("g");
            if (g != null) {
                e.graceWarnSec = g.optLong("w", 0);
                e.graceDegradeSec = g.optLong("d", 0);
                e.graceLockoutSec = g.optLong("l", 0);
            }
            JSONObject up = p.optJSONObject("up");
            if (up != null) {
                e.updateMode = up.optInt("m", -1);
                e.updateTitle = up.optString("t", "");
                e.updateMessage = up.optString("d", "");
                e.updateUrl = up.optString("u", "");
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

    /**
     * Ed25519 验签。签名消息与服务器 sign_guard_envelope 一致：
     *   msg = payload_json_bytes || str(ts) || nonce
     * 其中 payload_json_bytes = base64 解码 outer.payload，ts/nonce 取自外层。
     * 客户端只持公钥，伪造不了；alg 非 Ed25519 / 缺签名 → 直接判废。
     */
    private static boolean verifySignature(JSONObject outer, byte[] payloadBytes) {
        try {
            String alg = outer.optString("alg", "");
            if (!EXPECTED_ALG.equals(alg)) {
                Log.w(TAG, "[env] unexpected alg=" + alg);
                return false;
            }
            String sigB64 = outer.optString("sig", "");
            long ts = outer.optLong("ts", 0);
            String nonceB64 = outer.optString("nonce", "");
            if (sigB64.isEmpty() || ts <= 0 || nonceB64.isEmpty()) {
                Log.w(TAG, "[env] missing sig/ts/nonce");
                return false;
            }
            byte[] sig = Base64.decode(sigB64, Base64.DEFAULT);
            byte[] nonce = Base64.decode(nonceB64, Base64.DEFAULT);

            byte[] tsBytes = Long.toString(ts).getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[payloadBytes.length + tsBytes.length + nonce.length];
            System.arraycopy(payloadBytes, 0, msg, 0, payloadBytes.length);
            System.arraycopy(tsBytes, 0, msg, payloadBytes.length, tsBytes.length);
            System.arraycopy(nonce, 0, msg, payloadBytes.length + tsBytes.length, nonce.length);

            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
            byte[] pub = Base64.decode(ED25519_PUBLIC_B64, Base64.DEFAULT);
            EdDSAPublicKey key = new EdDSAPublicKey(new EdDSAPublicKeySpec(pub, spec));
            EdDSAEngine engine = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            engine.initVerify(key);
            engine.update(msg);
            return engine.verify(sig);
        } catch (Throwable t) {
            Log.w(TAG, "[env] verify err: " + t.getClass().getSimpleName());
            return false;
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
