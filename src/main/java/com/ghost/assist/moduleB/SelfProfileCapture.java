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
 * 采集方式：hook View.onAttachedToWindow，过滤 id=0x7f0c6c6d（资源名 ouv = 微信号 TextView）。
 * 昵称：优先走 ContactResolver.resolveJson(wxid)，失败时留空等下次。
 *
 * 一次采集持久化，终生可用（Bridge KEY_MY_NICK / KEY_MY_ALIAS）。
 */
public class SelfProfileCapture {

    private static final String TAG = "NCL";

    // 资源 id 0x7f0c6c6d = "ouv"，"我"页面顶部"微信号：xxx"TextView
    private static final int VIEW_ID_ALIAS = 0x7f0c6c6d;

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook TextView.setText(CharSequence) — 数据填充时即触发，无 onAttachedToWindow 时序问题。
            // 过滤 id=0x7f0c6c6d + 文本含"微信号："前缀 → 提取 alias。
            XposedHelpers.findAndHookMethod(
                TextView.class, "setText",
                CharSequence.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        TextView tv = (TextView) param.thisObject;
                        if (tv.getId() != VIEW_ID_ALIAS) return;
                        if (!Bridge.getInstance().getMyAlias().isEmpty()) return;

                        CharSequence cs = (CharSequence) param.args[0];
                        if (cs == null) return;
                        String raw = cs.toString().trim();
                        if (raw.isEmpty()) return;

                        // "微信号：MarkDno" → "MarkDno"
                        String alias = raw.contains("：")
                                ? raw.substring(raw.indexOf('：') + 1).trim()
                                : raw;
                        if (alias.isEmpty()) return;

                        Bridge.getInstance().setMyAlias(alias);
                        Log.i(TAG, "[SPC] alias=" + alias);

                        // 用户已在"我"Tab，补刷 wxid 并解析昵称
                        Bridge.getInstance().refreshWxid();
                        tryResolveNickname();
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
