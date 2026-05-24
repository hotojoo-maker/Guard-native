package com.ghost.assist.moduleB;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.StateMachine;

/**
 * 状态机自动触发器（P21 优先 #2）。
 *
 * B1 摇一摇 (SensorManager 加速度，默认关，阈值 15 m/s²)
 * B2 切后台 (Activity 生命周期进程级前台计数，默认开)
 *      + 兜底广播 CLOSE_SYSTEM_DIALOGS (Home/手势上划/Recent，跟 B2 同开关)
 * B5 锁屏   (BroadcastReceiver ACTION_SCREEN_OFF，默认开)
 *
 * 触发策略：B1/B2/B5 全部单向 → HIDDEN（PRODUCT_GATE §8.1 铁律）
 *
 * 注意：
 *   - onActivityStopped 才是"完全离开前台"，onActivityPaused 是局部切换（点开聊天页也 pause）
 *   - 触发后 enterHidden() 立即同步落盘（Bridge.putInt commit）
 *   - 早返：DEV 模式（非 PROD）禁用所有触发器，避免开发调试时被自动隐藏打断
 */
public class TriggerGuard {

    private static final String TAG = "NCL";
    private static final float SHAKE_THRESHOLD = 15.0f; // m/s²，仿 Catfish ShakeHandler
    private static final long SHAKE_COOLDOWN_MS = 1500; // 摇一次的冷却时间，避免连续触发

    private static boolean sInstalled = false;
    private static int sForegroundCount = 0;
    private static volatile long sLastShakeAt = 0L;
    private static SensorManager sSensorManager;
    private static SensorEventListener sShakeListener;
    private static BroadcastReceiver sScreenReceiver;

    public static void install(Application app) {
        if (sInstalled) return;
        sInstalled = true;

        installForegroundTracker(app);          // B2
        installScreenAndCloseDialogReceiver(app); // B5 + B2 兜底
        installShakeListener(app);               // B1

        Log.i(TAG, "[TG] install done b1=" + AppConfig.getInstance().isB1Enabled()
                + " b2=" + AppConfig.getInstance().isB2Enabled()
                + " b5=" + AppConfig.getInstance().isB5Enabled()
                + " dev=" + AppConfig.getInstance().isDevMode());
    }

    // -------------------------------------------------------------------------
    // B2 — Activity 生命周期前台计数器（最可靠）
    // -------------------------------------------------------------------------
    private static void installForegroundTracker(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                sForegroundCount++;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (sForegroundCount > 0) sForegroundCount--;
                // isChangingConfigurations() = true when Activity.recreate() is called or
                // during screen rotation. Neither is a user "go to background" intent.
                // Skip B2 to avoid false enterHidden during H→V recreate().
                if (sForegroundCount == 0 && !activity.isChangingConfigurations()) {
                    onLeftForeground("B2-stopped:" + activity.getClass().getSimpleName());
                }
            }

            // 其他生命周期空实现 ----------
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        Log.i(TAG, "[TG] B2 foreground tracker installed");
    }

    private static void onLeftForeground(String reason) {
        if (!AppConfig.getInstance().isB2Enabled()) return;
        if (AppConfig.getInstance().isDevMode()) return; // DEV 模式开发不打扰
        StateMachine sm = StateMachine.getInstance();
        if (sm.isActive()) return; // 已是 HIDDEN 不重复触发
        Log.i(TAG, "[TG] " + reason + " → enterHidden");
        sm.enterHidden();
    }

    // -------------------------------------------------------------------------
    // B5 锁屏 + B2 兜底 (CLOSE_SYSTEM_DIALOGS = Home/Recent/手势上划)
    // -------------------------------------------------------------------------
    private static void installScreenAndCloseDialogReceiver(Application app) {
        sScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (!AppConfig.getInstance().isB5Enabled()) return;
                    if (AppConfig.getInstance().isDevMode()) return;
                    StateMachine sm = StateMachine.getInstance();
                    if (sm.isActive()) return;
                    Log.i(TAG, "[TG] B5-screen_off → enterHidden");
                    sm.enterHidden();
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    // B2 兜底：Home / 手势上划 / Recent
                    if (!AppConfig.getInstance().isB2Enabled()) return;
                    if (AppConfig.getInstance().isDevMode()) return;
                    StateMachine sm = StateMachine.getInstance();
                    if (sm.isActive()) return;
                    String reason = intent.getStringExtra("reason");
                    Log.i(TAG, "[TG] B2-close_dialogs(" + reason + ") → enterHidden");
                    sm.enterHidden();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        try {
            // Android 13+ 要求显式标记 receiver 导出性
            int flag = 0;
            try {
                java.lang.reflect.Field f = Context.class.getField("RECEIVER_NOT_EXPORTED");
                flag = f.getInt(null);
            } catch (Throwable ignored) {}
            if (flag != 0) {
                java.lang.reflect.Method m = Context.class.getMethod(
                        "registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(app, sScreenReceiver, filter, flag);
            } else {
                app.registerReceiver(sScreenReceiver, filter);
            }
            Log.i(TAG, "[TG] B5 + B2-fallback receiver installed");
        } catch (Throwable t) {
            Log.w(TAG, "[TG] receiver install failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // B1 摇一摇（默认关，用户开关启用后才注册 Sensor）
    // -------------------------------------------------------------------------
    private static void installShakeListener(Application app) {
        if (!AppConfig.getInstance().isB1Enabled()) {
            Log.i(TAG, "[TG] B1 shake disabled, skip");
            return;
        }
        try {
            sSensorManager = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
            if (sSensorManager == null) return;
            Sensor accel = sSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel == null) {
                Log.w(TAG, "[TG] B1 no accelerometer on device");
                return;
            }
            sShakeListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.values.length < 3) return;
                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];
                    float gForce = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
                    if (gForce < 1.5f) return; // 1G + 0.5G 抖动阈值（≈ 15 m/s²）
                    long now = SystemClock.elapsedRealtime();
                    if (now - sLastShakeAt < SHAKE_COOLDOWN_MS) return;
                    sLastShakeAt = now;
                    onShake();
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
            sSensorManager.registerListener(sShakeListener, accel, SensorManager.SENSOR_DELAY_UI);
            Log.i(TAG, "[TG] B1 shake listener installed");
        } catch (Throwable t) {
            Log.w(TAG, "[TG] B1 shake install failed: " + t);
        }
    }

    private static void onShake() {
        if (AppConfig.getInstance().isDevMode()) return;
        StateMachine sm = StateMachine.getInstance();
        if (sm.isActive()) return;
        Log.i(TAG, "[TG] B1-shake → enterHidden");
        sm.enterHidden();
    }
}
