package com.ghost.assist.debug;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;

/**
 * Persistent notification via NotificationManager directly.
 * No Service — LSPosed module runs inside WeChat process, which has
 * no visibility into our module's AndroidManifest service registrations.
 */
public class StatusNotification {

    private static final String TAG = "NCL";
    private static final String CHANNEL_ID = "ncl_sts";
    private static final int NOTIFY_ID = 9001;
    // v1 手动改 seed：发版前将此字符串替换为当次种子的 4 字符前缀，不得保留明文项目名
    private static final String NTF_PFX = "SYS";

    private static NotificationManager sNM;
    private static Application sApp;

    public static void show(Application app) {
        sApp = app;
        if (sNM == null) {
            sNM = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "系统状态", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("模块运行状态");
                sNM.createNotificationChannel(ch);
            }
        }
        sNM.notify(NOTIFY_ID, buildNotification());
        Log.i(TAG, "[NF] notification posted");

        StateMachine.getInstance().addListener(mStateListener);
    }

    public static void refresh() {
        if (sNM != null && sApp != null) {
            sNM.notify(NOTIFY_ID, buildNotification());
        }
    }

    public static void dismiss() {
        StateMachine.getInstance().removeListener(mStateListener);
        if (sNM != null) {
            sNM.cancel(NOTIFY_ID);
        }
    }

    private static Notification buildNotification() {
        StateMachine sm = StateMachine.getInstance();
        InterceptCounter ic = InterceptCounter.getInstance();

        String title = "[" + NTF_PFX + "] 状态=" + sm.getStateName()
            + " / 拦截 " + ic.getTotal() + " / 密友 0";

        String content = "F04=" + ic.getF04()
            + " F05=" + ic.getF05()
            + " F07=" + ic.getF07();

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(sApp, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(sApp);
        }

        return builder
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build();
    }

    private static final StateMachine.StateListener mStateListener = (oldState, newState) -> {
        Log.i(TAG, "[NF] state " + oldState.label + "→" + newState.label);
        refresh();
    };
}
