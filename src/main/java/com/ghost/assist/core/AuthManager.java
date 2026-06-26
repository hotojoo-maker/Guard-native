package com.ghost.assist.core;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;

/**
 * P4-1 授权管理器 — wxid + 设备双绑定
 *
 * ═══════════════════════════════════════════════════════════════
 * 架构铁律：授权 ≠ 状态机，两者完全独立
 *   - AuthManager 决定功能能不能用（AUTH_OK / MISMATCH / TAMPERED）
 *   - StateMachine 决定现在是 HIDDEN 还是 VISIBLE
 *   - AuthManager 不得调用 StateMachine 的任何转换方法
 *   - 授权结果只写入 NativeBridge.setAuthState()，不触发显隐切换
 * ═══════════════════════════════════════════════════════════════
 *
 * 铁律 §6.10：
 *   AUTH_OK               = licensedWxid 匹配 && deviceHash 匹配
 *   AUTH_ACCOUNT_MISMATCH = 设备对，wxid 不匹配 → v1 放行
 *   AUTH_DEVICE_MISMATCH  = wxid 对，设备不匹配 → v1 放行
 *   AUTH_NO_LICENSE       = 首次安装，未绑定    → v1 放行
 *   AUTH_TAMPERED         = 包名/证书被篡改     → 功能全关 + PiracyNotice 引流
 *
 * v2：替换为 Ed25519 LicenseBox 服务端验签。
 */
public class AuthManager {

    private static final String TAG = "NCL";

    /** 在冷启动 Bridge.init() 之后调用，结果推入 C++ AuthEngine。 */
    public static int evaluate(Context ctx) {
        try {
            Bridge bridge = Bridge.getInstance();
            String licensedWxid = bridge.getLicensedWxid();

            // 未绑定
            if (licensedWxid.isEmpty()) {
                Log.i(TAG, "[auth] NO_LICENSE — not bound yet");
                return NativeBridge.AUTH_NO_LICENSE;
            }

            String currentWxid   = bridge.getMyWxid();
            String storedDevHash = bridge.getDeviceHash();
            String currentDev    = computeDeviceHash(ctx);

            boolean wxidOk = licensedWxid.equals(currentWxid);
            // 首次绑定时 deviceHash 可能为空（迁移兼容），视为通过
            boolean devOk  = storedDevHash.isEmpty() || storedDevHash.equals(currentDev);

            if (wxidOk && devOk) {
                Log.i(TAG, "[auth] AUTH_OK wxid=" + mask(currentWxid));
                return NativeBridge.AUTH_OK;
            }
            if (!wxidOk && devOk) {
                Log.w(TAG, "[auth] ACCOUNT_MISMATCH current=" + mask(currentWxid)
                        + " licensed=" + mask(licensedWxid));
                return NativeBridge.AUTH_ACCOUNT_MISMATCH;
            }
            if (wxidOk) {
                Log.w(TAG, "[auth] DEVICE_MISMATCH");
                return NativeBridge.AUTH_DEVICE_MISMATCH;
            }
            // 两者都不匹配（换机且换号）
            Log.w(TAG, "[auth] ACCOUNT_MISMATCH (both differ)");
            return NativeBridge.AUTH_ACCOUNT_MISMATCH;

        } catch (Throwable t) {
            Log.e(TAG, "[auth] evaluate crash: " + t);
            return NativeBridge.AUTH_UNKNOWN;
        }
    }

    /**
     * 激活绑定 — 将当前 myWxid + 设备 hash 存为授权身份。
     * 通过 DebugServer /api/bind_account 触发；成功后需冷启动生效。
     *
     * @return true 绑定成功；false myWxid 未设置
     */
    public static boolean bindAccount(Context ctx) {
        try {
            Bridge bridge = Bridge.getInstance();
            String myWxid = bridge.getMyWxid();
            if (myWxid == null || myWxid.isEmpty()) {
                Log.w(TAG, "[auth] bindAccount: myWxid not set");
                return false;
            }
            String devHash = computeDeviceHash(ctx);
            bridge.setLicensedWxid(myWxid);
            bridge.setDeviceHash(devHash);
            // 即时同步 C++（本次进程立即生效，下次冷启动也会走 evaluate 确认）
            NativeBridge.setAuthState(NativeBridge.AUTH_OK);
            Log.i(TAG, "[auth] bound wxid=" + mask(myWxid) + " dev=" + devHash);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "[auth] bindAccount crash: " + t);
            return false;
        }
    }

    // 设备材料唯一读点（memoize）。A2 同源隔离：A2-3 的 android_id spoof 只 hook
    // getPackageInfo/SSAID 读取者，绝不污染本读点；computeDeviceHash/Material 都走这里。
    private static volatile String sRawAndroidId;

    /** 真 ANDROID_ID 唯一读点（memoize；取不到返回 ""）。 */
    public static String rawAndroidId(Context ctx) {
        String v = sRawAndroidId;
        if (v != null) return v;
        try {
            String id = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            v = (id == null) ? "" : id;
        } catch (Throwable t) {
            v = "";
        }
        sRawAndroidId = v;
        return v;
    }

    /** SHA-256(ANDROID_ID) 取前 8 字节十六进制（device_id；= dm 前 8B）。 */
    public static String computeDeviceHash(Context ctx) {
        try {
            String androidId = rawAndroidId(ctx);
            if (androidId.isEmpty()) return "unknown";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(androidId.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * 牙③ W_dev 设备材料 D_mat = SHA-256(ANDROID_ID) 全 32 字节（前 8B == device_id，
     * 服务器 Batch 2 自校验基准 dm[:16]==device_id）。与 computeDeviceHash 共用 rawAndroidId
     * 唯一读点；推给 SO set_device_material 折进 W_dev。取不到 → null（不设 dm，双试自动
     * 回退全局 W，不阻塞正版）。
     */
    public static byte[] computeDeviceMaterial(Context ctx) {
        try {
            String androidId = rawAndroidId(ctx);
            if (androidId.isEmpty()) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(androidId.getBytes("UTF-8")); // 32B
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * dm = hex(SHA-256(ANDROID_ID)) 全 32B → 64 hex（出站请求字段，牙③ 服务器逐设备 wrap 依据）。
     * 前 16 hex == computeDeviceHash（服务器 Batch 2 自校验基准 dm[:16]==device_id）。
     * 走 computeDeviceMaterial → rawAndroidId 单一读点；取不到 → ""（不带 dm，双试回退全局 W，不阻塞正版）。
     */
    public static String computeDeviceMaterialHex(Context ctx) {
        byte[] dm = computeDeviceMaterial(ctx);
        if (dm == null) return "";
        StringBuilder sb = new StringBuilder(dm.length * 2);
        for (byte b : dm) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 日志脱敏：只显示前4位 + *** */
    private static String mask(String s) {
        if (s == null || s.length() < 4) return "****";
        return s.substring(0, 4) + "***";
    }
}
