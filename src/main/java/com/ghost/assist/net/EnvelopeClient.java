package com.ghost.assist.net;

import android.util.Log;

import com.ghost.assist.core.A2SignatureSpoof;
import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.AuthManager;
import com.ghost.assist.core.Bridge;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * EnvelopeClient — S2 真锁出站网络层（Phase 1D-server）。
 *
 * 职责（只做 IO，不做门控）：
 *   1. activate(card_key) → 服务器返回 token
 *   2. fetchEnvelope(token) → 服务器返回签名短命信封
 *   3. 主备 fallback：按 AppConfig.guardServerList() 逐台试，第一台成功即返回
 *
 * 安全约束（动这个文件前先读，别"优化"掉）：
 *   • 强制 HTTPS：post() 拒绝任何非 https:// 地址（信封里的短命 key 材料 k 不得走明文）。
 *   • 走系统证书校验（HttpsURLConnection 默认），不得关掉校验——证书不对就连接失败 = fail-closed。
 *   • 不持有任何签名密钥；本类只搬运密文，验签/sanity 在 AuthEnvelopeVerifier。
 *   • 运行在 com.tencent.mm 主进程（继承宿主 INTERNET 权限），属我们自有通道，不碰微信流量。
 *   • 必须在后台线程调用（runAsync）；不要在主线程网络。
 */
public final class EnvelopeClient {

    private static final String TAG = "NCL";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final String SCHEMA_ID = "r8071_v1";
    private static volatile String sLastErrorCode = "";
    // 服务器下发的中文错误文案与设备短码（db.py 所有 error 响应都带 message；DEVICE_LIMIT 还带 device_short）。
    // 由 activate() / fetchEnvelope() 在每次请求后回填，调用方读到即用，空串=未拿到（走客户端 authErrorText 兜底）。
    private static volatile String sLastErrorMessage = "";
    private static volatile String sLastDeviceShort = "";

    private EnvelopeClient() {}

    public static String getLastErrorCode()    { return sLastErrorCode; }
    public static String getLastErrorMessage() { return sLastErrorMessage; }
    public static String getLastDeviceShort()  { return sLastDeviceShort; }

    /**
     * 授权错误码 → 用户可见文案（合一：原 GuardActivation/GuardHeartbeat 各有一份私有副本，
     * 收敛到产错误码的本类单一出口，防漂移）。覆盖全部码（激活态超集）；未知码回落通用文案。
     */
    public static String authErrorText(String code) {
        if ("CARD_EXPIRED".equals(code)) return "授权已到期，请联系客服";
        if ("CARD_BANNED".equals(code) || "CARD_DISABLED".equals(code)) return "授权码已停用，请联系客服";
        if ("REFUNDED".equals(code)) return "该授权码已失效，请联系客服";
        if ("DEVICE_BANNED".equals(code)) return "设备已封停，请联系客服";
        if ("DEVICE_ALREADY_BOUND".equals(code)) return "此设备已绑定其他授权码，请联系客服";
        if ("DEVICE_LIMIT".equals(code)) return "设备数量已达上限，请联系客服";
        if ("RELEASE_KILLED".equals(code)) return "该版本已停用，请联系客服";
        if ("RELEASE_PAUSED".equals(code)) return "该版本暂停新激活，请联系客服";
        if ("VERSION_KILLED".equals(code)) return "该版本已停用，请更新";
        if ("TOKEN_INVALID".equals(code)) return "授权已失效，请重新激活";
        if ("DEVICE_ABNORMAL".equals(code)) return "设备异常，请联系客服";
        return "授权异常，请联系客服";
    }

    /**
     * dm = hex(SHA-256(ANDROID_ID)) 32B（牙③ 灰度①：服务器逐设备 wrap S_rel 的依据）。
     * 服务器 Batch 2 前忽略此字段 → 向后兼容。取值走 AuthManager 单一读点
     * （rawAndroidId memoize），不新增读点；A2 灌给官方包的官方 SSAID 不进此值（A2 同源隔离）。
     * 取不到（无 ctx / android_id 空）→ ""，不阻塞正版（SO 双试自动回退全局 W）。
     */
    private static String deviceMaterialHex() {
        try {
            android.content.Context ctx = AppConfig.getInstance().getAppContext();
            return ctx == null ? "" : AuthManager.computeDeviceMaterialHex(ctx);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 卡密激活 → 拿 token。
     *
     * @param cardKey  用户输入的激活码
     * @param deviceId 设备标识（与 envelope 用同一个，服务端按它绑定 d）
     * @return token；失败返回 null（卡密无效 / 网络失败 / 全部服务器不可达）
     */
    public static String activate(String cardKey, String deviceId) {
        JSONObject body = new JSONObject();
        try {
            body.put("card_key", cardKey == null ? "" : cardKey);
            body.put("product_id", AppConfig.GUARD_PRODUCT_ID);
            body.put("device_id", deviceId == null ? "" : deviceId);
            body.put("dm", deviceMaterialHex());
            body.put("release_id", AppConfig.GUARD_RELEASE_ID);
            body.put("app_version", AppConfig.GUARD_PRODUCT_VERSION);
            // Stage2：activate 顺带回填设备画像（与 checkin 同款嵌套 client{}，服务器按 device_id
            // 补全同一条记录）。兜底「冷启没网 → checkin deferred → 后来有网又激活」的边界：
            // 搭现成 activate 请求、零新增网络事件；纯遥测，不进授权判定（授权仍看 token/envelope）。
            JSONObject client = new JSONObject();
            JSONObject dp = deviceProfile();
            client.put("brand", dp.optString("brand", ""));
            client.put("model", dp.optString("model", ""));
            client.put("os", dp.optString("os", ""));
            client.put("re", A2SignatureSpoof.getBorrowedEnvSignal());  // 借官方眼睛弱信号（非封因，弱权重）
            body.put("client", client);
        } catch (Throwable t) {
            return null;
        }
        sLastErrorCode = "";
        sLastErrorMessage = "";
        sLastDeviceShort = "";
        for (String base : AppConfig.guardServerList()) {
            String resp = post(base + "/api/v1/activate", body.toString());
            if (resp == null) continue;
            try {
                JSONObject j = new JSONObject(resp);
                if ("ok".equals(j.optString("status"))) {
                    String token = j.optString("token", "");
                    if (!token.isEmpty()) return token;
                } else {
                    sLastErrorCode    = j.optString("code", j.optString("status", ""));
                    sLastErrorMessage = j.optString("message", "");
                    sLastDeviceShort  = j.optString("device_short", "");
                }
            } catch (Throwable ignore) {
                // malformed body → try next server
            }
        }
        return null;
    }

    /**
     * 用 token 取一份短命签名信封。返回的是信封对象 JSON 串（外层
     * {type,alg,kid,ts,nonce,payload,sig}），交给 AuthEnvelopeVerifier 解析。
     *
     * @return 信封 JSON 串；失败返回 null
     */
    public static String fetchEnvelope(String token, String deviceId,
                                       String certHex, String appVersion, String installId) {
        JSONObject body = new JSONObject();
        try {
            body.put("token", token == null ? "" : token);
            body.put("device_id", deviceId == null ? "" : deviceId);
            body.put("dm", deviceMaterialHex());
            body.put("product_id", AppConfig.GUARD_PRODUCT_ID);
            body.put("release_id", AppConfig.GUARD_RELEASE_ID);
            JSONObject client = new JSONObject();
            client.put("install_id", installId == null ? "" : installId);
            client.put("app_version", appVersion == null ? "" : appVersion);
            client.put("schema", SCHEMA_ID);
            client.put("cert", certHex == null ? "" : certHex);
            client.put("release_id", AppConfig.GUARD_RELEASE_ID);
            client.put("re", A2SignatureSpoof.getBorrowedEnvSignal());  // 借官方眼睛弱信号（非封因，服务器弱权重）
            body.put("client", client);
        } catch (Throwable t) {
            return null;
        }
        sLastErrorCode = "";
        sLastErrorMessage = "";
        sLastDeviceShort = "";
        for (String base : AppConfig.guardServerList()) {
            String resp = post(base + "/api/v1/guard/envelope", body.toString());
            if (resp == null) continue;
            try {
                JSONObject j = new JSONObject(resp);
                if ("ok".equals(j.optString("status")) && j.has("envelope")) {
                    return j.getJSONObject("envelope").toString();
                } else {
                    sLastErrorCode    = j.optString("code", j.optString("status", ""));
                    sLastErrorMessage = j.optString("message", "");
                    sLastDeviceShort  = j.optString("device_short", "");
                }
            } catch (Throwable ignore) {
                // malformed body → try next server
            }
        }
        return null;
    }

    /**
     * Best-effort health telemetry. This must never grant, revoke, or poison
     * auth state; failures are intentionally ignored by callers.
     */
    public static boolean reportHealth(String token, String deviceId,
                                       String certHex, String appVersion,
                                       JSONObject health) {
        JSONObject body = new JSONObject();
        try {
            body.put("token", token == null ? "" : token);
            body.put("device_id", deviceId == null ? "" : deviceId);
            body.put("dm", deviceMaterialHex());
            body.put("product_id", AppConfig.GUARD_PRODUCT_ID);
            body.put("release_id", AppConfig.GUARD_RELEASE_ID);

            JSONObject client = new JSONObject();
            client.put("install_id", deviceId == null ? "" : deviceId);
            client.put("app_version", appVersion == null ? "" : appVersion);
            client.put("schema", AuthEnvelopeVerifier.EXPECTED_SCHEMA);
            client.put("wx_version", AuthEnvelopeVerifier.EXPECTED_WX_VERSION);
            client.put("release_id", AppConfig.GUARD_RELEASE_ID);
            client.put("cert", certHex == null ? "" : certHex);
            client.put("re", A2SignatureSpoof.getBorrowedEnvSignal());  // 借官方眼睛弱信号（非封因，服务器弱权重）
            client.put("acct", currentAcct());  // 当前登录账号（微信号优先，空则 wxid）：后台换号识别 + 画像
            try {
                android.content.Context ctx = AppConfig.getInstance().getAppContext();
                client.put("package_name", ctx == null ? "" : ctx.getPackageName());
            } catch (Throwable ignored) {
                client.put("package_name", "");
            }
            body.put("client", client);
            body.put("health", health == null ? new JSONObject() : health);
        } catch (Throwable t) {
            return false;
        }

        String prevError = sLastErrorCode;
        String prevMsg = sLastErrorMessage;
        String prevDev = sLastDeviceShort;
        try {
            for (String base : AppConfig.guardServerList()) {
                String resp = post(base + "/api/v1/guard/health", body.toString());
                if (resp == null) continue;
                try {
                    JSONObject j = new JSONObject(resp);
                    if ("ok".equals(j.optString("status"))) return true;
                } catch (Throwable ignore) {
                    // malformed body -> try next server
                }
            }
            return false;
        } finally {
            sLastErrorCode = prevError;
            sLastErrorMessage = prevMsg;
            sLastDeviceShort = prevDev;
        }
    }

    /**
     * B 装后预注册（checkin）：best-effort 上报「设备画像」，让后台看到「装了但还没激活」的漏斗。
     *
     * 与 activate 的区别：activate 要 card_key、只在用户激活时发；checkin 免授权、微信首次冷启即发。
     * 时机/焊死由 ModuleMain 冷启触发 + EnvelopeStore.dr 标志控制（至少一次·最终一致，见该处注释）。
     *
     * 安全约束：
     *   • 仅 MAIN 进程调（ModuleMain onApplicationCreated 已天然隔离 :push，铁律 6）。
     *   • 后台线程调（runAsync），走强制 HTTPS post()，逐台 fallback。
     *   • 只带静态设备画像 + 借官方眼睛弱信号 re；不碰授权态、不碰微信流量、不进微信 normsg 链。
     *   • 载荷不含短命 key 材料，但仍走 https（post() 强制），与全局一致。
     *
     * @return true=某台服务器返回 status=ok（可焊死 dr）；false=全不可达/被拒（下次冷启再试）。
     */
    public static boolean checkin(String deviceId) {
        JSONObject body = new JSONObject();
        try {
            body.put("product_id", AppConfig.GUARD_PRODUCT_ID);
            body.put("release_id", AppConfig.GUARD_RELEASE_ID);
            body.put("device_id", deviceId == null ? "" : deviceId);
            body.put("dm", deviceMaterialHex());
            body.put("app_version", AppConfig.GUARD_PRODUCT_VERSION);
            JSONObject client = new JSONObject();
            JSONObject dp = deviceProfile();
            client.put("brand", dp.optString("brand", ""));
            client.put("model", dp.optString("model", ""));
            client.put("os", dp.optString("os", ""));
            client.put("install_id", deviceId == null ? "" : deviceId);
            client.put("re", A2SignatureSpoof.getBorrowedEnvSignal());  // 借官方眼睛弱信号（非封因，服务器弱权重）
            body.put("client", client);
        } catch (Throwable t) {
            return false;
        }
        String prevError = sLastErrorCode;
        String prevMsg = sLastErrorMessage;
        String prevDev = sLastDeviceShort;
        try {
            for (String base : AppConfig.guardServerList()) {
                String resp = post(base + "/api/v1/guard/checkin", body.toString());
                if (resp == null) continue;
                try {
                    JSONObject j = new JSONObject(resp);
                    if ("ok".equals(j.optString("status"))) return true;
                } catch (Throwable ignore) {
                    // malformed body -> try next server
                }
            }
            return false;
        } finally {
            // checkin 是遥测：不得污染激活/取信封的错误码回显（与 reportHealth 同款保护）。
            sLastErrorCode = prevError;
            sLastErrorMessage = prevMsg;
            sLastDeviceShort = prevDev;
        }
    }

    private static volatile JSONObject sDeviceProfile;

    /**
     * 静态设备画像（memoize，读一次）。全是 android.os.Build 公开静态字段——
     * 非 ro.boot.* 系统属性读取（守铁律 5），每机唯一稳定、零登录依赖。
     */
    private static JSONObject deviceProfile() {
        JSONObject v = sDeviceProfile;
        if (v != null) return v;
        v = new JSONObject();
        try {
            v.put("brand", android.os.Build.BRAND == null ? "" : android.os.Build.BRAND);
            v.put("model", android.os.Build.MODEL == null ? "" : android.os.Build.MODEL);
            String rel = android.os.Build.VERSION.RELEASE;
            v.put("os", rel == null ? "" : rel);
        } catch (Throwable ignore) {
            // 任一字段取不到 → 该字段留空，不阻塞上报
        }
        sDeviceProfile = v;
        return v;
    }

    /** 当前登录账号显示标识：微信号(alias)优先，空则原始 wxid；取不到返回 ""。后台用于换号识别 + 画像。 */
    private static String currentAcct() {
        try {
            Bridge b = Bridge.getInstance();
            String alias = b.getMyAlias();
            if (alias != null && !alias.isEmpty()) return alias;
            String wxid = b.getMyWxid();
            return wxid == null ? "" : wxid;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 在后台线程跑整套激活/取信封流程，避免主线程网络。 */
    public static void runAsync(Runnable r) {
        new Thread(r, "guard-env").start();
    }

    // ── 内部：单次 https POST ─────────────────────────────────

    private static String post(String urlStr, String json) {
        if (urlStr == null || !urlStr.startsWith("https://")) {
            // 强制 https：明文会泄露信封里的短命 key 材料 k
            Log.w(TAG, "[env] refuse non-https endpoint");
            sLastErrorCode = "BAD_SCHEME";
            return null;
        }
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(out);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code >= 200 && code < 300) return resp;
            Log.w(TAG, "[env] http " + code);
            return resp;
        } catch (Throwable t) {
            // 连接失败 / TLS 证书无效 / 超时 → fail-closed，交上层 fallback
            Log.w(TAG, "[env] post err: " + t.getClass().getSimpleName());
            sLastErrorCode = t.getClass().getSimpleName();
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignore) {}
            }
        }
    }

    private static String readAll(InputStream is) {
        if (is == null) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[1024];
            int n;
            while ((n = br.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > 65536) break; // 信封很小，封个上限防异常超大响应
            }
        } catch (Throwable t) {
            return null;
        }
        return sb.toString();
    }
}
