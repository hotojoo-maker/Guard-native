package com.ghost.assist.moduleD;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.StateMachine;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 隐藏微信「设置」页的「存储空间」入口行。
 *
 * 授权分支（授权检查官 2026-06-30 裁决）：
 *   有授权(isVipAuthorized) + 隐身态(getState != VISIBLE) → 隐藏；否则恢复显示。
 *   绑授权 = 防白嫖；对齐防撤回 f2 范式，不绑 f1 密友开关 / config 配方门
 *   （存储空间隐藏非密友隐私链，且避免因 f1/config 误判失效）。
 *   只读 StateMachine，不写状态机（Filter 边界）。
 *
 * hook 点（Frida L1 实证 2026-06-30，截图 after_hide3.png）：
 *   Activity = com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
 *   列表 = WxRecyclerView(#lqa)；每行 = LinearLayout(#no-id) wrapper
 *          内含 [分组标题 TextView(#gzf) + 行主体 LinearLayout(#m7k)]
 *   行标题 = TextView(#title)，text == "存储空间"
 *
 * 方案（照 M6a MomentsGroupIconFilter 的 onResume + OnGlobalLayout 节流 sweep）：
 *   遍历 → 每个 id=="title" 的 TextView：
 *     是「存储空间」且该隐藏 → 上溯 #m7k → 取父 wrapper(RecyclerView item)
 *                              → GONE + layoutParams.height=0（彻底收起、无残留缝隙）
 *     其余情况 → 恢复 VISIBLE + height=WRAP_CONTENT
 *   按 text 逐行恢复可防 RecyclerView 复用继承 GONE/height=0 误伤别的行。
 */
public final class SettingsStorageHideGuard {

    private static final String TAG = "NCL";
    private static final String STORAGE_TEXT = "\u5b58\u50a8\u7a7a\u95f4"; // 存储空间
    private static final String TITLE_ID     = "title";
    private static final String ROW_BODY_ID  = "m7k";
    private static final String SETTINGS_PAGE = "SettingsUI"; // MainSettingsUI / CommonSettingsUI 兼容
    private static final long SWEEP_MIN_INTERVAL_MS = 60L;

    private static boolean sInstalled = false;
    private static volatile long sLastSweep = 0L;
    private static volatile boolean sLoggedHit = false;
    private static volatile WeakReference<View> sDecorRef;   // 当前设置页 decorView，供 RefreshBus 热切重扫
    private static final java.util.WeakHashMap<View, Boolean> sAttached = new java.util.WeakHashMap<>();

    private SettingsStorageHideGuard() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object act = param.thisObject;
                        if (!(act instanceof Activity)) return;
                        if (!act.getClass().getName().contains(SETTINGS_PAGE)) return;
                        final View root = ((Activity) act).getWindow().getDecorView();
                        sDecorRef = new WeakReference<>(root);
                        attachLayoutSweep(root);
                        sweep(root);
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override public void run() {
                                try { sweep(root); } catch (Throwable ignored) {}
                            }
                        }, 300);
                    } catch (Throwable ignored) {}
                }
            });
            Log.i(TAG, "[SSH] installed (onResume sweep on *SettingsUI)");
        } catch (Throwable t) {
            Log.w(TAG, "[SSH] install fail: " + t);
        }

        // 热切：H↔V 状态变化时（用户正停在设置页）立即重扫，存储空间随显隐切换。
        // RefreshBus 回调已在主线程派发，可直接操作 view。
        try {
            RefreshBus.getInstance().register("SettingsStorageHide", hidden -> {
                try {
                    View root = sDecorRef != null ? sDecorRef.get() : null;
                    if (root != null && root.isAttachedToWindow()) sweep(root);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            Log.w(TAG, "[SSH] RefreshBus register fail: " + t);
        }
    }

    /** 授权分支：有授权 + 隐身态(HIDDEN/UNLOCKING) → 该隐藏存储空间。 */
    private static boolean shouldHide() {
        StateMachine sm = StateMachine.getInstance();
        return sm.isVipAuthorized() && sm.getState() != StateMachine.State.VISIBLE;
    }

    private static void attachLayoutSweep(final View root) {
        if (root == null) return;
        synchronized (sAttached) {
            if (sAttached.containsKey(root)) return;
            sAttached.put(root, Boolean.TRUE);
        }
        try {
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            try {
                                long now = android.os.SystemClock.uptimeMillis();
                                if (now - sLastSweep < SWEEP_MIN_INTERVAL_MS) return;
                                sLastSweep = now;
                                sweep(root);
                            } catch (Throwable ignored) {}
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "[SSH] attach layout sweep fail: " + t);
        }
    }

    private static void sweep(View root) {
        if (root == null) return;
        walk(root, shouldHide());
    }

    private static void walk(View v, boolean hideStorage) {
        if (v == null) return;
        if (v instanceof TextView && TITLE_ID.equals(idName(v))) {
            try {
                CharSequence cs = ((TextView) v).getText();
                boolean isStorage = cs != null && STORAGE_TEXT.contentEquals(cs);
                View m7k = climbTo(v, ROW_BODY_ID);
                if (m7k != null) {
                    ViewParent pp = m7k.getParent();
                    View wrapper = (pp instanceof View) ? (View) pp : m7k;
                    applyHide(wrapper, isStorage && hideStorage);
                }
            } catch (Throwable ignored) {}
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) walk(g.getChildAt(i), hideStorage);
        }
    }

    private static void applyHide(View wrapper, boolean hide) {
        try {
            ViewGroup.LayoutParams lp = wrapper.getLayoutParams();
            if (hide) {
                if (wrapper.getVisibility() == View.GONE
                        && lp != null && lp.height == 0) return;
                wrapper.setVisibility(View.GONE);
                if (lp != null && lp.height != 0) {
                    lp.height = 0;
                    wrapper.setLayoutParams(lp);
                }
                if (!sLoggedHit) {
                    sLoggedHit = true;
                    Log.i(TAG, "[SSH] 存储空间 row hidden");
                }
            } else {
                if (wrapper.getVisibility() == View.VISIBLE
                        && lp != null && lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) return;
                wrapper.setVisibility(View.VISIBLE);
                if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    wrapper.setLayoutParams(lp);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 从 v 上溯，找 id entry 名 == targetId 的祖先 View（最多 12 层）。 */
    private static View climbTo(View v, String targetId) {
        View cur = v;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (targetId.equals(idName(cur))) return cur;
            ViewParent p = cur.getParent();
            if (!(p instanceof View)) return null;
            cur = (View) p;
        }
        return null;
    }

    private static String idName(View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID || id == 0) return null;
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return null;
        }
    }
}
