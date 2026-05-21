package com.ghost.assist.debug;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Floating overlay via WindowManager.addView() directly.
 * No Service — LSPosed module runs inside WeChat process.
 *
 * Requires SYSTEM_ALERT_WINDOW: Android 6+ needs runtime grant.
 * On DEV mode, auto-prompt if not granted.
 */
public class OverlayWindow {

    private static final String TAG = "NCL";
    private static Application sApp;
    private static WindowManager sWM;
    private static LinearLayout sRootView;
    private static TextView sEventText;
    private static Handler sHandler;
    private static Runnable sRefreshTask;
    private static boolean sAttached = false;

    public static void attach(Application app) {
        if (sAttached) return;
        if (!AppConfig.getInstance().isOverlayEnabled()) return;

        sApp = app;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(app)) {
                // DEV mode: guide user to grant overlay permission
                if (AppConfig.getInstance().getMode() == AppConfig.Mode.DEV) {
                    Log.w(TAG, "[OV] overlay permission not granted — prompt user");
                    promptOverlayPermission(app);
                }
                return;
            }
        }

        sHandler = new Handler(Looper.getMainLooper());
        createOverlay(app);
        startRefreshLoop();
        sAttached = true;
        Log.i(TAG, "[OV] overlay attached");
    }

    public static void detach() {
        if (sRefreshTask != null && sHandler != null) {
            sHandler.removeCallbacks(sRefreshTask);
        }
        if (sRootView != null && sWM != null) {
            try { sWM.removeView(sRootView); } catch (Exception ignored) {}
        }
        sRootView = null;
        sWM = null;
        sAttached = false;
    }

    public static boolean isAttached() { return sAttached; }

    public static void showToast(Context ctx, String msg) {
        if (sApp != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(sApp, msg, Toast.LENGTH_SHORT).show());
        }
    }

    private static void promptOverlayPermission(Application app) {
        // On Android 6+ we need to guide user to enable overlay permission manually.
        // Show a Toast with instructions.
        Toast.makeText(app,
            "DEV: 请到 设置→应用→权限→悬浮窗 为微信开启悬浮窗权限",
            Toast.LENGTH_LONG).show();
    }

    private static void createOverlay(Application app) {
        sWM = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

        int overlayType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 8;
        params.y = 120;

        // Build layout
        sRootView = new LinearLayout(app);
        sRootView.setOrientation(LinearLayout.VERTICAL);
        sRootView.setBackgroundColor(0x88000000);
        sRootView.setPadding(8, 8, 8, 8);

        // Header
        TextView header = new TextView(app);
        header.setText("事件流");
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(11);
        header.setPadding(0, 0, 0, 4);
        sRootView.addView(header);

        // Scrollable event area
        ScrollView scrollView = new ScrollView(app);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            dpToPx(app, 220), dpToPx(app, 120)));

        sEventText = new TextView(app);
        sEventText.setTextColor(0xFFCCFFCC);
        sEventText.setTextSize(9);
        sEventText.setPadding(4, 4, 4, 4);
        scrollView.addView(sEventText);
        sRootView.addView(scrollView);

        // Close button
        TextView closeBtn = new TextView(app);
        closeBtn.setText("[X]");
        closeBtn.setTextColor(0xFFFF6666);
        closeBtn.setTextSize(10);
        closeBtn.setGravity(Gravity.END);
        closeBtn.setOnClickListener(v -> {
            detach();
            AppConfig.getInstance().setOverlayEnabled(false);
        });
        sRootView.addView(closeBtn);

        sWM.addView(sRootView, params);
    }

    private static void startRefreshLoop() {
        sRefreshTask = new Runnable() {
            @Override
            public void run() {
                refreshEvents();
                sHandler.postDelayed(this, 1000);
            }
        };
        sHandler.post(sRefreshTask);
    }

    private static void refreshEvents() {
        if (sEventText == null) return;
        StateMachine sm = StateMachine.getInstance();
        InterceptCounter ic = InterceptCounter.getInstance();

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

        sb.append("状态: ").append(sm.getStateName())
            .append(" | 拦截: ").append(ic.getTotal()).append("\n");
        sb.append("F04=").append(ic.getF04())
            .append(" F05=").append(ic.getF05())
            .append(" F07=").append(ic.getF07()).append("\n");
        sb.append("────────────────\n");

        LinkedList<InterceptCounter.Event> events = ic.getRecentEvents();
        int shown = 0;
        for (InterceptCounter.Event e : events) {
            if (shown >= 15) break;
            sb.append(sdf.format(new Date(e.timestamp)))
                .append(" ").append(e.type)
                .append(" ").append(e.detail).append("\n");
            shown++;
        }
        if (events.isEmpty()) {
            sb.append("(暂无事件)\n");
        }

        sEventText.setText(sb.toString());
    }

    private static int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
