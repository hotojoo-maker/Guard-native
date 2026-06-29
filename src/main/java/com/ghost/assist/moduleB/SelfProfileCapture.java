package com.ghost.assist.moduleB;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.debug.ContactResolver;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 自动采集当前登录用户的昵称 + 微信号。
 *
 * 触发时机：用户进入"我"Tab，页面顶部 Profile 区域展示了资料。
 * 采集方式：hook TextView.setText，按文本"微信号：xxx"命中（8.0.71 起微信资源 id 每版重排，
 *           写死 id 失效，故改按文本识别、与版本解耦）。
 * 昵称：优先走 ContactResolver.resolveJson(wxid)，失败时留空等下次。
 *
 * 一次采集持久化，终生可用（Bridge KEY_MY_NICK / KEY_MY_ALIAS）。
 */
public class SelfProfileCapture {

    private static final String TAG = "NCL";

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook TextView.setText(CharSequence) — 数据填充时即触发，无时序问题。
            // 8.0.71 起资源 id 每版重排，旧写死 id 失效；改按"我"页文本"微信号：xxx"命中，与版本解耦。
            XposedHelpers.findAndHookMethod(
                TextView.class, "setText",
                CharSequence.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!Bridge.getInstance().getMyAlias().isEmpty()) return;

                            CharSequence cs = (CharSequence) param.args[0];
                            if (cs == null) return;
                            String raw = cs.toString().trim();
                            // 只认"微信号"开头 + 含冒号（全角／半角都兼容）的那一栏
                            if (raw.length() < 4 || !raw.startsWith("\u5fae\u4fe1\u53f7")) return;
                            int colon = raw.indexOf('\uff1a');
                            if (colon < 0) colon = raw.indexOf(':');
                            if (colon < 0) return;

                            // "微信号：MarkDno" → "MarkDno"
                            String alias = raw.substring(colon + 1).trim();
                            if (alias.isEmpty() || alias.length() > 64) return;

                            Bridge.getInstance().setMyAlias(alias);
                            Log.i(TAG, "[SPC] alias=" + alias);

                            // 用户已在"我"Tab，补刷 wxid 并解析昵称
                            Bridge.getInstance().refreshWxid();
                            tryResolveNickname();
                        } catch (Throwable ignored) {}
                    }
                });
            Log.i(TAG, "[SPC] setText hook installed");
        } catch (Throwable e) {
            Log.w(TAG, "[SPC] install fail: " + e);
        }
    }

    /**
     * 用 ContactResolver 解析昵称并写入 Bridge。
     * 可在任意线程调用；ContactResolver 内部已做异常保护。
     */
    public static void tryResolveNickname() {
        String wxid = Bridge.getInstance().getMyWxid();
        if (wxid.isEmpty()) return;
        if (!Bridge.getInstance().getMyNick().isEmpty()) return; // 已有昵称

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String json = ContactResolver.resolveJson(wxid);
                // 简单解析 "nickname":"..." 字段
                String nick = extractJsonString(json, "nickname");
                if (nick != null && !nick.isEmpty() && !nick.equals(wxid)) {
                    Bridge.getInstance().setMyNick(nick);
                    Log.i(TAG, "[SPC] nick=" + nick);
                }
            } catch (Throwable t) {
                Log.w(TAG, "[SPC] resolveNickname fail: " + t);
            }
        });
    }

    /** 极简 JSON 字段提取，避免引入 JSONObject 依赖 */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\":\"";
        int i = json.indexOf(search);
        if (i < 0) return null;
        int start = i + search.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }
}
