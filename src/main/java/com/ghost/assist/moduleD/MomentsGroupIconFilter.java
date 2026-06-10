package com.ghost.assist.moduleD;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.InterceptCounter;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * M6a — 隐藏「自己发的、仅可见分组 / 部分可见」朋友圈条目右下角那个图标。
 *
 * 磁盘实证（dumpsys activity top，2026-06-10，ImproveSnsTimelineUI pid=22339；
 * 原文存 gi_dump.txt L1775-1784）：
 *   条目根 ha4.q3/k4/s2 (app:id/n9a) > 正文 LinearLayout(app:id/n95)
 *     > ConstraintLayout > 元信息行 RelativeLayout(app:id/n93)
 *       > LinearLayout > [时间 ImproveTextView, ViewStub, ViewStub, pt, pi]
 *   pt = #7f090304 app:id/pt = 可见分组图标  ← 藏它
 *   pi = #7f0902f8 app:id/pi = 删除图标       ← 绝不碰
 *   普通帖子这一行只有「时间 + 折叠的 ViewStub」；受限帖子才把其中两个 ViewStub
 *   inflate 成 pt / pi 两个 WeImageView。
 *
 * 为什么不走 onBindViewHolder：F-32x 实证——8.0.71 朋友圈 RV 的 onBindViewHolder
 *   hook 0 命中，是死路。
 * 为什么不全局 hook View.setVisibility：铁律 H1——高频基类。
 *
 * 方案（两层，都只对 id 名 == "pt" 的 View 调 GONE，从不碰 pi）：
 *   Layer1 = hook ViewStub.inflate() afterHook：图标首次 inflate 时即命中（低频，
 *            覆盖所有帖子类型，无闪烁）。
 *   Layer2 = hook Activity.onResume（沿用 MomentsRedDotGuard 已实证写法），进/回到
 *            朋友圈页时扫一遍 decorView 兜底（处理回收复用导致的重显）。
 *
 * 开关：AppConfig.isMomentsGroupIconEnabled()（纯开关驱动，独立于 HIDDEN 状态——
 *   这是「清爽自己时间线」的外观偏好，不属密友隐私链）。
 */
public final class MomentsGroupIconFilter {

    private static final String TAG = "NCL";
    private static final String ICON_ENTRY = "pt";   // 磁盘实证的资源 entry 名（= R.id.pt）
    // 朋友圈插件下所有页面：时间线 ImproveSnsTimelineUI / 详情 SnsCommentDetailUI /
    // 个人相册 SnsUserUI 等，类名都含 "plugin.sns"。按包名匹配一网打尽（sweep 只动 pt，别处无害）。
    private static final String SNS_PAGE    = "plugin.sns";

    private static final long SWEEP_MIN_INTERVAL_MS = 60L;

    private static volatile int sIconId = 0;          // 解析出的 R.id.pt（0 = 未解析）
    private static volatile boolean sLoggedHit = false;
    private static volatile long sLastSweep = 0L;
    private static boolean sInstalled = false;

    // 已挂过 OnGlobalLayout 监听的 decorView（避免重复挂、随 View 树 GC 自动清）
    private static final java.util.WeakHashMap<View, Boolean> sAttached = new java.util.WeakHashMap<>();

    private MomentsGroupIconFilter() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;
        installInflateHook();
        installResumeSweep();
        Log.i(TAG, "[MGI] installed (ViewStub.inflate + onResume sweep)");
    }

    // -------------------------------------------------------------------------
    // Layer1 — ViewStub.inflate() afterHook：图标首次 inflate 即藏
    // -------------------------------------------------------------------------
    private static void installInflateHook() {
        try {
            Method inflate = ViewStub.class.getDeclaredMethod("inflate");
            XposedBridge.hookMethod(inflate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!enabled()) return;
                        Object r = param.getResult();
                        if (r instanceof View) maybeHide((View) r);
                    } catch (Throwable ignored) {}
                }
            });
            Log.i(TAG, "[MGI] L1 ViewStub.inflate hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[MGI] L1 hook failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // Layer2 — Activity.onResume：进朋友圈页时立即扫一遍 + 挂 OnGlobalLayout 监听，
    //           滚动/异步渲染出新帖子（含 pt）时随布局变化重扫（节流）。
    // -------------------------------------------------------------------------
    private static void installResumeSweep() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!enabled()) return;
                        final Object act = param.thisObject;
                        if (!(act instanceof Activity)) return;
                        if (!act.getClass().getName().contains(SNS_PAGE)) return;
                        final View root = ((Activity) act).getWindow().getDecorView();
                        attachLayoutSweep(root);
                        sweep(root);                                   // 立即扫一遍
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override public void run() {
                                try { if (enabled()) sweep(root); } catch (Throwable ignored) {}
                            }
                        }, 400);                                       // 异步首屏兜底
                    } catch (Throwable ignored) {}
                }
            });
            Log.i(TAG, "[MGI] L2 Activity.onResume sweep hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[MGI] L2 hook failed: " + t);
        }
    }

    /** 给朋友圈页 decorView 挂一次 OnGlobalLayout 监听：布局变化（滚动出新帖）时节流重扫。 */
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
                                if (!enabled()) return;
                                long now = android.os.SystemClock.uptimeMillis();
                                if (now - sLastSweep < SWEEP_MIN_INTERVAL_MS) return;
                                sLastSweep = now;
                                sweep(root);
                            } catch (Throwable ignored) {}
                        }
                    });
            Log.i(TAG, "[MGI] L2 OnGlobalLayout sweep attached");
        } catch (Throwable t) {
            Log.w(TAG, "[MGI] attach layout sweep failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static boolean enabled() {
        return AppConfig.getInstance().isMomentsGroupIconEnabled();
    }

    /** 遍历 root 子树，把 id 名 == "pt" 的 View 全部 GONE（pi 的 id 不同，天然不命中）。 */
    private static void sweep(View root) {
        int id = iconId(root);
        if (id == 0) return;
        walk(root, id, 0);
    }

    private static void walk(View v, int iconId, int depth) {
        if (v == null || depth > 24) return;
        maybeHideById(v, iconId);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) walk(g.getChildAt(i), iconId, depth + 1);
        }
    }

    /** Layer1 路径：拿到 inflate 结果直接按 id 判定。 */
    private static void maybeHide(View v) {
        int id = iconId(v);
        if (id == 0) return;
        maybeHideById(v, id);
    }

    private static void maybeHideById(View v, int iconId) {
        if (v.getId() != iconId) return;
        if (v.getVisibility() == View.GONE) return;
        v.setVisibility(View.GONE);
        InterceptCounter.getInstance().incF05("MGI-pt-gone");
        if (!sLoggedHit) {
            sLoggedHit = true;
            Log.i(TAG, "[MGI] pt GONE (cls=" + v.getClass().getName() + ")");
        }
    }

    /** 懒解析 R.id.pt（按 entry 名，跨小版本容错）；解析一次后缓存。 */
    private static int iconId(View any) {
        int id = sIconId;
        if (id != 0) return id;
        try {
            Resources res = any.getResources();
            id = res.getIdentifier(ICON_ENTRY, "id", any.getContext().getPackageName());
            if (id != 0) {
                sIconId = id;
                Log.i(TAG, "[MGI] resolved pt id=0x" + Integer.toHexString(id));
            }
        } catch (Throwable ignored) {}
        return id;
    }
}
