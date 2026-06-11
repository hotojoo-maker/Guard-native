package com.ghost.assist.core;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * FunnelPrompt — 引流弹窗【展示层】（段1 新建；旧 PiracyNotice 一点不动）。
 *
 * ═══════════════════════════════════════════════════════════════
 * UI（用户要求 2026-06-12）：圆角卡片、显示落地页链接、「复制链接」+「前往」双按钮。
 *   • 程序化构图（不依赖宿主资源），用 GradientDrawable 画圆角，window 背景透明
 *     去掉系统方框 → 真圆角卡片。
 *   • 「复制链接」：写剪贴板 + Toast，浏览器打不开时用户可手动粘贴。
 *   • 「前往」：浏览器直接打开落地页。
 *
 * 边界铁律（安全官硬红线 #9 / §10.8 / 铁律 24）：
 *   • 只是展示层，不是策略源；弹不弹只由唯一出口 RiskPromptController 决定。
 *   • 落地页 URL 不在 Java 明文常量——由 RiskPromptController 从 SO 加密引导段
 *     (NativeBridge.getEndpoint("funnel")) 取出传入。运行时显示给用户（引流目的）
 *     ≠ 静态明文：APK/SO 里 grep 不到该 URL。
 *   • 用 Dialog（不用 Service）；不清用户数据、不碰微信本体。
 * ═══════════════════════════════════════════════════════════════
 */
public final class FunnelPrompt {

    private static final String TAG = "NCL";

    private FunnelPrompt() {}

    /**
     * 画引流弹窗。必须传 Activity context（Application context 画 Dialog 会 token null）。
     * 文案由调用方（RiskPromptController）按风险等级传入：确认盗版→吓人文案；
     * 离线/过期→软文案（不误伤付费客户，红线）。
     *
     * @param ctx     前台 Activity context（RiskPromptController 从前台触发点保证）
     * @param url     落地页 URL（已从 SO 引导段解出）；空则回退 AppConfig.SHOP_URL
     * @param title   标题
     * @param message 正文
     */
    public static void show(Context ctx, String url, String title, String message) {
        if (ctx == null) return;
        final String target = (url == null || url.isEmpty()) ? AppConfig.SHOP_URL : url;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                AlertDialog dialog = new AlertDialog.Builder(ctx)
                        .setView(buildCard(ctx, target, title, message))
                        .setCancelable(true)   // 用户要求「可关掉」；再弹由冷却控制
                        .create();
                if (dialog.getWindow() != null) {
                    // 去掉系统方框白底 → 只露我们的圆角卡片
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
                wireButtons(ctx, dialog, target);
                dialog.show();
                Log.i(TAG, "[funnel] shown");
            } catch (Throwable t) {
                Log.w(TAG, "[funnel] show fail: " + t.getClass().getSimpleName());
            }
        });
    }

    // 待绑定的按钮（buildCard 创建，wireButtons 绑事件——拿到 dialog 引用才能 dismiss）。
    private static TextView sCopyBtn;
    private static TextView sGoBtn;

    private static View buildCard(Context ctx, String url, String titleText, String messageText) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 22);
        card.setPadding(pad, dp(ctx, 24), pad, dp(ctx, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(ctx, 18));
        card.setBackground(bg);
        // 卡片本身留左右外边距，避免贴边
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);

        TextView title = new TextView(ctx);
        title.setText(titleText == null || titleText.isEmpty() ? "温馨提示" : titleText);
        title.setTextColor(0xFF1A1A1A);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView msg = new TextView(ctx);
        msg.setText(messageText == null || messageText.isEmpty()
                ? "检测到当前为非官方版本，部分功能可能不稳定。\n前往官方渠道获取完整版本，或复制下方链接在浏览器打开。"
                : messageText);
        msg.setTextColor(0xFF666666);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        msg.setLineSpacing(dp(ctx, 4), 1f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(ctx, 14);
        msg.setLayoutParams(msgLp);
        card.addView(msg);

        // URL 框（浅灰圆角，可选中复制）
        TextView urlView = new TextView(ctx);
        urlView.setText(url);
        urlView.setTextColor(0xFF1A73E8);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        urlView.setTextIsSelectable(true);
        int up = dp(ctx, 12);
        urlView.setPadding(up, dp(ctx, 10), up, dp(ctx, 10));
        GradientDrawable urlBg = new GradientDrawable();
        urlBg.setColor(0xFFF2F4F7);
        urlBg.setCornerRadius(dp(ctx, 10));
        urlView.setBackground(urlBg);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = dp(ctx, 14);
        urlView.setLayoutParams(urlLp);
        card.addView(urlView);

        // 按钮行
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(ctx, 20);
        row.setLayoutParams(rowLp);

        sCopyBtn = makeButton(ctx, "复制链接", 0xFFEFF3F8, 0xFF1A73E8);
        sGoBtn = makeButton(ctx, "前往", 0xFF1A73E8, Color.WHITE);
        LinearLayout.LayoutParams bl = (LinearLayout.LayoutParams) sCopyBtn.getLayoutParams();
        bl.rightMargin = dp(ctx, 10);
        sCopyBtn.setLayoutParams(bl);
        row.addView(sCopyBtn);
        row.addView(sGoBtn);
        card.addView(row);

        return card;
    }

    private static TextView makeButton(Context ctx, String text, int bgColor, int textColor) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(ctx, 12), 0, dp(ctx, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(ctx, 24));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        b.setClickable(true);
        return b;
    }

    private static void wireButtons(Context ctx, AlertDialog dialog, String url) {
        if (sCopyBtn != null) {
            sCopyBtn.setOnClickListener(v -> {
                try {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("link", url));
                    Toast.makeText(ctx, "链接已复制，去浏览器粘贴打开", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "[funnel] copied");
                } catch (Throwable t) {
                    Log.w(TAG, "[funnel] copy fail: " + t.getClass().getSimpleName());
                }
                // 复制后不关弹窗，方便用户接着点「前往」
            });
        }
        if (sGoBtn != null) {
            sGoBtn.setOnClickListener(v -> {
                try {
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    Log.w(TAG, "[funnel] open fail: " + t.getClass().getSimpleName());
                }
                try { dialog.dismiss(); } catch (Throwable ignored) {}
            });
        }
    }

    private static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
