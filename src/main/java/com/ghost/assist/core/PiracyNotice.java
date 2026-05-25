package com.ghost.assist.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * P4-1 盗版引流弹窗
 *
 * 触发条件：AUTH_TAMPERED（包名/证书不匹配，篡改版本）
 * 行为：AlertDialog → 点击"前往正版" → 浏览器打开 SHOP_URL
 *
 * 铁律 §6.10：
 *   AUTH_ACCOUNT_MISMATCH 不弹（不暴露模块存在）
 *   AUTH_NO_LICENSE 不弹（首次安装正常用户）
 *   只有 AUTH_TAMPERED 才弹（确认篡改才引流）
 *
 * P4-2：title/message/url 改由服务端签名包下发，本类保留为展示层。
 */
public class PiracyNotice {

    private static final String TAG = "NCL";

    public static void showIfTampered(Context ctx, int authState) {
        if (authState != NativeBridge.AUTH_TAMPERED) return;
        show(ctx,
             "功能异常",
             "检测到版本异常，部分功能已停用。\n前往官方渠道获取完整授权版本。",
             "前往官方渠道",
             AppConfig.SHOP_URL);
    }

    /**
     * 通用弹窗入口 — P4-2 由服务端 notice 字段驱动时直接调此方法。
     *
     * @param title   弹窗标题
     * @param message 正文
     * @param btnText 按钮文字
     * @param url     点击后跳转的 URL
     */
    public static void show(Context ctx,
                            String title,
                            String message,
                            String btnText,
                            String url) {
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                new AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(btnText, (d, w) -> {
                        try {
                            ctx.startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Throwable t) {
                            Log.w(TAG, "[notice] openURL fail: " + t);
                        }
                    })
                    .setNegativeButton("关闭", (d, w) -> d.dismiss())
                    .show();
                Log.i(TAG, "[notice] shown url=" + url);
            } catch (Throwable t) {
                Log.w(TAG, "[notice] show fail: " + t);
            }
        });
    }
}
