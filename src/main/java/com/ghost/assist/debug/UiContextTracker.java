package com.ghost.assist.debug;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Tracks current WeChat Activity → logical page for the debug console.
 */
public final class UiContextTracker {

    private static final String TAG = "NCL";
    private static volatile boolean sInstalled = false;

    /** 最近前台 Activity，供 SearchUnlock 调用 finish() */
    private static volatile Activity sCurrentActivity = null;

    public static Activity getCurrentActivity() { return sCurrentActivity; }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        try {
            XposedHelpers.findAndHookMethod(Application.class, "registerActivityLifecycleCallbacks",
                    Application.ActivityLifecycleCallbacks.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // WeChat registers its own; we hook Activity directly instead
                        }
                    });
        } catch (Throwable ignored) {}

        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity act = (Activity) param.thisObject;
                    sCurrentActivity = act;
                    String cn = act.getClass().getName();
                    String page = inferPage(cn);
                    String tab = inferTab(cn, act);
                    String title = safeTitle(act);

                    DebugTelemetry.getInstance().setPageContext(cn, page, tab);
                    DebugTelemetry.getInstance().emit("page", "enter",
                            DebugTelemetry.fields(
                                    "activity", cn,
                                    "page", page,
                                    "tab", tab,
                                    "title", title));

                    Log.i(TAG, "[UI] page=" + page + " activity=" + cn);
                }
            });
            Log.i(TAG, "[UI] UiContextTracker ok");
        } catch (Throwable t) {
            Log.w(TAG, "[UI] install failed: " + t);
        }
    }

    private static String inferPage(String activityCn) {
        String low = activityCn.toLowerCase();
        if (low.contains("sns") && (low.contains("timeline") || low.contains("snsui")))
            return "Moments";
        if (low.contains("sns")) return "Moments";
        if (low.contains("conversation") || low.contains("chatting")) return "Chat";
        if (low.contains("address") || low.contains("contact")) return "Address";
        if (low.contains("finder")) return "Finder";
        if (low.contains("launcherui") || low.contains("mainui")) return "Main";
        if (low.contains("login")) return "Login";
        if (low.contains("settings") || low.contains("setting")) return "Settings";
        if (low.contains("profile") || low.contains("contactinfo")) return "Profile";
        return activityCn;
    }

    private static String inferTab(String activityCn, Activity act) {
        try {
            CharSequence t = act.getTitle();
            if (t != null && t.length() > 0) return t.toString();
        } catch (Throwable ignored) {}
        return "";
    }

    private static String safeTitle(Activity act) {
        try {
            CharSequence t = act.getTitle();
            if (t == null) return "";
            String s = t.toString();
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        } catch (Throwable ignored) {
            return "";
        }
    }
}
