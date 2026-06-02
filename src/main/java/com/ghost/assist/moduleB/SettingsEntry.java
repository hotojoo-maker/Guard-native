package com.ghost.assist.moduleB;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * SettingsEntry v5 — 在微信「我 → 设置」页注入「密友设置 ›」入口行。
 *
 * 路径 A：ListView → addHeaderView（天然随列表滚动）
 *
 * 路径 B：WxRecyclerView（pz3.g adapter，19 items）
 *   B1（即时固定 header）：在 WxRecyclerView 爷父 LinearLayout 的 index=1 注入 guardRow
 *        作用：第一次进设置立即可见，B2 hooks 装好后自动移除
 *   B2（真正随列表滚动）：XposedBridge.hookAllMethods 钩 pz3.g 三个方法
 *        getItemCount()        → N+1 时返回 N+1（header visible）
 *        getItemViewType(pos)  → pos 0: 借用 original[0] 的 viewType；pos 1..N: shift -1
 *        onBindViewHolder(h,p) → pos 0: 跳过 original，定制 itemView 为 "密友设置 ›"
 *                                 pos 1..N: shift -1，让 original 正常绑定
 *
 * 铁律：
 *   - 不改 WeChat Adapter 数据字段
 *   - catch (Throwable) 全部静默
 *   - B1 固定 header 在 B2 装好后立即移除，不会重叠
 */
public class SettingsEntry {

    private static final String TAG = "NCL";

    private static final String MAIN_SETTINGS_CLASS =
            "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI";
    // P_SE5: 8.0.71 实测设置页是 CommonSettingsUI（不是 MainSettingsUI）。
    // 两个都监听以兼容不同版本入口。
    private static final String COMMON_SETTINGS_CLASS =
            "com.tencent.mm.plugin.setting.ui.setting_new.CommonSettingsUI";
    private static final String LAUNCHER_UI_CLASS =
            "com.tencent.mm.ui.LauncherUI";
    private static final int MORE_PROFILE_TAG = 0x67757072; // "gupr"

    private static volatile boolean sInstalled      = false;
    private static volatile boolean sDiagDone       = false;
    private static volatile boolean sProbeScheduled = false;

    // Path A: ListView
    private static volatile WeakReference<ListView> sListViewRef;

    // Shared header row ref (used by both B1 and path A)
    private static volatile WeakReference<View>     sHeaderRowRef;
    private static volatile WeakReference<ViewGroup> sLlRef;

    // Path B: RecyclerView weak ref
    private static volatile WeakReference<View> sRvRef;

    // P_SE7 (2026-05-28): banner 跟随 RV 滚动 —— OnScrollChangedListener + computeVerticalScrollOffset。
    // sScrollFollowRvRef: 已挂监听的 RV；sScrollListenerRef: 监听器强引用（VTO 内部是 weak ref，
    // 必须 GC 防护）；removeLlHeader / syncLlHeader 删除分支必须先 detachScrollFollow。
    private static volatile WeakReference<View> sScrollFollowRvRef;
    private static volatile android.view.ViewTreeObserver.OnScrollChangedListener sScrollListenerRef;

    // P_SE8 (2026-06-01): 真·跟随滚动改良 —— banner 不再当占高度的兄弟，
    // 改为悬浮在 RV 父层 + 给 RV 顶部 padding 腾空间（clipToPadding=false）。
    // 空间由 RV 自身 padding 提供并随滚动自然回收 → 不留空槽、不用每帧 requestLayout。
    // 记录被改 padding 的 RV 及其原始值，HIDDEN / 移除时还原。
    private static volatile WeakReference<View> sPaddedRvRef;
    private static volatile int                 sRvOrigPaddingTop    = 0;
    private static volatile boolean             sRvOrigClipToPadding = true;

    // B2: Xposed adapter hooks state
    private static volatile boolean               sAdapterHooked   = false;
    private static volatile WeakReference<Object> sKnownAdapterRef = null;
    private static volatile boolean               sHeaderVisible   = false;
    // Cached original WeChat item count — our header is appended at pos == sLastOrigCount.
    // Updated on every getItemCount call to stay in sync with RecyclerView state.
    private static volatile int                   sLastOrigCount   = 0;

    // P_SE5 (2026-05-28): 切回 INJECT 模式（5-25 原版方案）。
    // hijack 路径在 8.0.71 上有两个无法解决的难题：
    //   1. pz3.g.onBindViewHolder afterHook 是死路径（实测 0 命中）
    //   2. WeChat 的 click dispatch 走 RecyclerView.OnItemTouchListener、不是 OnClickListener
    //      → H 态 delegate original 永远拿到 null 或 self-ref、点击不响应
    // INJECT 模式不动"个人资料"行、只在 pos=1 注入"密友设置 ›"新行，H 态点"个人资料"
    // 走 RecyclerView 原生 dispatch 100% 工作。
    private static volatile boolean sUseInjectMode    = true;

    // P_SE5: overlay 显示期间禁止状态机 enterHidden。
    // 用户在密友设置面板里配置密友时不应被误触发拉回 H。
    // TriggerGuard 各 enterHidden 路径检查这个 flag、true 时跳过。
    private static volatile boolean sOverlayActive    = false;

    /** TriggerGuard 用：overlay 显示中？显示时所有 enterHidden 触发都跳过。 */
    public static boolean isOverlayActive() {
        return sOverlayActive;
    }

    /**
     * 设置页顶部「量子密友设置」入口行是否应当显示。
     *
     * - VISIBLE 态：恒显示。
     * - HIDDEN / UNLOCKING 态：仅当用户关闭了「隐藏功能入口」开关时仍显示（常显）；
     *   开关开启（默认）时隐藏 = 现状行为。
     *
     * 纯 EntryGate 可见性判断：只读状态机 + Bridge 开关，绝不写状态机、不碰过滤/授权。
     */
    private static boolean shouldShowEntry() {
        if (StateMachine.getInstance().getState() == StateMachine.State.VISIBLE) {
            return true;
        }
        return !Bridge.getInstance().isHideEntryInHidden();
    }

    /**
     * P_SE5: 状态机切换时主动刷 banner（不依赖 onResume 重新 fire）。
     * TriggerGuard 触发 enterHidden 后、overlay 状态切换按钮 dismiss 后调本方法。
     */
    public static void onStateChanged() {
        if (!sUseInjectMode) return;
        final ViewGroup ll = sLlRef != null ? sLlRef.get() : null;
        if (ll == null) return;
        final boolean shouldShow = shouldShowEntry();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try {
                    Activity act = unwrapActivity(ll.getContext());
                    if (act == null) return;
                    syncLlHeader(ll, act, shouldShow);
                    Log.i(TAG, "[SET] onStateChanged sync shouldShow=" + shouldShow);
                } catch (Throwable t) {
                    Log.w(TAG, "[SET] onStateChanged failed: " + t);
                }
            }
        });
    }

    /**
     * P_IMPORT: 从 SelectContactUI 导入/移除回来后，即时刷新 overlay 里
     * 「密友列表 / 密群列表」的「已选择 N 个」计数（修「导入成功 UI 不立刻刷新」bug）。
     * 由 ContactImportGuard.consumeResult 在写完 Bridge 后调用；overlay 未显示时静默跳过。
     */
    public static void refreshImportCounts() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try {
                    Bridge br = Bridge.getInstance();
                    TextView b = sBuddyCountRef != null ? sBuddyCountRef.get() : null;
                    if (b != null) {
                        b.setText("\u5df2\u9009\u62e9 " + br.getWxidCount() + " \u4e2a \u203a");
                    }
                    TextView g = sGroupCountRef != null ? sGroupCountRef.get() : null;
                    if (g != null) {
                        g.setText("\u5df2\u9009\u62e9 " + br.getGroupCount() + " \u4e2a \u203a");
                    }
                    Log.i(TAG, "[SET:overlay] import counts refreshed buddy="
                            + br.getWxidCount() + " group=" + br.getGroupCount());
                } catch (Throwable t) {
                    Log.w(TAG, "[SET:overlay] refresh counts failed: " + t);
                }
            }
        });
    }
    private static final int        PROFILE_ROW_TAG   = 0x67757a72; // "guzr"
    private static final int        PROFILE_ORIG_TAG  = 0x67757a73; // "guzs" — cache 原 onClick listener
    private static final String     PROFILE_TEXT      = "\u4e2a\u4eba\u8d44\u6599"; // 个人资料
    private static final String     MIYOU_TEXT        = "\u91cf\u5b50\u5bc6\u53cb"; // 量子密友 — V 态下"个人资料"行替换文字

    // "我" tab top profile row replacement.
    private static volatile WeakReference<View> sMoreProfileRowRef;
    private static volatile WeakReference<TextView> sMoreTitleRef;
    private static volatile WeakReference<View.OnClickListener> sMoreOriginalClickRef;
    private static volatile String sMoreOriginalTitle;

    // P_IMPORT: overlay 内「密友列表 / 密群列表」的「已选择 N 个」计数 TextView 引用。
    // 导入/移除从 SelectContactUI 回来后，ContactImportGuard.consumeResult 调
    // refreshImportCounts() 即时刷新这两个数字（修「导入成功 UI 不立刻刷新」bug）。
    private static volatile WeakReference<TextView> sBuddyCountRef;
    private static volatile WeakReference<TextView> sGroupCountRef;


    // -----------------------------------------------------------------------
    // Install hook
    // -----------------------------------------------------------------------

    public static void install(
            de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            String cls = activity.getClass().getName();
                            try {
                                if (MAIN_SETTINGS_CLASS.equals(cls)
                                        || COMMON_SETTINGS_CLASS.equals(cls)) {
                                    syncEntry(activity);
                                } else if (LAUNCHER_UI_CLASS.equals(cls)) {
                                    scheduleMoreTabProfileProbe(activity);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "[SET] syncEntry error: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[SET] entry installed class=" + MAIN_SETTINGS_CLASS
                    + " more=" + LAUNCHER_UI_CLASS);
        } catch (Throwable t) {
            Log.w(TAG, "[SET] install failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    android.view.View.class,
                    "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object obj = param.thisObject;
                            if (!(obj instanceof TextView)) return;
                            TextView tv = (TextView) obj;
                            CharSequence text = tv.getText();
                            if (text == null || !text.toString().contains("微信号")) return;
                            try {
                                syncMoreTabProfileEntryFromWxid(tv);
                            } catch (Throwable t) {
                                Log.w(TAG, "[SET:more] attach probe failed: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[SET:more] TextView attach hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "[SET:more] attach hook failed: " + t);
        }

        // P_SE3: 量子密友文字防覆写 hook。
        // syncMoreTabProfileEntryFromWxid 把 title.setText("量子密友") 后，WeChat 内部 binder
        // 可能在后续 layout/refresh 中再次 setText 把文字改回去（如 "昵称"/"用户名"）。
        // 装一个 TextView.setText(CharSequence) 的 before hook，专门拦 sMoreTitleRef 这一个实例：
        //   - 仅在 VISIBLE 状态下生效
        //   - 仅当 this == sMoreTitleRef.get()
        //   - 当 args[0] != "量子密友" 时强制改回，并打 [SET:more:guard] 日志
        // 性能：每次 TextView.setText 进 hook 都做一次实例比对，开销可忽略。
        try {
            XposedHelpers.findAndHookMethod(
                    TextView.class,
                    "setText",
                    CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            TextView title = sMoreTitleRef != null ? sMoreTitleRef.get() : null;
                            if (title == null) return;
                            if (param.thisObject != title) return;
                            if (StateMachine.getInstance().getState()
                                    != StateMachine.State.VISIBLE) return;
                            CharSequence incoming = (CharSequence) param.args[0];
                            String expected = "\u91cf\u5b50\u5bc6\u53cb";
                            if (incoming == null || !expected.contentEquals(incoming)) {
                                param.args[0] = expected;
                                Log.i(TAG, "[SET:more:guard] setText intercept "
                                        + incoming + " -> 量子密友");
                            }
                        }
                    });
            Log.i(TAG, "[SET:more:guard] setText anti-overwrite hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "[SET:more:guard] setText hook failed: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // syncEntry — main dispatcher (every onResume, idempotent)
    // -----------------------------------------------------------------------

    private static void syncEntry(Activity activity) {
        if (activity == null) return;

        ViewGroup root = getContentRoot(activity);
        if (root == null) {
            Log.w(TAG, "[SET] content root=null");
            return;
        }

        if (!sDiagDone) {
            sDiagDone = true;
            diagViewTree(root);
        }
        // P_SE1 hijack: delayed — RecyclerView not laid out until after onResume
        scheduleProfileRowHijack(root);

        boolean shouldShow = shouldShowEntry();

        // ── Path A: ListView ─────────────────────────────────────────────────
        ListView lv = findListViewRecursive(root);
        if (lv != null) {
            syncListViewHeader(lv, activity, shouldShow);
            return;
        }

        // ── Path B: WxRecyclerView ────────────────────────────────────────────
        View rv = findRecyclerViewRecursive(root);
        if (rv != null) {
            handleRecyclerView(rv, activity, shouldShow);
            return;
        }

        Log.w(TAG, "[SET] no ListView/RecyclerView found — check [SET:tree] log");
    }

    // -----------------------------------------------------------------------
    // P_SE1 hijack: delayed scan for "个人资料" row → hijack onClick
    // -----------------------------------------------------------------------

    /**
     * P_SE5: 扫"个人资料"行 → 根据状态机做差异化处理。
     *
     * 8.0.71 pz3.g.onBindViewHolder hook 死路径，所以 view-tree 扫描是唯一路径。
     *
     * - VISIBLE: 文字 → "量子密友"，装 hijack listener 整行热区 → 弹 overlay
     * - HIDDEN:  文字 → "个人资料"，**卸**所有 hijack listener，让 RecyclerView.OnItemTouchListener
     *            自然处理 click → 走 WeChat 原生跳 ContactInfo Fragment
     *
     * 2 波重试：100ms 早探 + 800ms 兜底；conditional setText 防闪烁。
     */
    private static void scheduleProfileRowHijack(final ViewGroup root) {
        // P_SE5: INJECT 模式下不动"个人资料"行（走 5-25 路线，只注入新行）。
        if (sUseInjectMode) return;
        final int[] delays = {100, 800};
        Handler h = new Handler(Looper.getMainLooper());
        for (final int delay : delays) {
            h.postDelayed(new Runnable() {
                @Override public void run() { tryPatchProfileRow(root, delay); }
            }, delay);
        }
    }

    private static void tryPatchProfileRow(final ViewGroup root, final int waveTag) {
        try {
            TextView profileTv = findProfileRowTitle(root);
            if (profileTv == null) {
                if (waveTag == 800) {
                    Log.w(TAG, "[SET:hijack:zir] wave-" + waveTag + "ms: no profile row found");
                }
                return;
            }
            // walk up to RecyclerView's direct child (the full row item)
            View row = profileTv;
            ViewParent p = profileTv.getParent();
            while (p instanceof View
                    && !p.getClass().getName().contains("RecyclerView")) {
                row = (View) p;
                p = p.getParent();
            }

            final TextView titleTvFinal = profileTv;
            final View rowRef = row;
            boolean visible = StateMachine.getInstance().getState()
                    == StateMachine.State.VISIBLE;
            final String desired = visible ? MIYOU_TEXT : PROFILE_TEXT;

            // Q4: conditional setText 防闪烁（文字已经对就不动）。
            if (!desired.contentEquals(titleTvFinal.getText())) {
                titleTvFinal.setText(desired);
            }
            // 防 WeChat 异步覆写：250ms / 700ms 兜底重写两次。
            Handler hf = new Handler(Looper.getMainLooper());
            hf.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!desired.contentEquals(titleTvFinal.getText())) {
                        titleTvFinal.setText(desired);
                    }
                }
            }, 250);
            hf.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!desired.contentEquals(titleTvFinal.getText())) {
                        titleTvFinal.setText(desired);
                    }
                }
            }, 700);

            if (visible) {
                // VISIBLE: 装 hijack listener，整行热区 → overlay
                row.setClickable(true);
                View.OnClickListener hijack = new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Log.i(TAG, "[SET:hijack:zir] click VISIBLE -> overlay");
                        showGuardOverlay(v.getContext());
                    }
                };
                row.setOnClickListener(hijack);
                attachHijackToAllDescendants(row, hijack);
                Log.i(TAG, "[SET:hijack:zir] wave-" + waveTag + "ms VISIBLE patched"
                        + " rowClass=" + row.getClass().getName());
            } else {
                // HIDDEN: 卸所有 hijack，让 RecyclerView.OnItemTouchListener 自然处理 click。
                // 8.0.71 ContactInfo 是 CommonSettingsUI 的 Fragment，原生 click dispatch 走 RV。
                detachHijackFromAllDescendants(row);
                row.setOnClickListener(null);
                row.setClickable(false);
                Log.i(TAG, "[SET:hijack:zir] wave-" + waveTag + "ms HIDDEN unpatched (native)"
                        + " rowClass=" + row.getClass().getName());
            }
        } catch (Throwable t) {
            Log.w(TAG, "[SET:hijack:zir] wave-" + waveTag + "ms failed: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // "我" tab profile row entry
    // -----------------------------------------------------------------------

    private static void scheduleMoreTabProfileProbe(final Activity activity) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override public void run() { syncMoreTabProfileEntry(activity); }
        });
        h.postDelayed(new Runnable() {
            @Override public void run() { syncMoreTabProfileEntry(activity); }
        }, 300);
        h.postDelayed(new Runnable() {
            @Override public void run() { syncMoreTabProfileEntry(activity); }
        }, 900);
    }

    private static void syncMoreTabProfileEntry(Activity activity) {
        if (activity == null || !LAUNCHER_UI_CLASS.equals(activity.getClass().getName())) return;

        if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) {
            restoreMoreTabProfileEntry();
            return;
        }

        ViewGroup root = getContentRoot(activity);
        if (root == null) return;
        TextView wxidText = findTextViewContaining(root, "微信号");
        if (wxidText == null) return;
        syncMoreTabProfileEntryFromWxid(wxidText);
    }

    private static void syncMoreTabProfileEntryFromWxid(TextView wxidText) {
        if (wxidText == null) return;
        if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) {
            restoreMoreTabProfileEntry();
            return;
        }

        ViewGroup row = findProfileRowAncestor(wxidText);
        if (row == null) return;

        TextView title = findProfileTitle(row, wxidText);
        if (title == null) return;

        if (row.getTag(MORE_PROFILE_TAG) == null) {
            sMoreOriginalTitle = title.getText() != null ? title.getText().toString() : "";
            View.OnClickListener originalClick = getCurrentOnClickListener(row);
            if (originalClick != null) {
                sMoreOriginalClickRef = new WeakReference<>(originalClick);
            }
        }

        title.setText("\u91cf\u5b50\u5bc6\u53cb"); // 量子密友
        row.setTag(MORE_PROFILE_TAG, Boolean.TRUE);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "[SET:more] profile row clicked");
                showGuardOverlay(v.getContext());
            }
        });
        sMoreProfileRowRef = new WeakReference<View>(row);
        sMoreTitleRef = new WeakReference<TextView>(title);
        Log.i(TAG, "[SET:more] profile row patched");
    }

    private static void restoreMoreTabProfileEntry() {
        View row = sMoreProfileRowRef != null ? sMoreProfileRowRef.get() : null;
        TextView title = sMoreTitleRef != null ? sMoreTitleRef.get() : null;
        if (title != null && sMoreOriginalTitle != null) {
            title.setText(sMoreOriginalTitle);
        }
        if (row != null && row.getTag(MORE_PROFILE_TAG) != null) {
            View.OnClickListener originalClick =
                    sMoreOriginalClickRef != null ? sMoreOriginalClickRef.get() : null;
            row.setOnClickListener(originalClick);
            row.setTag(MORE_PROFILE_TAG, null);
            Log.i(TAG, "[SET:more] profile row restored");
        }
        sMoreProfileRowRef = null;
        sMoreTitleRef = null;
        sMoreOriginalClickRef = null;
        sMoreOriginalTitle = null;
    }

    // -----------------------------------------------------------------------
    // Path B dispatch
    // -----------------------------------------------------------------------

    private static void handleRecyclerView(View rv, Activity activity, boolean shouldShow) {
        // P_SE5 (2026-05-28): 8.0.71 实测 pz3.g.onBindViewHolder hook 0 命中（死路径），
        // INJECT 模式的核心也无法靠 B2 hook 注入新行。改为依赖 B1 grandparent injection
        // 注入 banner 到 RV 同层级 LinearLayout —— 这条路径不依赖 adapter hook、稳定可靠。
        if (sUseInjectMode) {
            // P_SE7: 保存 RV 引用让 syncLlHeader 给 scroll-follow 监听器用。
            sRvRef = new WeakReference<>(rv);
            ViewGroup ll = findRvContainerLinearLayout(rv);
            if (ll != null) {
                syncLlHeader(ll, activity, shouldShow);
            }
            // 同时让 B2 hook 试一次（万一某个版本 onBindViewHolder 真触发）
            if (sAdapterHooked && sHeaderVisible != shouldShow) {
                sHeaderVisible = shouldShow;
                refreshAdapterNotify();
            }
            return;
        }

        // hijack 模式（已弃用）：每次 onResume 强制 rebind
        if (sAdapterHooked) {
            refreshAdapterNotify();
            return;
        }

        // Schedule one-time adapter probe → installs B2 hooks
        if (!sProbeScheduled) {
            sProbeScheduled = true;
            sRvRef = new WeakReference<>(rv);
            final Activity actRef  = activity;
            final boolean  showRef = shouldShow;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { probeLate(actRef, showRef); }
            }, 400);
        }
    }

    // -----------------------------------------------------------------------
    // B2: delayed adapter probe + hook installation
    // -----------------------------------------------------------------------

    private static void probeLate(Activity activity, boolean initialShow) {
        View rv = sRvRef != null ? sRvRef.get() : null;
        if (rv == null) { Log.w(TAG, "[SET:adapter] rv ref lost"); return; }

        // Log class hierarchy
        try {
            Class<?> c = rv.getClass();
            StringBuilder chain = new StringBuilder();
            while (c != null && !c.getName().equals("android.view.ViewGroup")) {
                if (chain.length() > 0) chain.append(" -> ");
                chain.append(c.getName());
                c = c.getSuperclass();
            }
            Log.i(TAG, "[SET:rv-chain] " + chain);
        } catch (Throwable ignored) {}

        // Get adapter
        Object adapter;
        try {
            adapter = rv.getClass().getMethod("getAdapter").invoke(rv);
        } catch (Throwable t) {
            Log.w(TAG, "[SET:adapter] getAdapter failed: " + t);
            return;
        }
        if (adapter == null) {
            Log.w(TAG, "[SET:adapter] null — hooks not installed");
            return;
        }

        int cnt = 0;
        try { cnt = (int) adapter.getClass().getMethod("getItemCount").invoke(adapter); }
        catch (Throwable ignored) {}
        Log.i(TAG, "[SET:adapter] class=" + adapter.getClass().getName() + " count=" + cnt);

        // Log inner classes (ViewHolder discovery for future reference)
        try {
            for (Class<?> inner : adapter.getClass().getDeclaredClasses()) {
                Log.i(TAG, "[SET:vh] " + inner.getName()
                        + " super=" + inner.getSuperclass().getName());
            }
        } catch (Throwable ignored) {}

        // Log first 5 item viewTypes
        for (int i = 0; i < Math.min(cnt, 5); i++) {
            try {
                int vt = (int) adapter.getClass()
                        .getMethod("getItemViewType", int.class).invoke(adapter, i);
                Log.i(TAG, "[SET:adapter] item[" + i + "] viewType=" + vt);
            } catch (Throwable ignored) {}
        }

        sKnownAdapterRef = new WeakReference<>(adapter);
        installAdapterHooks(adapter.getClass(), initialShow);
    }

    // -----------------------------------------------------------------------
    // B2: Xposed hooks on pz3.g
    //
    //  getItemCount()           afterHook:  return N+1 when sHeaderVisible
    //  getItemViewType(pos)     beforeHook: pos>0 → shift pos-1
    //                                       pos=0 → unchanged (borrow original[0]'s type)
    //  onBindViewHolder(h, pos) beforeHook: pos=0 → customize view, skip original
    //                                       pos>0 → shift pos-1
    //  getItemId(pos)           beforeHook: pos=0 → return -1L (unique id)
    //                                       pos>0 → shift pos-1
    // -----------------------------------------------------------------------

    // Position of our injected guard entry in the settings RecyclerView.
    // pos 0 is WeChat's search box (viewType=6); we inject at pos 1 so the
    // entry appears right at the top of the visible settings list.
    private static final int INJECT_POS = 1;

    private static void installAdapterHooks(final Class<?> adapterCls, boolean initialShow) {
        try {
            // Inject our guard entry at INJECT_POS (= 1, after the search box at pos 0).
            // Total count becomes N+1; positions INJECT_POS+1 … N are WeChat's items
            // shifted by 1 — achieved by rewriting pos in getItemViewType / onBindViewHolder
            // / getItemId before the original method runs.

            // 1. getItemCount — advertise N+1 items. (legacy inject mode only)
            XposedBridge.hookAllMethods(adapterCls, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!sUseInjectMode) return;
                    if (!sHeaderVisible) return;
                    Object r = param.getResult();
                    if (r instanceof Integer) {
                        param.setResult((Integer) r + 1);
                    }
                }
            });

            // 2. getItemViewType — our slot returns viewType 1; shifted slots delegate. (legacy)
            XposedBridge.hookAllMethods(adapterCls, "getItemViewType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!sUseInjectMode) return;
                    if (!sHeaderVisible
                            || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) return;
                    int pos = (int) param.args[0];
                    if (pos == INJECT_POS) {
                        param.setResult(1); // normal row ViewHolder type
                    } else if (pos > INJECT_POS) {
                        param.args[0] = pos - 1; // shift → WeChat handles original pos-1
                    }
                    // pos 0 (search box): unchanged
                }
            });

            // 3. onBindViewHolder — bind our entry at INJECT_POS; shift the rest. (legacy)
            XposedBridge.hookAllMethods(adapterCls, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!sUseInjectMode) return;
                    if (!sHeaderVisible
                            || param.args.length < 2
                            || !(param.args[1] instanceof Integer)) return;
                    int pos = (int) param.args[1];
                    if (pos == INJECT_POS) {
                        Object holder = param.args[0];
                        try {
                            View itemView = (View) holder.getClass()
                                    .getField("itemView").get(holder);
                            customizeGuardHeader(itemView);
                        } catch (Throwable t) {
                            Log.w(TAG, "[SET:hook] customize failed: " + t);
                        }
                        param.setResult(null); // skip WeChat's original bind
                    } else if (pos > INJECT_POS) {
                        param.args[1] = pos - 1; // shift
                    }
                }
            });

            // 4. getItemId — stable id for our slot; shift the rest. (legacy)
            XposedBridge.hookAllMethods(adapterCls, "getItemId", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!sUseInjectMode) return;
                    if (!sHeaderVisible
                            || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) return;
                    int pos = (int) param.args[0];
                    if (pos == INJECT_POS) {
                        param.setResult(-1L);
                    } else if (pos > INJECT_POS) {
                        param.args[0] = pos - 1; // shift
                    }
                }
            });

            // 5. P_SE1 hijack: 在每次 onBindViewHolder 完成后扫"个人资料"行，接管 onClick。
            //    VISIBLE → showGuardDialog；HIDDEN → 委托给原始 onClickListener（走 ContactInfoUI）。
            //    R1 整行热区：itemView 自身 + 所有后代 View 都装 hijack listener。
            //    无论 Android 把 click 派发到哪个子 View，触发的都是同一个 hijack 决策点。
            //    原始 onClick 在首次 patch 时用 findFirstOnClickListener 抓出来，
            //    缓存在 PROFILE_ORIG_TAG，避免后续 bind 误把我们的 hijack 当原始递归装。
            XposedBridge.hookAllMethods(adapterCls, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sUseInjectMode) return;
                    if (param.args.length < 2) return;
                    try {
                        Object holder = param.args[0];
                        if (holder == null) return;
                        View itemView = (View) holder.getClass().getField("itemView").get(holder);
                        if (itemView == null) return;
                        TextView titleTv = findProfileRowTitle(itemView);
                        if (titleTv == null) return;

                        // Q4: VISIBLE 下文字替换为"量子密友"，HIDDEN 还原"个人资料"。
                        // 三次 post 对抗 WeChat 在 onBindViewHolder 之后的异步 setText 覆盖。
                        boolean visibleNow = StateMachine.getInstance().getState()
                                == StateMachine.State.VISIBLE;
                        final String desiredText = visibleNow ? MIYOU_TEXT : PROFILE_TEXT;
                        final TextView titleTvF = titleTv;
                        titleTvF.setText(desiredText);
                        Handler h5 = new Handler(Looper.getMainLooper());
                        h5.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (!desiredText.contentEquals(titleTvF.getText())) {
                                    titleTvF.setText(desiredText);
                                }
                            }
                        }, 100);
                        h5.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (!desiredText.contentEquals(titleTvF.getText())) {
                                    titleTvF.setText(desiredText);
                                }
                            }
                        }, 400);

                        // 拿原始 onClick：首次从子树扫，之后从 tag 缓存读，避免 hijack 自己递归装自己。
                        Object cached = itemView.getTag(PROFILE_ORIG_TAG);
                        final View.OnClickListener original;
                        if (cached instanceof View.OnClickListener) {
                            original = (View.OnClickListener) cached;
                        } else {
                            original = findFirstOnClickListener(itemView);
                            if (original != null) {
                                itemView.setTag(PROFILE_ORIG_TAG, original);
                            }
                        }

                        final View itemViewRef = itemView;
                        final View.OnClickListener hijack = new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                boolean visible = StateMachine.getInstance().getState()
                                        == StateMachine.State.VISIBLE;
                                if (visible) {
                                    Log.i(TAG, "[SET:hijack:zir] click VISIBLE -> overlay");
                                    showGuardOverlay(v.getContext());
                                } else {
                                    Log.i(TAG, "[SET:hijack:zir] click HIDDEN  -> delegate original (on itemView)");
                                    // H2 修复：8.0.71 ContactInfo 是 CommonSettingsUI Fragment，
                                    // 原 listener 内部要从 itemView 拿 adapterPosition 才能跳 Fragment。
                                    // 必须传 itemViewRef，否则传子 View 时 getChildAdapterPosition(v) == -1，listener no-op。
                                    if (original != null) original.onClick(itemViewRef);
                                }
                            }
                        };

                        // R1 整行：自身 + 全后代都装 hijack listener，无论 WeChat 把 clickable
                        // 设在哪个子 View，触发的都是 hijack。
                        itemView.setClickable(true);
                        itemView.setOnClickListener(hijack);
                        attachHijackToAllDescendants(itemView, hijack);

                        // P_SE5: 不再用 firstTime 门控、每次都打 orig 日志，方便诊断 H 态点不动问题。
                        // PROFILE_ROW_TAG 已不再控制 patched 日志、保留 setTag 用于其他可能的复用。
                        itemView.setTag(PROFILE_ROW_TAG, Boolean.TRUE);
                        Log.i(TAG, "[SET:hijack:zir] hook5 patched orig="
                                + (original != null ? original.getClass().getName() : "null")
                                + " state=" + StateMachine.getInstance().getStateName());
                    } catch (Throwable t) {
                        Log.w(TAG, "[SET:hijack:zir] afterHook failed: " + t);
                    }
                }
            });

            sAdapterHooked = true;
            sHeaderVisible = initialShow;
            Log.i(TAG, "[SET:hook] adapter hooks installed on " + adapterCls.getName()
                    + " visible=" + initialShow + " hijack=" + !sUseInjectMode);

            // P_SE5: 不再移除 B1 banner —— 8.0.71 上 B2 onBindViewHolder hook 死路径，
            // B1 banner 必须长期保留作为唯一注入路径。removeLlHeader 保留作为状态切换工具。
            // Trigger redraw with new item count（B2 hook 若工作则切换 visibility）
            refreshAdapterNotify();

        } catch (Throwable t) {
            Log.w(TAG, "[SET:hook] installAdapterHooks failed: " + t);
        }
    }

    /**
     * Customize a WeChat settings ViewHolder's itemView to show the guard entry.
     * Called from onBindViewHolder hook at position 0.
     * WeChat's original onBindViewHolder is skipped, so this replaces its binding.
     * When this ViewHolder is recycled for a real position, the original binding
     * restores correct content (text + click listener).
     */
    private static void customizeGuardHeader(View itemView) {
        TextView tv = findFirstTextView(itemView);
        if (tv != null) tv.setText("\u5bc6\u53cb\u8bbe\u7f6e \u203a"); // 密友设置 ›
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "[SET] entry clicked");
                showGuardOverlay(v.getContext());
            }
        });
    }

    private static void refreshAdapterNotify() {
        final Object adapter = sKnownAdapterRef != null ? sKnownAdapterRef.get() : null;
        if (adapter == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try {
                    adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
                    Log.i(TAG, "[SET:hook] notifyDataSetChanged visible=" + sHeaderVisible);
                } catch (Throwable t) {
                    Log.w(TAG, "[SET:hook] notifyDataSetChanged failed: " + t);
                }
            }
        });
    }

    private static void removeLlHeader() {
        final View row = sHeaderRowRef != null ? sHeaderRowRef.get() : null;
        sHeaderRowRef = null;
        sLlRef = null;
        detachScrollFollow();
        restoreRvPadding();
        if (row == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try {
                    ViewGroup p = (ViewGroup) row.getParent();
                    if (p != null) p.removeView(row);
                    Log.i(TAG, "[SET] B1 fixed header removed (B2 active)");
                } catch (Throwable ignored) {}
            }
        });
    }

    // -----------------------------------------------------------------------
    // B1: Fixed header — grandparent LinearLayout injection (fallback)
    // -----------------------------------------------------------------------

    private static ViewGroup findRvContainerLinearLayout(View rv) {
        try {
            ViewGroup parent = (ViewGroup) rv.getParent();          // RelativeLayout
            if (parent == null) return null;
            ViewGroup gp = (ViewGroup) parent.getParent();          // LinearLayout?
            if (gp instanceof LinearLayout) return gp;
            ViewGroup ggp = (ViewGroup) gp.getParent();
            if (ggp instanceof LinearLayout) return ggp;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void syncLlHeader(ViewGroup ll, Activity activity, boolean shouldShow) {
        ViewGroup knownLl  = sLlRef        != null ? sLlRef.get()        : null;
        View      knownRow = sHeaderRowRef != null ? sHeaderRowRef.get() : null;

        if (shouldShow) {
            // 幂等：同一 Activity 且 banner 仍挂着 → 不重复注入。
            if (knownLl == ll && knownRow != null && knownRow.getParent() != null) return;
            // 清旧 banner（可能来自上一个 Activity 实例）。
            if (knownRow != null && knownRow.getParent() != null) {
                try { ((ViewGroup) knownRow.getParent()).removeView(knownRow); }
                catch (Throwable ignored) {}
            }
            detachScrollFollow();
            restoreRvPadding();

            final View row = buildGuardRow(activity);
            sLlRef        = new WeakReference<>(ll);
            sHeaderRowRef = new WeakReference<>(row);

            // P_SE8: 真·跟随滚动 —— banner 改挂到 RV 父层做顶部悬浮，并给 RV 顶部
            // padding 腾空间（clipToPadding=false）。滚动时空间由 RV 自身 padding 提供、
            // 随内容自然覆盖，banner 用 translationY 跟手；不留空槽、不用每帧 requestLayout。
            final View rv = sRvRef != null ? sRvRef.get() : null;
            final ViewGroup host = rv != null ? overlayHostFor(rv) : null;
            if (rv != null && host != null) {
                host.addView(row, makeTopOverlayLp(host));
                Log.i(TAG, "[SET] overlay banner added host=" + host.getClass().getSimpleName());
                row.post(new Runnable() {
                    @Override public void run() {
                        try {
                            int bh = measureBannerHeight(row, rv, host);
                            if (bh <= 0) {
                                Log.w(TAG, "[SET] overlay bh<=0, skip padding");
                                return;
                            }
                            // 让 banner 对齐 RV 在 host 内的顶边（多数情况 rv.getTop()==0）。
                            try {
                                ViewGroup.MarginLayoutParams mlp =
                                        (ViewGroup.MarginLayoutParams) row.getLayoutParams();
                                mlp.topMargin = Math.max(0, rv.getTop());
                                row.setLayoutParams(mlp);
                            } catch (Throwable ignored) {}
                            applyRvTopPadding(rv, bh);
                            attachScrollFollow(rv, row);
                            Log.i(TAG, "[SET] overlay applied bh=" + bh + " rvTop=" + rv.getTop());
                        } catch (Throwable t) {
                            Log.w(TAG, "[SET] overlay apply failed: " + t);
                        }
                    }
                });
            } else {
                // Fallback（host 不可层叠）：保持原兄弟注入 + translationY（现状，不砸）。
                int rvIdx = -1;
                for (int i = 0; i < ll.getChildCount(); i++) {
                    View c = ll.getChildAt(i);
                    if (c != null && c.getClass().getName().contains("RecyclerView")) {
                        rvIdx = i;
                        break;
                    }
                }
                int idx = rvIdx >= 0 ? rvIdx : Math.min(1, ll.getChildCount());
                ll.addView(row, idx);
                Log.i(TAG, "[SET] B1 fallback row added idx=" + idx
                        + " llChildCount=" + ll.getChildCount());
                if (rv != null) attachScrollFollow(rv, row);
                else Log.w(TAG, "[SET] scroll-follow not attached: sRvRef null");
            }
        } else {
            detachScrollFollow();
            restoreRvPadding();
            if (knownRow != null && knownRow.getParent() != null) {
                try {
                    ((ViewGroup) knownRow.getParent()).removeView(knownRow);
                    Log.i(TAG, "[SET] entry row removed (HIDDEN)");
                } catch (Throwable ignored) {}
            }
            sHeaderRowRef = null;
            sLlRef = null;
        }
    }

    /**
     * P_SE7: 在 RV 上挂 ViewTreeObserver.OnScrollChangedListener。
     * 每次 RV 滚动 → 读 computeVerticalScrollOffset → 把 banner 的 translationY
     * 设为 -min(offset, bannerHeight)，让 banner "跟随" RV 一起向上滑动，
     * 滑过 banner 高度后停止（不会无限漂出屏幕）。
     */
    private static void attachScrollFollow(final View rv, final View banner) {
        try {
            final WeakReference<View> rvWeak = new WeakReference<>(rv);
            final WeakReference<View> bannerWeak = new WeakReference<>(banner);
            android.view.ViewTreeObserver.OnScrollChangedListener listener =
                    new android.view.ViewTreeObserver.OnScrollChangedListener() {
                        @Override public void onScrollChanged() {
                            View rvNow = rvWeak.get();
                            View bannerNow = bannerWeak.get();
                            if (rvNow == null || bannerNow == null) return;
                            try {
                                // View.computeVerticalScrollOffset() 是 protected，但 RecyclerView
                                // 重写为 public，反射调用拿运行时实际方法（不受静态类型限制）。
                                Object offsetObj = XposedHelpers.callMethod(
                                        rvNow, "computeVerticalScrollOffset");
                                int offset = (offsetObj instanceof Integer)
                                        ? ((Integer) offsetObj).intValue() : 0;
                                int bh = bannerNow.getHeight();
                                if (bh <= 0) return;
                                int clamped = Math.min(offset, bh);
                                bannerNow.setTranslationY(-clamped);
                            } catch (Throwable ignored) {}
                        }
                    };
            rv.getViewTreeObserver().addOnScrollChangedListener(listener);
            sScrollFollowRvRef = new WeakReference<>(rv);
            sScrollListenerRef = listener;
            Log.i(TAG, "[SET] scroll-follow attached rv=" + rv.getClass().getSimpleName());
        } catch (Throwable t) {
            Log.w(TAG, "[SET] scroll-follow attach failed: " + t);
        }
    }

    private static void detachScrollFollow() {
        try {
            View rv = sScrollFollowRvRef != null ? sScrollFollowRvRef.get() : null;
            android.view.ViewTreeObserver.OnScrollChangedListener listener = sScrollListenerRef;
            if (rv != null && listener != null) {
                rv.getViewTreeObserver().removeOnScrollChangedListener(listener);
                Log.i(TAG, "[SET] scroll-follow detached");
            }
        } catch (Throwable ignored) {
        } finally {
            sScrollFollowRvRef = null;
            sScrollListenerRef = null;
        }
    }

    // -----------------------------------------------------------------------
    // P_SE8: overlay banner host + RV top-padding helpers
    // -----------------------------------------------------------------------

    /** RV 的可层叠父容器（FrameLayout / RelativeLayout）才能承载顶部悬浮 banner。 */
    private static ViewGroup overlayHostFor(View rv) {
        try {
            ViewParent p = rv.getParent();
            if (p instanceof FrameLayout || p instanceof android.widget.RelativeLayout) {
                return (ViewGroup) p;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 顶部对齐、横向铺满、纵向 wrap 的 LayoutParams（按 host 类型生成）。 */
    private static ViewGroup.LayoutParams makeTopOverlayLp(ViewGroup host) {
        if (host instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP;
            return lp;
        }
        android.widget.RelativeLayout.LayoutParams lp =
                new android.widget.RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        return lp;
    }

    /** banner 实测高度：优先 getHeight()，未布局则手动 measure 兜底。 */
    private static int measureBannerHeight(View row, View rv, ViewGroup host) {
        int bh = row.getHeight();
        if (bh > 0) return bh;
        try {
            int w = rv.getWidth();
            if (w <= 0) w = host.getWidth();
            int ws = (w > 0)
                    ? View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
                    : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hs = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            row.measure(ws, hs);
            bh = row.getMeasuredHeight();
        } catch (Throwable ignored) {}
        return bh;
    }

    /** 给 RV 顶部加 topPad 高度 padding + clipToPadding=false；首次记录原值以便还原。 */
    private static void applyRvTopPadding(View rv, int topPad) {
        try {
            View padded = sPaddedRvRef != null ? sPaddedRvRef.get() : null;
            if (padded != rv) {
                sRvOrigPaddingTop = rv.getPaddingTop();
                try { sRvOrigClipToPadding = ((ViewGroup) rv).getClipToPadding(); }
                catch (Throwable ignored) { sRvOrigClipToPadding = true; }
                sPaddedRvRef = new WeakReference<>(rv);
            }
            try { ((ViewGroup) rv).setClipToPadding(false); } catch (Throwable ignored) {}
            rv.setPadding(rv.getPaddingLeft(), sRvOrigPaddingTop + topPad,
                    rv.getPaddingRight(), rv.getPaddingBottom());
        } catch (Throwable t) {
            Log.w(TAG, "[SET] applyRvTopPadding failed: " + t);
        }
    }

    /** 还原被改过 padding 的 RV（top + clipToPadding），并清引用。 */
    private static void restoreRvPadding() {
        try {
            View rv = sPaddedRvRef != null ? sPaddedRvRef.get() : null;
            if (rv != null) {
                rv.setPadding(rv.getPaddingLeft(), sRvOrigPaddingTop,
                        rv.getPaddingRight(), rv.getPaddingBottom());
                try { ((ViewGroup) rv).setClipToPadding(sRvOrigClipToPadding); }
                catch (Throwable ignored) {}
                Log.i(TAG, "[SET] RV padding restored top=" + sRvOrigPaddingTop);
            }
        } catch (Throwable ignored) {
        } finally {
            sPaddedRvRef = null;
        }
    }

    // -----------------------------------------------------------------------
    // Path A: ListView header injection
    // -----------------------------------------------------------------------

    private static void syncListViewHeader(ListView lv, Activity activity, boolean shouldShow) {
        if (!sUseInjectMode) {
            return;
        }
        ListView knownLv  = sListViewRef  != null ? sListViewRef.get()  : null;
        View     knownRow = sHeaderRowRef != null ? sHeaderRowRef.get() : null;

        if (shouldShow) {
            if (knownLv == lv && knownRow != null) return;
            if (knownLv != null && knownLv != lv && knownRow != null) {
                try { knownLv.removeHeaderView(knownRow); } catch (Throwable ignored) {}
            }
            View row = buildGuardRow(activity);
            lv.addHeaderView(row, null, true);
            sListViewRef  = new WeakReference<>(lv);
            sHeaderRowRef = new WeakReference<>(row);
            Log.i(TAG, "[SET] A entry row added via addHeaderView");
        } else {
            if (knownRow != null) {
                ListView target = (knownLv != null) ? knownLv : lv;
                try {
                    target.removeHeaderView(knownRow);
                    Log.i(TAG, "[SET] A entry row removed");
                } catch (Throwable ignored) {}
                sHeaderRowRef = null;
                sListViewRef  = null;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Guard row View (native WeChat item style)
    // -----------------------------------------------------------------------

    /**
     * P_SE7: banner 视觉对齐 WeChat 8.0.71 设置行原生样式 + 顶部 8dp 灰色 spacer 呼吸感。
     *
     * 返回结构：
     *   outer (vertical LinearLayout)
     *     ├── topSpacer  8dp gray (#EFEFEF, 与设置页分组间隙同色)
     *     └── rowWrapper FrameLayout 白底
     *           ├── row    水平 LinearLayout 标题 + ›
     *           └── divider 底部 1dp #E0E0E0
     */
    private static View buildGuardRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(context, 16);
        int pv = dp(context, 14);
        row.setPadding(ph, pv, ph, pv);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "[SET] entry clicked");
                showGuardOverlay(v.getContext());
            }
        });

        TextView title = new TextView(context);
        title.setText("\u91cf\u5b50\u5bc6\u53cb\u8bbe\u7f6e"); // 量子密友设置
        title.setTextColor(Color.parseColor("#191919"));
        title.setTextSize(17f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(title, titleLp);

        TextView arrow = new TextView(context);
        arrow.setText("\u203a"); // ›
        arrow.setTextColor(Color.parseColor("#C7C7CC"));
        arrow.setTextSize(18f);
        row.addView(arrow);

        // 包一层 FrameLayout 加底部分隔线
        FrameLayout rowWrapper = new FrameLayout(context);
        rowWrapper.setBackgroundColor(Color.WHITE);
        rowWrapper.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.gravity = Gravity.BOTTOM;
        rowWrapper.addView(divider, dlp);

        // P_SE7: 外层 vertical LL 提供顶部分组样式（仿"账号/通用/功能"分组）。
        // 结构：
        //   [topSpacer 8dp gray]
        //   [groupHeader "隐私功能" — small gray text, 同 WeChat 分组 header 样式]
        //   [rowWrapper 白底行]
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EFEFEF"));

        View topSpacer = new View(context);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 8));
        outer.addView(topSpacer, spacerLp);

        TextView groupHeader = new TextView(context);
        groupHeader.setText("\u9690\u79c1\u529f\u80fd"); // 隐私功能
        // P_SE7: 颜色对齐 WeChat 设置页"账号 / 通用 / 功能"分组 header（实测 #9A9A9A）
        groupHeader.setTextColor(Color.parseColor("#9A9A9A"));
        groupHeader.setTextSize(13f);
        int ghPh = dp(context, 16);
        int ghPv = dp(context, 6);
        groupHeader.setPadding(ghPh, ghPv, ghPh, ghPv);
        LinearLayout.LayoutParams ghLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.addView(groupHeader, ghLp);

        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.addView(rowWrapper, wrapperLp);

        return outer;
    }

    // -----------------------------------------------------------------------
    // P_SE4: 量子密友设置 — 全屏 overlay 注入到 Activity DecorView
    //
    // 8.0.71 ContactInfoUI 是 CommonSettingsUI 的 Fragment、无法 startActivity 跳转；
    // 模块自己的 Activity 又无法在 WeChat 进程注册。所以走 overlay 注入路线：
    //   - addView 一个全屏 LinearLayout 到 Activity.getWindow().getDecorView()
    //   - clickable=true 拦截穿透、setOnKeyListener 拦截 BACK
    //   - 顶栏返回按钮 + 滚动内容区
    // 单进程、单 ClassLoader、不依赖 manifest，最稳。
    // -----------------------------------------------------------------------

    private static final int OVERLAY_TAG = 0x67756f76; // "guov"

    private static void showGuardOverlay(final Context ctx) {
        try {
            Activity activity = unwrapActivity(ctx);
            if (activity == null) {
                Log.w(TAG, "[SET:overlay] no Activity in context " + ctx);
                return;
            }
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            // 重复点击：已显示则不再叠加
            if (decor.findViewWithTag(OVERLAY_TAG) != null) {
                Log.i(TAG, "[SET:overlay] already shown, skip");
                return;
            }
            View panel = buildGuardOverlay(activity, decor);
            decor.addView(panel, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            panel.requestFocus();
            sOverlayActive = true;  // P_SE5: 让 TriggerGuard 各 enterHidden 跳过
            Log.i(TAG, "[SET:overlay] shown state="
                    + StateMachine.getInstance().getStateName());
        } catch (Throwable t) {
            Log.w(TAG, "[SET:overlay] show failed: " + t);
        }
    }

    private static View buildGuardOverlay(final Activity activity, final ViewGroup decor) {
        final Bridge br = Bridge.getInstance();
        final StateMachine sm = StateMachine.getInstance();

        // 根容器：FrameLayout，白底，拦截点击穿透。整块内容滚动，返回 ← 固定悬浮。
        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.parseColor("#F7F7F7"));
        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setTag(OVERLAY_TAG);

        // BACK 键 → 移除自己
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP
                        && keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissOverlay(decor);
                    return true;
                }
                return false;
            }
        });

        // 内容区（ScrollView 包裹，含头图块——整块随内容滚动）
        ScrollView sv = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        // ===== 头图块（居中标题 + 致谢 + 版本号，随内容滚动）=====
        content.addView(buildHeaderBlock(activity));

        // ===== 密友 =====
        content.addView(buildSectionHeader(activity, "密友"));

        // 1. 开启密友（总开关）
        content.addView(buildSwitchRow(activity, "开启密友",
                "密友功能总开关", br.isFeatureEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setFeatureEnabled(checked);
                        Log.i(TAG, "[SET:overlay] feature=" + checked);
                    }
                }));

        // 2. 消息防撤回（接通 AntiRecall）
        content.addView(buildSwitchRow(activity, "消息防撤回",
                "拦截并保留对方撤回的消息", br.isAntiRecallEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setAntiRecallEnabled(checked);
                        Log.i(TAG, "[SET:overlay] antiRecall=" + checked);
                    }
                }));

        // 3. 语音一键转发（占位，未接逻辑）
        content.addView(buildSwitchRow(activity, "语音一键转发",
                "功能更新中", false, null));

        // 4. 显示密友未读消息数（默认关；开 = 顶部「微信(N)」照常计入密友未读）
        content.addView(buildSwitchRow(activity, "显示密友未读消息数",
                "开启后顶部微信计数包含密友未读", br.isShowHiddenUnread(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setShowHiddenUnread(checked);
                        Log.i(TAG, "[SET:overlay] showHiddenUnread=" + checked);
                    }
                }));

        // 隐藏通讯录标签（原"隐藏指定标签"，移到密友列表上方）
        content.addView(buildSwitchRow(activity, "隐藏通讯录标签",
                "联系人标签隐藏入口",
                br.isHideContactLabelEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setHideContactLabelEnabled(checked);
                        Log.i(TAG, "[SET:overlay] hideLabel=" + checked);
                    }
                }));

        // 隐藏功能入口（默认开 = 隐身态自动藏入口；关 = 隐身态也常显入口）
        content.addView(buildSwitchRow(activity, "隐藏功能入口",
                "隐身时自动隐藏设置入口（关闭后常显）",
                br.isHideEntryInHidden(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setHideEntryInHidden(checked);
                        Log.i(TAG, "[SET:overlay] hideEntryInHidden=" + checked);
                    }
                }));

        // 5. 密友列表（点击 = 拉起微信官方选择器，预选已有 + 增删一体）
        final TextView[] buddyCountOut = new TextView[1];
        content.addView(buildButtonRow(activity, "密友列表",
                "已选择 " + br.getWxidCount() + " 个",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ContactImportGuard.launchSelectBuddy(activity);
                    }
                }, buddyCountOut));
        sBuddyCountRef = new WeakReference<>(buddyCountOut[0]);

        // 6. 密群列表（点击 = 拉起微信原生选群器 GroupCardSelectUI，预选已隐密群 → 增删一体）
        final TextView[] groupCountOut = new TextView[1];
        content.addView(buildButtonRow(activity, "密群列表",
                "已选择 " + br.getGroupCount() + " 个",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ContactImportGuard.launchSelectGroup(activity);
                    }
                }, groupCountOut));
        sGroupCountRef = new WeakReference<>(groupCountOut[0]);

        // ===== 性能（密友与特色功能之间的分组）=====
        content.addView(buildSectionHeader(activity, "性能"));
        content.addView(buildSwitchRow(activity, "高性能模式",
                "跳过冷启动白屏遮罩 / 要求微信常驻后台",
                br.isHighPerfMode(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setHighPerfMode(checked);
                        Log.i(TAG, "[SET:overlay] highPerf=" + checked);
                    }
                }));

        // ===== 特色功能（占位，未接逻辑）=====
        content.addView(buildSectionHeader(activity, "特色功能"));
        content.addView(buildSwitchRow(activity, "伪装定位",
                "伪造 GPS 位置（占位）", false, null));
        content.addView(buildSwitchRow(activity, "余额装X",
                "功能更新中", false, null));
        content.addView(buildNote(activity, "独家功能 · 请低调使用"));

        // ===== 通知（密友消息 + 来电 合并为一个小分组）=====
        content.addView(buildSectionHeader(activity, "通知"));
        content.addView(buildNotifyModeRow(activity, br));      // 密友消息通知模式
        content.addView(buildCallNotifyModeRow(activity, br));  // 来电提示
        content.addView(buildNote(activity, "推荐静默"));

        // ===== 授权（占位）=====
        content.addView(buildSectionHeader(activity, "\u6388\u6743"));
        content.addView(buildTextRow(activity, "\u6388\u6743\u72b6\u6001",
                "\u5df2\u6fc0\u6d3b (\u5360\u4f4d)"));
        content.addView(buildTextRow(activity, "\u5230\u671f\u65f6\u95f4",
                "\u6c38\u4e45 (\u5360\u4f4d)"));

        // 底部留白（替代原"关闭"按钮——返回用顶部 ← 或系统返回键）
        View bottomPad = new View(activity);
        content.addView(bottomPad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 24)));

        sv.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 固定悬浮返回 ←（左上角，状态栏下方，不随内容滚动）
        TextView back = new TextView(activity);
        back.setText("\u2190");
        back.setTextColor(Color.parseColor("#1C1C1E"));
        back.setTextSize(24f);
        back.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 18), dp(activity, 8));
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(decor); }
        });
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.gravity = Gravity.START | Gravity.TOP;
        backLp.topMargin = getStatusBarHeight(activity) + dp(activity, 8);
        root.addView(back, backLp);

        return root;
    }

    private static void dismissOverlay(ViewGroup decor) {
        View panel = decor.findViewWithTag(OVERLAY_TAG);
        if (panel != null) {
            try { decor.removeView(panel); } catch (Throwable ignored) {}
            sOverlayActive = false;  // P_SE5: 解除 enterHidden 抑制
            Log.i(TAG, "[SET:overlay] dismissed");
        }
    }

    /**
     * 扁平 iOS 风格开关（自绘）：大尺寸圆角轨道 + 白色圆钮，开绿关灰，带滑动动画。
     * listener=null → 占位（略暗、不可点）。
     */
    private static View buildIosSwitch(final Context ctx, boolean checked,
                                       final CompoundButton.OnCheckedChangeListener listener) {
        final int wPx = dp(ctx, 54);
        final int hPx = dp(ctx, 32);
        final int thumbPx = dp(ctx, 28);
        final int travel = wPx - thumbPx - dp(ctx, 4);
        final int onColor = Color.parseColor("#34C759");
        final int offColor = Color.parseColor("#E4E4EA");

        final FrameLayout sw = new FrameLayout(ctx);
        final boolean[] on = { checked };

        final GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(hPx / 2f);
        track.setColor(on[0] ? onColor : offColor);
        sw.setBackground(track);

        final View thumb = new View(ctx);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumbBg.setColor(Color.WHITE);
        thumb.setBackground(thumbBg);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(thumbPx, thumbPx);
        tlp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        tlp.leftMargin = dp(ctx, 2);
        sw.addView(thumb, tlp);
        thumb.setTranslationX(on[0] ? travel : 0);

        sw.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));

        if (listener != null) {
            sw.setClickable(true);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    on[0] = !on[0];
                    track.setColor(on[0] ? onColor : offColor);
                    thumb.animate().translationX(on[0] ? travel : 0).setDuration(150).start();
                    listener.onCheckedChanged(null, on[0]);
                }
            });
        } else {
            sw.setAlpha(0.5f); // 占位：略暗、不可点
        }
        return sw;
    }

    private static View buildSwitchRow(Context ctx, String title, String subtitle,
                                       boolean checked,
                                       CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(ctx, 16);
        int pv = dp(ctx, 12);
        row.setPadding(ph, pv, ph, pv);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#191919"));
        tvTitle.setTextSize(17f);
        textCol.addView(tvTitle);
        if (subtitle != null && subtitle.length() > 0) {
            TextView tvSub = new TextView(ctx);
            tvSub.setText(subtitle);
            tvSub.setTextColor(Color.parseColor("#888888"));
            tvSub.setTextSize(12f);
            textCol.addView(tvSub);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(buildIosSwitch(ctx, checked, listener));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    private static View buildButtonRow(Context ctx, String title, String btnText,
                                       View.OnClickListener listener) {
        return buildButtonRow(ctx, title, btnText, listener, null);
    }

    /**
     * @param outBtn 非 null 时，把内部「值/计数」TextView 回传到 outBtn[0]，
     *               供 refreshImportCounts() 后续刷新（修导入后计数不更新 bug）。
     */
    private static View buildButtonRow(Context ctx, String title, String btnText,
                                       View.OnClickListener listener, TextView[] outBtn) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(ctx, 16);
        int pv = dp(ctx, 14);
        row.setPadding(ph, pv, ph, pv);
        row.setOnClickListener(listener);
        row.setClickable(true);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#191919"));
        tvTitle.setTextSize(17f);
        row.addView(tvTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvBtn = new TextView(ctx);
        tvBtn.setText(btnText + " \u203a");
        tvBtn.setTextColor(Color.parseColor("#576B95"));
        tvBtn.setTextSize(14f);
        row.addView(tvBtn);
        if (outBtn != null && outBtn.length > 0) outBtn[0] = tvBtn;

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    private static View buildTextRow(Context ctx, String title, String value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(ctx, 16);
        int pv = dp(ctx, 14);
        row.setPadding(ph, pv, ph, pv);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#191919"));
        tvTitle.setTextSize(17f);
        row.addView(tvTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextColor(Color.parseColor("#888888"));
        tvVal.setTextSize(14f);
        row.addView(tvVal);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    private static View buildSectionHeader(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#888888"));
        tv.setTextSize(13f);
        int ph = dp(ctx, 16);
        tv.setPadding(ph, dp(ctx, 16), ph, dp(ctx, 6));
        return tv;
    }

    /** 分组底部小灰字注脚（如「独家功能 · 请低调使用」）。 */
    private static View buildNote(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9A9AA0"));
        tv.setTextSize(12f);
        int ph = dp(ctx, 16);
        tv.setPadding(ph, dp(ctx, 8), ph, dp(ctx, 4));
        return tv;
    }

    /**
     * 头图块（随内容滚动）—— 居中标题 + 致谢文案 + 版本号，浅灰大气，仿竞品/苹果设置头。
     * 返回 ← 不在这里（由 buildGuardOverlay 固定悬浮在左上角）。
     */
    private static View buildHeaderBlock(Context ctx) {
        LinearLayout band = new LinearLayout(ctx);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setBackgroundColor(Color.parseColor("#F2F2F7"));
        band.setGravity(Gravity.CENTER_HORIZONTAL);
        int sbar = getStatusBarHeight(ctx);
        band.setPadding(dp(ctx, 16), sbar + dp(ctx, 13), dp(ctx, 16), dp(ctx, 20));

        TextView title = new TextView(ctx);
        title.setText("量子密友设置");
        title.setTextColor(Color.parseColor("#1C1C1E"));
        title.setTextSize(18f);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        band.addView(title);

        TextView thanks = new TextView(ctx);
        thanks.setText("感谢您使用量子密友");
        thanks.setTextColor(Color.parseColor("#07A85C"));
        thanks.setTextSize(13f);
        thanks.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(ctx, 12);
        band.addView(thanks, tlp);

        TextView ver = new TextView(ctx);
        ver.setText("Version 1.0.0");
        ver.setTextColor(Color.parseColor("#9A9AA0"));
        ver.setTextSize(11.5f);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(ctx, 3);
        band.addView(ver, vlp);

        return band;
    }

    private static int getStatusBarHeight(Context ctx) {
        try {
            int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return ctx.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {}
        return dp(ctx, 24);
    }

    private static View buildNotifyModeRow(final Context ctx, final Bridge br) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(ctx, 16);
        int pv = dp(ctx, 10);
        row.setPadding(ph, pv, ph, pv);

        TextView label = new TextView(ctx);
        label.setText("\u901a\u77e5\u6a21\u5f0f");
        label.setTextColor(Color.parseColor("#191919"));
        label.setTextSize(17f);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // iOS 风格胶囊分段控件：圆角灰底轨道 + 选中段浅绿圆角药丸。
        final int selBg      = Color.parseColor("#C8ECD0"); // 选中段浅绿底
        final int selText    = Color.parseColor("#07A85C"); // 选中段绿字
        final int normalText = Color.parseColor("#555555"); // 未选中灰字

        LinearLayout seg = new LinearLayout(ctx);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(Color.parseColor("#E9E9EB"));        // 轨道灰底
        track.setCornerRadius(dp(ctx, 9));
        seg.setBackground(track);
        int sp = dp(ctx, 2);
        seg.setPadding(sp, sp, sp, sp);

        final String[] titles = {"\u9759\u9ed8", "\u9707\u52a8", "\u94c3\u58f0"}; // 静默/震动/铃声
        final TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView t = new TextView(ctx);
            t.setText(titles[i]);
            t.setTextSize(13f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(ctx, 14), dp(ctx, 5), dp(ctx, 14), dp(ctx, 5));
            seg.addView(t);
            segs[i] = t;
        }

        // 当前已生效档：铃声未实现 → SOUND/OFF 一律回落到「静默」选中。
        final Bridge.NotifyPolicy[] applied = { br.getNotifyPolicy() };
        final int[] sel = { (applied[0] == Bridge.NotifyPolicy.VIBRATE) ? 1 : 0 };

        final Runnable repaint = new Runnable() {
            @Override public void run() {
                for (int i = 0; i < 3; i++) {
                    if (i == sel[0]) {
                        GradientDrawable pill = new GradientDrawable();
                        pill.setColor(selBg);
                        pill.setCornerRadius(dp(ctx, 7));
                        segs[i].setBackground(pill);
                        segs[i].setTextColor(selText);
                        segs[i].getPaint().setFakeBoldText(true);
                    } else {
                        segs[i].setBackground(null);
                        segs[i].setTextColor(normalText);
                        segs[i].getPaint().setFakeBoldText(false);
                    }
                    segs[i].invalidate();
                }
            }
        };
        repaint.run();

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            segs[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (idx == 2) {
                        // 铃声为占位档：提示「功能更新中」，不改选中、不落库。
                        Toast.makeText(ctx, "\u529f\u80fd\u66f4\u65b0\u4e2d..",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sel[0] = idx;
                    if (idx == 0) {
                        applied[0] = Bridge.NotifyPolicy.OFF;
                        br.setNotifyPolicy(Bridge.NotifyPolicy.OFF);
                    } else {
                        applied[0] = Bridge.NotifyPolicy.VIBRATE;
                        br.setNotifyPolicy(Bridge.NotifyPolicy.VIBRATE);
                        // 选「震动」即时给一次震动反馈（与来电提示一致）
                        try {
                            com.ghost.assist.moduleC.NotifyRouter.fireAlert(
                                    ctx.getApplicationContext(),
                                    com.ghost.assist.moduleC.NotifyRouter.EventType.MSG);
                        } catch (Throwable ignored) {}
                    }
                    repaint.run();
                    Log.i(TAG, "[SET:overlay] notifyMode=" + br.getNotifyPolicy());
                }
            });
        }

        row.addView(seg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    /** 来电（语音/视频）通知模式：仅 静默 / 震动 两态，默认静默。来电永不放铃声。 */
    private static View buildCallNotifyModeRow(Context ctx, final Bridge br) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dp(ctx, 16);
        int pv = dp(ctx, 8);
        row.setPadding(ph, pv, ph, pv);

        TextView label = new TextView(ctx);
        label.setText("\u6765\u7535\u63d0\u793a");
        label.setTextColor(Color.parseColor("#191919"));
        label.setTextSize(17f);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbOff     = new RadioButton(ctx);
        final RadioButton rbVibrate = new RadioButton(ctx);
        rbOff.setText("\u9759\u9ed8");      // 静默（默认）
        rbVibrate.setText("\u9707\u52a8");  // 震动
        rg.addView(rbOff);
        rg.addView(rbVibrate);
        Bridge.NotifyPolicy current = br.getCallNotifyPolicy();
        if (current == Bridge.NotifyPolicy.VIBRATE) rbVibrate.setChecked(true);
        else                                        rbOff.setChecked(true);
        final Context appCtx = ctx.getApplicationContext();
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                if (id == rbVibrate.getId()) {
                    br.setCallNotifyPolicy(Bridge.NotifyPolicy.VIBRATE);
                    // Live preview: let the owner feel the call vibration on selection.
                    try {
                        com.ghost.assist.moduleC.NotifyRouter.fireAlert(
                                appCtx, com.ghost.assist.moduleC.NotifyRouter.EventType.CALL);
                    } catch (Throwable ignored) {}
                } else {
                    br.setCallNotifyPolicy(Bridge.NotifyPolicy.OFF);
                }
                Log.i(TAG, "[SET:overlay] callNotifyMode=" + br.getCallNotifyPolicy());
            }
        });
        row.addView(rg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    private static void showWxidListDialog(Activity activity) {
        Bridge br = Bridge.getInstance();
        java.util.Set<String> wxids = br.getWxids();
        if (wxids == null || wxids.isEmpty()) {
            Toast.makeText(activity, "\u6682\u65e0\u5bc6\u53cb", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = wxids.toArray(new String[0]);
        new AlertDialog.Builder(activity)
                .setTitle("\u5bc6\u53cb\u5217\u8868 (\u70b9\u51fb\u79fb\u9664)")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Bridge.getInstance().removeWxid(items[which]);
                        Log.i(TAG, "[SET:overlay] remove wxid=" + items[which]);
                    }
                })
                .setNegativeButton("\u5173\u95ed", null)
                .show();
    }

    private static void showAddWxidDialog(final Activity activity) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("\u8f93\u5165 wxid");
        new AlertDialog.Builder(activity)
                .setTitle("\u6dfb\u52a0\u5bc6\u53cb")
                .setView(input)
                .setPositiveButton("\u6dfb\u52a0", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String wxid = input.getText().toString().trim();
                        if (wxid.isEmpty()) return;
                        Bridge.getInstance().addWxid(wxid);
                        Log.i(TAG, "[SET:overlay] add wxid=" + wxid);
                        Toast.makeText(activity,
                                "\u5df2\u6dfb\u52a0: " + wxid, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private static void showGroupListDialog(Activity activity) {
        Bridge br = Bridge.getInstance();
        java.util.Set<String> groups = br.getGroupIds();
        if (groups == null || groups.isEmpty()) {
            Toast.makeText(activity, "\u6682\u65e0\u5bc6\u7fa4", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = groups.toArray(new String[0]);
        new AlertDialog.Builder(activity)
                .setTitle("\u5bc6\u7fa4\u5217\u8868 (\u70b9\u51fb\u79fb\u9664)")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Bridge.getInstance().removeGroupId(items[which]);
                        Log.i(TAG, "[SET:overlay] remove group=" + items[which]);
                    }
                })
                .setNegativeButton("\u5173\u95ed", null)
                .show();
    }

    private static Activity unwrapActivity(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            Context base = ((ContextWrapper) ctx).getBaseContext();
            if (base == ctx) return null;
            ctx = base;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // One-shot immediate view tree probe
    // -----------------------------------------------------------------------

    private static void diagViewTree(ViewGroup root) {
        Log.i(TAG, "[SET:tree] ===== START root=" + root.getClass().getName() + " =====");
        walkViewTree(root, 0);
        Log.i(TAG, "[SET:tree] ===== END =====");
    }

    private static void walkViewTree(ViewGroup vg, int depth) {
        if (depth > 8) return;
        String pad = "  ".repeat(depth);
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child == null) continue;
            String cn    = child.getClass().getName();
            String extra = "";
            if (child instanceof ListView) {
                ListAdapter a = ((ListView) child).getAdapter();
                extra = " [ListView] adapter=" + (a != null ? a.getClass().getName() : "null")
                        + " count=" + ((ListView) child).getCount();
            } else if (cn.contains("RecyclerView")) {
                extra = " [RecyclerView!] w=" + child.getWidth() + " h=" + child.getHeight();
            }
            Log.i(TAG, "[SET:tree] " + pad + "[" + i + "] " + cn + extra);
            if (child instanceof ViewGroup) walkViewTree((ViewGroup) child, depth + 1);
        }
    }

    // -----------------------------------------------------------------------
    // View tree search
    // -----------------------------------------------------------------------

    private static ListView findListViewRecursive(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ListView) return (ListView) child;
            if (child instanceof ViewGroup) {
                ListView found = findListViewRecursive((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findRecyclerViewRecursive(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getClass().getName().contains("RecyclerView")) return child;
            if (child instanceof ViewGroup) {
                View found = findRecyclerViewRecursive((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findFirstTextView(View v) {
        if (v instanceof TextView) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
        for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findFirstTextView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTextViewContaining(View v, String needle) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.toString().contains(needle)) return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findTextViewContaining(vg.getChildAt(i), needle);
                if (found != null) return found;
            }
        }
        return null;
    }

    // P_SE1: 严格等值匹配（避免 "个人资料" 被 "个人资料隐私" 等长名命中）。
    private static TextView findExactText(View v, String exact) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && exact.contentEquals(text)) return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findExactText(vg.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Q4: 找设置菜单"个人资料"行的 title TextView。
     * 需要同时识别已替换为"量子密友"的状态，否则状态机切换后无法再找到这一行。
     */
    private static TextView findProfileRowTitle(View v) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null
                    && (PROFILE_TEXT.contentEquals(text) || MIYOU_TEXT.contentEquals(text))) {
                return (TextView) v;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findProfileRowTitle(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ViewGroup findProfileRowAncestor(TextView wxidText) {
        ViewParent p = wxidText.getParent();
        for (int depth = 0; depth < 8 && p instanceof ViewGroup; depth++) {
            ViewGroup vg = (ViewGroup) p;
            if (containsImageView(vg) && findProfileTitle(vg, wxidText) != null) {
                return vg;
            }
            p = vg.getParent();
        }
        return null;
    }

    private static boolean containsImageView(View v) {
        if (v instanceof ImageView) return true;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsImageView(vg.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static TextView findProfileTitle(View v, TextView wxidText) {
        TextView best = null;
        if (v instanceof TextView && v != wxidText) {
            TextView tv = (TextView) v;
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0 && !text.toString().contains("微信号")) {
                best = tv;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findProfileTitle(vg.getChildAt(i), wxidText);
                if (found == null) continue;
                if (best == null || found.getTextSize() > best.getTextSize()) {
                    best = found;
                }
            }
        }
        return best;
    }

    private static View.OnClickListener getCurrentOnClickListener(View v) {
        try {
            java.lang.reflect.Method getListenerInfo =
                    View.class.getDeclaredMethod("getListenerInfo");
            getListenerInfo.setAccessible(true);
            Object listenerInfo = getListenerInfo.invoke(v);
            if (listenerInfo == null) return null;
            java.lang.reflect.Field f = listenerInfo.getClass().getDeclaredField("mOnClickListener");
            f.setAccessible(true);
            Object listener = f.get(listenerInfo);
            return listener instanceof View.OnClickListener
                    ? (View.OnClickListener) listener
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // disable clickable on all child views so clicks bubble up to row
    private static void disableChildClicks(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                child.setClickable(false);
                child.setFocusable(false);
                disableChildClicks(child);
            }
        }
    }

    // P_SE1 R1 整行热区：把同一个 hijack listener 装到 root 的全部后代。
    // 无论 Android 把 click 派发到 root 还是哪个子 View，触发的都是同一个 hijack。
    /**
     * P_SE5: 状态机切到 HIDDEN 后，卸掉所有装在 row 子树上的 hijack listener，
     * 让 RecyclerView.OnItemTouchListener 自然处理 click，走 WeChat 原生跳 Fragment。
     */
    private static void detachHijackFromAllDescendants(View v) {
        v.setOnClickListener(null);
        v.setClickable(false);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                detachHijackFromAllDescendants(vg.getChildAt(i));
            }
        }
    }

    private static void attachHijackToAllDescendants(View v, View.OnClickListener listener) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                child.setOnClickListener(listener);
                attachHijackToAllDescendants(child, listener);
            }
        }
    }

    // walk subtree to find first OnClickListener (for full-row hijack)
    private static View.OnClickListener findFirstOnClickListener(View v) {
        View.OnClickListener listener = getCurrentOnClickListener(v);
        if (listener != null) return listener;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                listener = findFirstOnClickListener(vg.getChildAt(i));
                if (listener != null) return listener;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static ViewGroup getContentRoot(Activity activity) {
        try {
            ViewGroup frame = (ViewGroup) activity.findViewById(android.R.id.content);
            if (frame == null) return null;
            if (frame.getChildCount() == 0) return frame;
            View child = frame.getChildAt(0);
            return (child instanceof ViewGroup) ? (ViewGroup) child : frame;
        } catch (Throwable t) { return null; }
    }

    private static int dp(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
