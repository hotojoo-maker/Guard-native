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

    /** SHA-256(ANDROID_ID) 取前 8 字节十六进制 */
    public static String computeDeviceHash(Context ctx) {
        try {
            String androidId = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.isEmpty()) return "unknown";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(androidId.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** 日志脱敏：只显示前4位 + *** */
    private static String mask(String s) {
        if (s == null || s.length() < 4) return "****";
        return s.substring(0, 4) + "***";
    }
}
