package com.ghost.assist.moduleB;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
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
import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.AuthManager;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.net.EnvelopeClient;
import com.ghost.assist.net.EnvelopeStore;
import com.ghost.assist.net.GuardActivation;
import com.ghost.assist.net.GuardHeartbeat;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * SettingsEntry — 微信「我 → 设置」页的密友设置入口。
 *
 * 主入口（设置页）：在「我 → 设置」页「个人资料」行上方注入「量子密友设置 ›」。
 *   - 锚点 = 「个人资料」那一行（findSettingsListByProfileRow）：找到该文本且祖先里有
 *     RecyclerView/ListView 的列表 → 就是设置主列表；banner 悬浮在列表最顶（= 个人资料上面）。
 *   - 渲染：8.0.71 实测 adapter hook（pz3.g onBindViewHolder）0 命中（死路径），改为在列表父层
 *     注入悬浮 guardRow banner + 顶部 padding + 跟随滚动（syncLlHeader / overlayHostFor）。
 *
 * 备用入口（「我」首页）：解锁态下把「我」首页个人资料行改成「量子密友」，点它弹面板
 *   （scheduleMoreTabProfileProbe / syncMoreTabProfileEntry）。主入口万一某机型没出来时兜底。
 *   仅在 LauncherUI 生效，只改行标题、不动「个人资料」详情页的真实微信号值。
 *
 * 铁律：
 *   - 不改 WeChat Adapter 数据字段
 *   - catch (Throwable) 全部静默
 */
public class SettingsEntry {

    private static final String TAG = "NCL";

    private static final String MAIN_SETTINGS_CLASS =
            "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI";
    // P_SE5: 8.0.71 实测设置页是 CommonSettingsUI（不是 MainSettingsUI）。
    // 两个都监听以兼容不同版本入口。
    private static final String COMMON_SETTINGS_CLASS =
            "com.tencent.mm.plugin.setting.ui.setting_new.CommonSettingsUI";
    // 备用入口：右下角「我」首页（LauncherUI）——主入口(设置页那行)万一没出来时兜底。
    private static final String LAUNCHER_UI_CLASS =
            "com.tencent.mm.ui.LauncherUI";
    private static final int MORE_PROFILE_TAG = 0x67757072; // "gupr"
    private static final int PROFILE_LP_TAG   = 0x67756c70; // "gulp" 设置页个人资料行长按备用入口

    private static volatile boolean sInstalled      = false;
    private static volatile boolean sDiagDone       = false;

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

    // P_SE5: overlay 显示期间禁止状态机 enterHidden。
    // 用户在密友设置面板里配置密友时不应被误触发拉回 H。
    // TriggerGuard 各 enterHidden 路径检查这个 flag、true 时跳过。
    private static volatile boolean sOverlayActive    = false;
    private static volatile boolean sAuthDialogShowing       = false;
    private static volatile boolean sUpdateNoticeShown       = false;

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

    private static boolean isActivationReady(Context ctx) {
        try {
            if (ctx != null) EnvelopeStore.init(ctx.getApplicationContext());
            return EnvelopeStore.isAuthorizedNow();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * P_SE5: 状态机切换时主动刷 banner（不依赖 onResume 重新 fire）。
     * TriggerGuard 触发 enterHidden 后、overlay 状态切换按钮 dismiss 后调本方法。
     */
    public static void onStateChanged() {
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

    /**
     * E2 伪装定位：从原生选点页设置坐标回来后即时刷新「选择伪装位置」当前值。
     * 由 FakeLocation.captureLocationIntent 调用；overlay 未显示时静默跳过。
     */
    public static void refreshFakeLocation() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                try {
                    TextView t = sFakeLocLabelRef != null ? sFakeLocLabelRef.get() : null;
                    if (t != null) {
                        t.setText(fakeLocLabelText(Bridge.getInstance()) + " \u203a");
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    /** 「选择伪装位置」行右侧当前值文案：有坐标→POI名/「已设置」，无→「未设置」。 */
    private static String fakeLocLabelText(Bridge br) {
        if (!br.hasFakeLocation()) return "\u672a\u8bbe\u7f6e"; // 未设置
        String label = br.getFakeLocLabel();
        return (label != null && !label.isEmpty()) ? label : "\u5df2\u8bbe\u7f6e"; // 已设置
    }

    // 备用入口：右下角「我」首页的头像行（LauncherUI）。主入口(设置页那行)万一某机型没出来时兜底。
    // 只在「我」首页生效，不碰「个人资料」详情页（详情页微信号保持真实 wxid）。
    private static volatile WeakReference<View> sMoreProfileRowRef;
    private static volatile WeakReference<TextView> sMoreTitleRef;
    private static volatile WeakReference<View.OnClickListener> sMoreOriginalClickRef;
    private static volatile String sMoreOriginalTitle;

    // P_IMPORT: overlay 内「密友列表 / 密群列表」的「已选择 N 个」计数 TextView 引用。
    // 导入/移除从 SelectContactUI 回来后，ContactImportGuard.consumeResult 调
    // refreshImportCounts() 即时刷新这两个数字（修「导入成功 UI 不立刻刷新」bug）。
    private static volatile WeakReference<TextView> sBuddyCountRef;
    private static volatile WeakReference<TextView> sGroupCountRef;

    // E2 伪装定位：overlay 内「选择伪装位置」当前值 TextView 引用。
    // 从原生选点页设置坐标回来后，FakeLocation.captureLocationIntent 调 refreshFakeLocation() 即时刷新。
    private static volatile WeakReference<TextView> sFakeLocLabelRef;


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
                                    // 主入口：syncEntry 靠「个人资料」行判定是不是设置主页：
                                    // 是主页 → 在其上方注入入口行；不是（子页）→ 收掉可能残留的 banner。
                                    // 带延迟重试：首次进设置页 onResume 时列表可能还没布局好，
                                    // 立即跑会找不到「个人资料」行 → 需返回再进/点其他才刷出来。
                                    // 0/250/700ms 三拨补跑，列表一布局好就注入，不用用户再操作。
                                    scheduleSyncEntry(activity);
                                } else if (LAUNCHER_UI_CLASS.equals(cls)) {
                                    // 备用入口：「我」首页个人资料行改「量子密友」（解锁态自显）。
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

        // 备用入口 hook：任何带「微信号」字样的 TextView attach 时改成「量子密友」（解锁态自显）。
        // 这是最早截图那个行为（个人资料页微信号→量子密友），作为主入口没出来时的兜底。
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
                            if (text == null || !text.toString().contains("\u5fae\u4fe1\u53f7")) return;
                            // 仅「我」首页 LauncherUI 放行；其他含「头像+微信号+大字」的页面
                            // （转账页 RemittanceUI 等）会被 findProfileTitle 误选中大字控件当标题。
                            Activity attachAct = unwrapActivity(tv.getContext());
                            if (attachAct == null
                                    || !LAUNCHER_UI_CLASS.equals(attachAct.getClass().getName())) return;
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

        // P_SE3: 量子密友文字防覆写 hook —— 微信内部 binder 可能在后续 layout 把文字改回，
        // 专拦 sMoreTitleRef 这一个实例：仅 VISIBLE + this==sMoreTitleRef + args[0]!=量子密友 时改回。
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
                                        + incoming + " -> \u91cf\u5b50\u5bc6\u53cb");
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

    /**
     * 进设置页：即时试一次，然后挂 OnGlobalLayout —— 列表一旦布局好（个人资料行出现）就注入，
     * 不再靠固定延迟猜（慢机上 700ms 也可能不够，导致要返回再进/点其他才刷出）。
     * 注入成功 / 该隐藏 / 超 4s 任一 → 自摘监听，避免常驻。syncEntry 幂等。
     */
    private static void scheduleSyncEntry(final Activity activity) {
        syncEntry(activity);
        final ViewGroup root = getContentRoot(activity);
        if (root == null) return;
        final long start = System.currentTimeMillis();
        final android.view.ViewTreeObserver.OnGlobalLayoutListener[] holder =
                new android.view.ViewTreeObserver.OnGlobalLayoutListener[1];
        holder[0] = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                View banner = sHeaderRowRef != null ? sHeaderRowRef.get() : null;
                boolean injected = banner != null && banner.getParent() != null;
                boolean done = injected || !shouldShowEntry()
                        || (System.currentTimeMillis() - start) > 4000;
                if (done) {
                    try {
                        android.view.ViewTreeObserver vto = root.getViewTreeObserver();
                        if (vto.isAlive()) vto.removeOnGlobalLayoutListener(holder[0]);
                    } catch (Throwable ignored) {}
                    return;
                }
                syncEntry(activity);
            }
        };
        try {
            root.getViewTreeObserver().addOnGlobalLayoutListener(holder[0]);
        } catch (Throwable ignored) {}
    }

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

        // 锚点 = 「个人资料」那一行所在的列表（RecyclerView / ListView）。
        // 只认「个人资料」文本且其祖先里有 RV/LV 的 —— 详情页的「个人资料」标题栏没有
        // 列表祖先，天然被排除；子页（通用/隐私等）没有「个人资料」行，也进不来。
        // 找不到 = 不是设置主页 → 收掉可能残留的 banner。不再靠标题栏猜测、不靠搜索框位置。
        View list = findSettingsListByProfileRow(root);
        if (list == null) {
            removeLlHeader();
            return;
        }

        boolean shouldShow = shouldShowEntry();
        if (list instanceof ListView) {
            syncListViewHeader((ListView) list, activity, shouldShow);
        } else {
            handleRecyclerView(list, activity, shouldShow);
        }
        attachProfileRowLongPressBackup(root);
    }

    // 备用入口②（长按 · 2026-07-09）：设置页「个人资料」整行长按 → 弹量子密友面板。
    // 只加长按、不碰普通点击（普通点击照常进真·个人资料页，零破坏）；主入口 banner 某机型
    // 没注入出来时的兜底。仅 shouldShowEntry()（显形态）挂、隐藏态摘。EntryGate：只开面板，
    // 不碰授权/状态机/过滤（授权检查官改前审 PASS）。
    private static void attachProfileRowLongPressBackup(ViewGroup root) {
        try {
            TextView profileTv = findTextViewContaining(root, "\u4e2a\u4eba\u8d44\u6599"); // 个人资料
            if (profileTv == null) return;
            View row = null;
            ViewParent p = profileTv.getParent();
            for (int i = 0; i < 5 && p instanceof View; i++) {
                View pv = (View) p;
                row = pv;
                if (pv.isClickable()) break;
                p = pv.getParent();
            }
            if (row == null) return;
            if (!shouldShowEntry()) {
                if (row.getTag(PROFILE_LP_TAG) != null) {
                    row.setOnLongClickListener(null);
                    row.setTag(PROFILE_LP_TAG, null);
                }
                return;
            }
            if (row.getTag(PROFILE_LP_TAG) != null) return;
            final Context ctx = row.getContext();
            row.setLongClickable(true);
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    Log.i(TAG, "[SET:lp] profile row long-press -> overlay");
                    showGuardOverlay(ctx);
                    return true;
                }
            });
            row.setTag(PROFILE_LP_TAG, Boolean.TRUE);
            Log.i(TAG, "[SET:lp] profile row long-press backup attached");
        } catch (Throwable t) {
            Log.w(TAG, "[SET:lp] attach fail: " + t);
        }
    }

    /**
     * 递归找「个人资料」那一行（精确文本）且其祖先里有 RecyclerView/ListView 的那个列表。
     * 返回该列表 View（RecyclerView 或 ListView），找不到返回 null。
     */
    private static View findSettingsListByProfileRow(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && "\u4e2a\u4eba\u8d44\u6599".contentEquals(t)) { // 个人资料
                View list = findListAncestor(v);
                if (list != null) return list;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findSettingsListByProfileRow(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 从某个 View 往上找最近的 RecyclerView / ListView 祖先（≤12 层）。 */
    private static View findListAncestor(View v) {
        ViewParent p = v.getParent();
        for (int depth = 0; depth < 12 && p instanceof View; depth++) {
            if (p instanceof ListView) return (View) p;
            if (p.getClass().getName().contains("RecyclerView")) return (View) p;
            p = p.getParent();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // 备用入口：「我」首页个人资料行 → 量子密友（解锁态自显；主入口那行没出来时兜底）
    // 保留最早的行为：个人资料页带「微信号」的行，解锁态自动显示成「量子密友」，点它弹面板。
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
        TextView wxidText = findTextViewContaining(root, "\u5fae\u4fe1\u53f7"); // 微信号
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
        // 8.0.71 实测 pz3.g.onBindViewHolder hook 0 命中（死路径），无法靠 adapter hook 注入新行。
        // 改为在 RV 父层注入悬浮 banner（syncLlHeader / overlayHostFor）—— 不依赖 adapter hook、稳定可靠。
        // P_SE7: 保存 RV 引用让 syncLlHeader 给 scroll-follow 监听器用。
        sRvRef = new WeakReference<>(rv);
        ViewGroup ll = findRvContainerLinearLayout(rv);
        if (ll == null) {
            // 兜底：容器猜不中时用 RV 的直接父层（syncLlHeader 优先走 overlayHostFor(rv)，
            // sibling 注入才用到 ll），保证换 OEM 层级时仍能落地。
            ViewParent p = rv.getParent();
            if (p instanceof ViewGroup) ll = (ViewGroup) p;
        }
        if (ll != null) {
            syncLlHeader(ll, activity, shouldShow);
        } else {
            Log.w(TAG, "[SET] no container for banner (rv parent null)");
        }
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
        // 对齐原生设置行字号（原来 17f 明显比「个人资料」大）。
        title.setTextSize(16f);
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
            // 授权态：跑 更新通知 / 续费预警 / 断网>72h 重验。
            // 未授权 / 到期 / 封停（非影子期）：进功能页弹引导（#1，用户拍板改 D-018 旧「零弹」口径）。
            //   只在用户主动进本功能页时弹，不全局「打开即弹」、不误伤普通微信用户；
            //   蜜罐影子期是另一套逻辑（RiskState / RiskPromptController），不走这里。
            if (isActivationReady(activity)) {
                showUpdateNoticeIfNeeded(activity);
                showRenewReminderIfNeeded(activity);   // #3 到期前 7 天每天功能页预警续费
                // S3b-B：进设置页 = 算账检查点。断网 >72h 强制重验，失败→撤销+提示。
                maybeStaleReverify(activity, decor);
            } else {
                showAuthGuidePrompt(activity);          // #1 未授权/到期/封停 → 引导激活/续费/客服
            }
            Log.i(TAG, "[SET:overlay] shown state="
                    + StateMachine.getInstance().getStateName());
        } catch (Throwable t) {
            Log.w(TAG, "[SET:overlay] show failed: " + t);
        }
    }

    /**
     * S3b-B：设置页"算账检查点"。后台调 GuardHeartbeat.reverifyIfStale（断网 >72h 才真跑），
     * 若被撤销 → 主线程关设置页 + 弹时间错误提示。授权决策在 net 层，这里只触发 + 展示。
     */
    private static void maybeStaleReverify(final Activity activity, final ViewGroup decor) {
        try {
            if (!EnvelopeStore.isAuthorizedNow()) return;
            final String deviceId = AuthManager.computeDeviceHash(activity);
            EnvelopeClient.runAsync(new Runnable() {
                @Override public void run() {
                    final boolean stillAuth = GuardHeartbeat.reverifyIfStale(
                            deviceId, "", AppConfig.GUARD_PRODUCT_VERSION);
                    if (stillAuth) return;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            dismissOverlay(decor);
                            showTimeErrorDialog(activity);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "[SET:overlay] stale reverify trigger failed: " + t);
        }
    }

    /** S3b-B：授权重验失败（断网 >72h）时的提示弹窗。白色圆角卡片，与更新提示同款。 */
    private static void showTimeErrorDialog(final Activity activity) {
        try {
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(activity, 20));
            box.setBackground(bg);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText("授权验证失败");
            tvTitle.setTextColor(Color.parseColor("#1C1C1E"));
            tvTitle.setTextSize(18f);
            tvTitle.getPaint().setFakeBoldText(true);
            tvTitle.setGravity(Gravity.CENTER);
            box.addView(tvTitle);

            TextView tvMsg = new TextView(activity);
            tvMsg.setText("当前时间错误，授权验证失败，请检查时间");
            tvMsg.setTextColor(Color.parseColor("#555555"));
            tvMsg.setTextSize(14f);
            tvMsg.setGravity(Gravity.CENTER);
            tvMsg.setLineSpacing(dp(activity, 3), 1.0f);
            LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            msgLp.topMargin = dp(activity, 12);
            box.addView(tvMsg, msgLp);

            final AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
            dialog.setCancelable(false);

            Button ok = new Button(activity);
            ok.setText("我知道了");
            ok.setTextColor(Color.WHITE);
            ok.setTextSize(14f);
            ok.getPaint().setFakeBoldText(true);
            GradientDrawable okBg = new GradientDrawable();
            okBg.setColor(Color.parseColor("#07A85C"));
            okBg.setCornerRadius(dp(activity, 18));
            ok.setBackground(okBg);
            LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 38));
            okLp.topMargin = dp(activity, 18);
            box.addView(ok, okLp);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dialog.dismiss(); }
            });

            dialog.show();
        } catch (Throwable t) {
            Log.w(TAG, "[SET:overlay] time-error dialog failed: " + t);
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
        final boolean authorizedNow = StateMachine.getInstance().isVipAuthorized();

        // 1. 开启密友（总开关）
        content.addView(buildSwitchRow(activity, "开启密友",
                authorizedNow ? "密友功能总开关" : "请先完成授权",
                authorizedNow && br.isFeatureEnabled(),
                authorizedNow ? new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setFeatureEnabled(checked);
                        Log.i(TAG, "[SET:overlay] feature=" + checked);
                    }
                } : null));

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

        // M6a 朋友圈「可见分组」图标隐藏（默认开 = 过滤藏图标；关 = 不过滤）。
        // 只藏自己受限帖右下角分组图标，不碰删除键；时间线/详情页生效（相册页 Flutter 不做）。
        content.addView(buildSwitchRow(activity, "隐藏朋友圈分组图标",
                "藏掉自己「仅可见分组」帖子右下角的小图标",
                AppConfig.getInstance().isMomentsGroupIconEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        AppConfig.getInstance().setMomentsGroupIconEnabled(checked);
                        Log.i(TAG, "[SET] momentsGroupIcon=" + checked);
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

        // B1 入口：默认关，用户显式打开后才注册加速度传感器。
        content.addView(buildSwitchRow(activity, "摇一摇隐藏好友",
                "显形时摇一摇立即进入隐藏态",
                AppConfig.getInstance().isB1Enabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        TriggerGuard.setShakeEnabled(activity.getApplicationContext(), checked);
                        Log.i(TAG, "[SET:overlay] b1Shake=" + checked);
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

        // 7. 密码设置：只改 EntryGate 口令，不切状态、不碰过滤链。
        final TextView[] passwordOut = new TextView[1];
        content.addView(buildButtonRow(activity, "密码设置",
                currentPasswordLabel(sm),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showPasswordSettingDialog(activity, passwordOut[0]);
                    }
                }, passwordOut));

        // ===== 使用教程（服务器下发温馨提示 · 仅已激活可见 · 后台清空则整组隐藏）=====
        // 单条运营提示（tip），验签信封搭载下发；点开走公告卡（沿用 showStyledPrompt 弹窗风格）。
        if (EnvelopeStore.isAuthorizedNow() && EnvelopeStore.hasTip()) {
            final String tipTitle = EnvelopeStore.getTipTitle();
            final String tipBody = EnvelopeStore.getTipBody();
            final String tipUrl = EnvelopeStore.getTipUrl();
            content.addView(buildSectionHeader(activity, "使用教程"));
            content.addView(buildTipRow(activity,
                    (tipTitle == null || tipTitle.isEmpty()) ? "使用教程" : tipTitle,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            showTipAnnouncement(activity, tipTitle, tipBody, tipUrl);
                        }
                    }));
        }

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

        // ===== 特色功能 =====
        content.addView(buildSectionHeader(activity, "特色功能"));
        // 伪装定位（E2）：总开关 + 复用微信原生选点页设置坐标，全局生效。
        content.addView(buildSwitchRow(activity, "伪装定位",
                "全局伪造定位（发位置/共享/朋友圈/附近的人）",
                br.isFakeLocationEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setFakeLocationEnabled(checked);
                        Log.i(TAG, "[SET:overlay] fakeLoc=" + checked);
                    }
                }));
        final TextView[] fakeLocOut = new TextView[1];
        content.addView(buildButtonRow(activity, "\u9009\u62e9\u4f2a\u88c5\u4f4d\u7f6e",
                fakeLocLabelText(br),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        com.ghost.assist.moduleE.FakeLocation.launchPicker(activity);
                    }
                }, fakeLocOut));
        sFakeLocLabelRef = new WeakReference<>(fakeLocOut[0]);
        // 改余额显示（E3）：全局伪造钱包/零钱余额数字（纯显示层，不碰真钱/支付）；
        // 门控 isVipAuthorized() && isEditBalanceEnabled() && 已填自定义金额（留空=不改）；不绑 H/V。
        content.addView(buildSwitchRow(activity, "改余额显示",
                "全局伪造钱包/零钱余额（仅显示，不碰真钱）",
                br.isEditBalanceEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        br.setEditBalanceEnabled(checked);
                        Log.i(TAG, "[SET:overlay] editBal=" + checked);
                    }
                }));
        // 自定义金额：点击填数字（方案②末两位自动为小数）→ Bridge.setFakeBalanceYuan；留空=不生效。
        final TextView[] fakeBalOut = new TextView[1];
        content.addView(buildButtonRow(activity, "自定义金额",
                fakeBalanceLabel(br),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showBalanceInputDialog(activity, fakeBalOut[0]);
                    }
                }, fakeBalOut));
        content.addView(buildNote(activity, "独家功能 · 请低调使用"));

        // ===== 通知（密友消息 + 来电 合并为一个小分组）=====
        content.addView(buildSectionHeader(activity, "通知"));
        content.addView(buildNotifyModeRow(activity, br));      // 密友消息通知模式
        content.addView(buildCallNotifyModeRow(activity, br));  // 来电提示
        content.addView(buildNote(activity, "推荐静默"));

        // ===== 授权 =====
        content.addView(buildSectionHeader(activity, "\u6388\u6743"));
        final TextView[] authStatusOut = new TextView[1];
        // 授权码行：授权后把「输入 / 激活」换成绿色「已授权」色块（激活成功面板会整块重建，
        // 故只需按 isAuthorizedNow 在建行时渲染，无需单独刷新引用）。
        final boolean authedNow = EnvelopeStore.isAuthorizedNow();
        final TextView[] authCodeOut = new TextView[1];
        content.addView(buildButtonRow(activity, "\u6388\u6743\u7801",
                authedNow ? "\u5df2\u6388\u6743" : "\u8f93\u5165 / \u6fc0\u6d3b",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showActivationDialog(activity, authStatusOut[0], "settings-row");
                    }
                }, authCodeOut));
        if (authedNow) styleAuthorizedPill(activity, authCodeOut[0]);
        content.addView(buildButtonRow(activity, "\u6388\u6743\u72b6\u6001",
                activationStatusText(activity),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showActivationDialog(activity, authStatusOut[0], "status-row");
                    }
                }, authStatusOut));
        content.addView(buildTextRow(activity, "\u5230\u671f\u65f6\u95f4",
                activationExpireText(activity)));

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

    private static final int RENEW_WARN_DAYS = 7;   // #3 到期前 N 天起每天功能页预警续费

    /**
     * #3 续费预警：授权到期前 RENEW_WARN_DAYS 天起，进功能页每天弹一次提醒续费——他进来就看到，
     * 赶在到期前续，自然不被「到期失效」暴露。客户端自己算（手里有到期时间），用可信时间防改墙钟刷弹、
     * 按天去重。文案 / 续费链接走默认（SHOP_URL）；服务器侧文案 / 开关 / 天数下发为后续（保留服务器控制）。
     * 注意：隐私侧到期当场失效、无到期后宽限（EnvelopeStore.isLicenseExpired 硬判 exp<=trustedNow）；
     * 本弹窗是「到期前」预警，与配方卡「到期后 A2 多开 7 天宽限」是两回事，别混。
     */
    private static void showRenewReminderIfNeeded(final Activity activity) {
        if (activity == null) return;
        if (!EnvelopeStore.isAuthorizedNow()) return;                 // 未授权走激活框，不在此
        long secs = EnvelopeStore.secondsToLicenseExpiry();
        if (secs <= 0 || secs >= RENEW_WARN_DAYS * 86400L) return;    // 已到期 / 还早 → 不提醒
        long today = EnvelopeStore.trustedDay();
        if (today > 0 && EnvelopeStore.getRenewNoticeDay() == today) return;   // 今天已弹
        EnvelopeStore.setRenewNoticeDay(today);
        long daysLeft = (secs + 86399L) / 86400L;                    // 向上取整
        final String url = com.ghost.assist.core.AppConfig.SHOP_URL;
        String msg = "授权将在 " + daysLeft + " 天内到期。到期后密友功能会停用，请及时续费，避免使用中断。";
        // #3 续费提醒：改用统一风格卡片（与引流弹窗 FunnelPrompt 同款白色圆角）。
        showStyledPrompt(activity, "续费提醒", msg, "去续费",
                (url == null || url.isEmpty()) ? null : new Runnable() {
                    @Override public void run() {
                        try {
                            activity.startActivity(new android.content.Intent(
                                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Throwable ignored) {}
                    }
                }, "稍后");
    }

    /**
     * #1/#3 统一风格弹窗（白色圆角卡片，与 FunnelPrompt 同款；程序化构图、无宿主资源依赖）。
     * primaryAction==null → 只显示次按钮（secondaryLabel）。点主按钮先关弹窗再跑动作。
     * 纯 UI，fail-safe（异常只记日志、不崩、不碰微信本体）。
     */
    private static void showStyledPrompt(final Activity activity, String titleText, String messageText,
                                         String primaryLabel, final Runnable primaryAction, String secondaryLabel) {
        if (activity == null) return;
        try {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 24);
            card.setPadding(pad, dp(activity, 30), pad, dp(activity, 24));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(activity, 20));
            card.setBackground(bg);

            TextView title = new TextView(activity);
            title.setText(titleText == null ? "温馨提示" : titleText);
            title.setTextColor(0xFF1A1A1A);
            title.setTextSize(18f);
            title.getPaint().setFakeBoldText(true);
            title.setGravity(Gravity.CENTER);
            card.addView(title);

            TextView msgView = new TextView(activity);
            msgView.setText(messageText == null ? "" : messageText);
            msgView.setTextColor(0xFF666666);
            msgView.setTextSize(14f);
            msgView.setLineSpacing(dp(activity, 3), 1.15f);
            LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            msgLp.topMargin = dp(activity, 22);
            msgView.setLayoutParams(msgLp);
            card.addView(msgView);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(activity, 28);
            row.setLayoutParams(rowLp);

            final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(card).setCancelable(true).create();

            TextView sec = makeStyledBtn(activity,
                    secondaryLabel != null ? secondaryLabel : "知道了", 0xFFEFF3F8, 0xFF1A73E8);
            sec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { try { dialog.dismiss(); } catch (Throwable ignored) {} }
            });
            row.addView(sec);

            if (primaryAction != null && primaryLabel != null) {
                ((LinearLayout.LayoutParams) sec.getLayoutParams()).rightMargin = dp(activity, 10);
                TextView pos = makeStyledBtn(activity, primaryLabel, 0xFF1A73E8, Color.WHITE);
                pos.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                        try { primaryAction.run(); } catch (Throwable ignored) {}
                    }
                });
                row.addView(pos);
            }
            card.addView(row);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.show();
            // 窄化卡片：默认 AlertDialog 偏宽显扁平，收到屏宽 78% 让卡片更竖、不扁（用户口径 2026-06-29）。
            if (dialog.getWindow() != null) {
                int w = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.78f);
                dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[styledPrompt] fail: " + t);
        }
    }

    private static TextView makeStyledBtn(Activity ctx, String text, int bgColor, int textColor) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(ctx, 14), 0, dp(ctx, 14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(ctx, 24));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        b.setClickable(true);
        return b;
    }

    /**
     * #1 未授权/到期/封停 进功能页引导弹窗（蜜罐影子期是另一套逻辑、不走这里）。
     * 三态分别给「去激活 / 去续费 / 联系客服」入口；只在用户进本功能页时弹，不全局打开即弹。
     */
    private static void showAuthGuidePrompt(final Activity activity) {
        if (activity == null) return;
        try {
            final String url = com.ghost.assist.core.AppConfig.SHOP_URL;
            final Runnable openShop = new Runnable() {
                @Override public void run() {
                    try {
                        activity.startActivity(new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) {}
                }
            };
            if (EnvelopeStore.isCardRevoked()) {
                showStyledPrompt(activity, "授权已停用",
                        "当前授权已停用（封停 / 退款撤销）。如有疑问请联系客服处理。",
                        "联系客服", openShop, "稍后");
            } else if (EnvelopeStore.isLicenseExpired()) {
                showStyledPrompt(activity, "授权已到期",
                        "授权已到期，密友功能已暂停。续费后即可继续使用。",
                        "去续费", openShop, "稍后");
            } else {
                // 未授权（未激活）：直接弹授权码输入框，省掉多余的「请先完成授权」引导卡（用户口径 2026-06-29）。
                showActivationDialog(activity, null, "auth-guide");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[authGuide] fail: " + t);
        }
    }

    /**
     * #2 激活成功后自重启宿主：launcher intent + AlarmManager 拉起 + 杀进程。
     * 由用户点「立即重启」触发（前台发起，可拉起）；让 registry/隐藏链全新初始化、当场生效。
     */
    private static void restartHost(Context ctx) {
        if (ctx == null) return;
        try {
            android.content.Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (i != null) {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                int flags = android.app.PendingIntent.FLAG_CANCEL_CURRENT;
                if (android.os.Build.VERSION.SDK_INT >= 23) flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0x2026, i, flags);
                android.app.AlarmManager am =
                        (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                if (am != null) am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 300L, pi);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[restart] schedule fail: " + t);
        }
        try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) {}
        System.exit(0);
    }

    private static void showUpdateNoticeIfNeeded(final Activity activity) {
        if (activity == null || sUpdateNoticeShown) return;
        if (!EnvelopeStore.isAuthorizedNow()) return;
        final int mode = EnvelopeStore.getUpdateMode();
        if (mode < 0) return;
        final String title = nonEmpty(EnvelopeStore.getUpdateTitle(), "量子密友更新");
        final String msg = nonEmpty(EnvelopeStore.getUpdateMessage(), "发现新版本，请联系客服获取更新。");
        final String url = EnvelopeStore.getUpdateUrl();
        final boolean hasUrl = url != null && !url.isEmpty();
        sUpdateNoticeShown = true;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(activity, 20));
        box.setBackground(bg);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#1C1C1E"));
        tvTitle.setTextSize(19f);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setGravity(Gravity.CENTER);
        box.addView(tvTitle);

        TextView tvMsg = new TextView(activity);
        tvMsg.setText(msg);
        tvMsg.setTextColor(Color.parseColor("#FA5151"));
        tvMsg.setTextSize(14f);
        tvMsg.setGravity(Gravity.START);
        tvMsg.setLineSpacing(dp(activity, 3), 1.0f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(activity, 12);
        box.addView(tvMsg, msgLp);

        if (hasUrl) {
            TextView linkHint = new TextView(activity);
            linkHint.setText("点击底部按钮，用浏览器打开");
            linkHint.setTextColor(Color.parseColor("#D97706"));
            linkHint.setTextSize(13f);
            linkHint.getPaint().setFakeBoldText(true);
            linkHint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintLp.topMargin = dp(activity, 10);
            box.addView(linkHint, hintLp);
        }

        View divider = new View(activity);
        divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(activity, 14);
        box.addView(divider, divLp);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dp(activity, 6);
        box.addView(buttons, buttonsLp);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();

        if (hasUrl) {
            Button later = new Button(activity);
            later.setText("稍后");
            later.setTextColor(Color.parseColor("#9A9A9A"));
            later.setTextSize(14f);
            later.setAllCaps(false);
            later.setBackgroundColor(Color.TRANSPARENT);
            later.setMinWidth(0); later.setMinimumWidth(0);
            later.setMinHeight(0); later.setMinimumHeight(0);
            later.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
            later.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dialog.dismiss(); }
            });
            buttons.addView(later, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button primary = new Button(activity);
        primary.setText(hasUrl ? (mode == 1 ? "下载更新" : "联系客服") : "我知道了");
        primary.setTextColor(Color.parseColor("#576B95"));
        primary.setTextSize(14f);
        primary.getPaint().setFakeBoldText(true);
        primary.setAllCaps(false);
        primary.setBackgroundColor(Color.TRANSPARENT);
        primary.setMinWidth(0); primary.setMinimumWidth(0);
        primary.setMinHeight(0); primary.setMinimumHeight(0);
        primary.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (hasUrl) primaryLp.leftMargin = dp(activity, 4);
        buttons.addView(primary, primaryLp);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hasUrl) {
                    try {
                        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(it);
                        dialog.dismiss();
                    } catch (Throwable t) {
                        Toast.makeText(activity, "无法打开下载链接", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    dialog.dismiss();
                }
            }
        });

        dialog.show();
        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        } catch (Throwable ignored) {}
    }

    private static String nonEmpty(String v, String fallback) {
        return v == null || v.isEmpty() ? fallback : v;
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

    /** 使用教程行：暗绿色标题字与普通行区分，点开走公告卡。 */
    private static View buildTipRow(Context ctx, String title, View.OnClickListener listener) {
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
        tvTitle.setTextColor(Color.parseColor("#2E7D32")); // 暗绿，与普通行区分
        tvTitle.setTextSize(17f);
        row.addView(tvTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvBtn = new TextView(ctx);
        tvBtn.setText("\u67e5\u770b \u203a"); // 查看 ›
        tvBtn.setTextColor(Color.parseColor("#2E7D32"));
        tvBtn.setTextSize(14f);
        row.addView(tvBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 1));
        row.setLayoutParams(lp);
        return row;
    }

    /** 使用教程公告卡：沿用 showStyledPrompt 弹窗风格；服务器带了链接才出「前往」按钮。 */
    private static void showTipAnnouncement(final Activity activity, String title,
                                            String body, String url) {
        final String u = url == null ? "" : url.trim();
        Runnable go = null;
        if (!u.isEmpty()) {
            go = new Runnable() {
                @Override public void run() {
                    try {
                        activity.startActivity(new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) {}
                }
            };
        }
        showStyledPrompt(activity,
                (title == null || title.isEmpty()) ? "使用教程" : title,
                (body == null || body.isEmpty()) ? "" : body,
                go != null ? "前往" : null, go, "知道了");
    }

    /** 已授权态：把行尾值 TextView 渲染成微信绿「已授权」圆角色块（去掉 ›，白字绿底）。 */
    private static void styleAuthorizedPill(Context ctx, TextView tv) {
        if (tv == null) return;
        tv.setText("\u5df2\u6388\u6743"); // 已授权
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13f);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#07A85C")); // 微信绿（与确认按钮一致）
        pill.setCornerRadius(dp(ctx, 12));
        tv.setBackground(pill);
        tv.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));
    }

    private static String currentPasswordLabel(StateMachine sm) {
        String pwd = sm.getPassword();
        if (pwd == null || pwd.isEmpty()) return StateMachine.getDefaultPassword();
        return pwd;
    }

    private static void showPasswordSettingDialog(final Activity activity, final TextView valueView) {
        final LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        box.setPadding(pad, dp(activity, 10), pad, 0);

        TextView warning = new TextView(activity);
        warning.setText("重要提示：进入隐藏后，功能入口会一并隐藏。"
                + "为保护隐私，入口密码无法找回；一旦忘记，只能卸载重装恢复默认。请务必牢记。");
        warning.setTextColor(Color.parseColor("#D93025"));
        warning.setTextSize(16f);
        warning.getPaint().setFakeBoldText(true);
        warning.setLineSpacing(dp(activity, 2), 1.0f);
        box.addView(warning, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine(true);
        input.setText(currentPasswordLabel(StateMachine.getInstance()));
        input.setSelectAllOnFocus(true);
        input.setHint("请输入新密码，4-32 位");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(activity, 14);
        box.addView(input, ilp);

        final TextView error = new TextView(activity);
        error.setTextColor(Color.parseColor("#D93025"));
        error.setTextSize(12f);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorLp.topMargin = dp(activity, 8);
        box.addView(error, errorLp);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("密码设置")
                .setView(box)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (save == null) return;
                save.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String pwd = input.getText() != null
                                ? input.getText().toString().trim() : "";
                        if (pwd.length() < 4 || pwd.length() > 32) {
                            Toast.makeText(activity, "密码长度需为 4-32 位", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        StateMachine.getInstance().setPassword(pwd);
                        if (valueView != null) {
                            valueView.setText(pwd + " \u203a");
                        }
                        Log.i(TAG, "[SET:overlay] password updated len=" + pwd.length());
                        Toast.makeText(activity, "密码已保存，请务必牢记", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    /** 自定义余额金额标签：空 = 未填（不生效）提示，否则显示已填金额。 */
    private static String fakeBalanceLabel(Bridge br) {
        String v = br.getFakeBalanceYuan();
        return (v == null || v.isEmpty()) ? "未填（不生效）" : v;
    }

    /**
     * 自定义余额金额输入弹窗（卡片风格，与项目授权弹窗统一外观）。
     * 交互：输入框打「原始数字」（光标流畅、不改写），上方预览区实时渲染成「元.角分」效果；
     * 保存「元」字符串到 Bridge.setFakeBalanceYuan；留空 → 清空（FakeBalance 不生效，显示真实余额）。
     */
    private static void showBalanceInputDialog(final Activity activity, final TextView valueView) {
        final Bridge br = Bridge.getInstance();

        final LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        box.setPadding(pad, dp(activity, 16), pad, dp(activity, 14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(226, 255, 255, 255));
        cardBg.setCornerRadius(dp(activity, 22));
        box.setBackground(cardBg);

        TextView title = new TextView(activity);
        title.setText("自定义余额金额");
        title.setTextColor(Color.parseColor("#1C1C1E"));
        title.setTextSize(18f);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText("末尾两个数字，永远被渲染为小数");
        hint.setTextColor(Color.parseColor("#666666"));
        hint.setTextSize(13f);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(activity, 2), 1.0f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(activity, 8);
        box.addView(hint, hintLp);

        // 预览区：把输入的原始数字实时渲染成「元.角分」显示效果（所见即所得）。
        final TextView preview = new TextView(activity);
        preview.setTextColor(Color.parseColor("#07A85C"));
        preview.setTextSize(20f);
        preview.getPaint().setFakeBoldText(true);
        preview.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = dp(activity, 12);
        box.addView(preview, previewLp);

        // 输入框：纯数字「原始数据」，不实时改写（光标流畅）。
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("输入数字，如 8888888");
        input.setTextSize(16f);
        input.setGravity(Gravity.CENTER);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#F2F2F7"));
        inputBg.setCornerRadius(dp(activity, 12));
        inputBg.setStroke(1, Color.parseColor("#E1E1E6"));
        input.setBackground(inputBg);
        input.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        ilp.topMargin = dp(activity, 14);
        box.addView(input, ilp);

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String raw = input.getText() != null ? input.getText().toString() : "";
                String yuan = formatCentsToYuan(raw);
                if (yuan.isEmpty()) {
                    preview.setText("请输入数字");
                    return;
                }
                String prefix = "显示效果：";
                SpannableString sp = new SpannableString(prefix + "\u00a5" + yuan);
                sp.setSpan(new RelativeSizeSpan(0.6f), 0, prefix.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // 「显示效果：」小字，¥金额保持大字
                preview.setText(sp);
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });
        // 预填：已存 "88888.88" → 还原成原始数字串 "8888888" 给输入框。
        String saved = br.getFakeBalanceYuan();
        if (saved != null && !saved.isEmpty()) {
            String digits = saved.replaceAll("[^0-9]", "");
            input.setText(digits);
            input.setSelection(digits.length());
        }
        refresh.run();

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(activity, 16);

        Button cancel = new Button(activity);
        cancel.setText("取消");
        cancel.setTextColor(Color.parseColor("#1C1C1E"));
        cancel.setTextSize(15f);
        cancel.setAllCaps(false);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.parseColor("#F2F2F7"));
        cancelBg.setCornerRadius(dp(activity, 19));
        cancel.setBackground(cancelBg);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                dp(activity, 120), dp(activity, 38));
        cancelLp.rightMargin = dp(activity, 10);
        actions.addView(cancel, cancelLp);

        Button save = new Button(activity);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setTextSize(15f);
        save.setAllCaps(false);
        save.getPaint().setFakeBoldText(true);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(Color.parseColor("#07A85C"));
        saveBg.setCornerRadius(dp(activity, 19));
        save.setBackground(saveBg);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                dp(activity, 120), dp(activity, 38));
        actions.addView(save, saveLp);
        box.addView(actions, actionsLp);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        dialog.setCancelable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String raw = input.getText() != null ? input.getText().toString() : "";
                String yuan = formatCentsToYuan(raw); // "" = 留空 → 不生效
                br.setFakeBalanceYuan(yuan);
                if (valueView != null) valueView.setText(fakeBalanceLabel(br));
                Log.i(TAG, "[SET:overlay] fakeBalance set=\"" + yuan + "\"");
                Toast.makeText(activity, yuan.isEmpty() ? "已清空（显示真实余额）" : "金额已保存",
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.22f);
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * 纯数字串按「分」格式化成「元.角分」：取数字、限长防溢出、末两位为小数（parseLong 自动忽略前导零）。
     * 例：8888888 → 88888.88；100 → 1.00；0 → 0.00；无数字 → ""。
     */
    private static String formatCentsToYuan(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        if (digits.length() > 15) digits = digits.substring(0, 15);
        long cents;
        try { cents = Long.parseLong(digits); } catch (Throwable t) { return ""; }
        long yuan = cents / 100L;
        long fen = cents % 100L;
        return yuan + "." + (fen < 10 ? "0" + fen : Long.toString(fen));
    }

    private static String activationStatusText(Context ctx) {
        try {
            if (ctx != null) EnvelopeStore.init(ctx.getApplicationContext());
            String prefix = "\u91cf\u5b50\u5bc6\u53cb "
                    + EnvelopeStore.getProductVersion(AppConfig.GUARD_PRODUCT_VERSION)
                    + " \u00b7 "; // 量子密友 vX ·
            if (EnvelopeStore.isAuthorizedNow()) return prefix + "\u5df2\u6fc0\u6d3b"; // 已激活
            return prefix + "\u672a\u6388\u6743"; // 未授权
        } catch (Throwable ignored) {
            return "\u672a\u6388\u6743 / \u8f93\u5165\u6388\u6743\u7801";
        }
    }

    private static String activationExpireText(Context ctx) {
        // 未授权(封停删卡/到期/未激活/未同步/异常)一律回 Unix 纪元(1970-01-01)——看着像未初始化
        // 默认值，不暴露真到期、不暴露残留缓存、不给破解者"未授权"触发点(掩人耳目)。仅授权态显真到期。
        long exp = 0L;
        try {
            if (ctx != null) EnvelopeStore.init(ctx.getApplicationContext());
            if (EnvelopeStore.isAuthorizedNow()) {
                exp = EnvelopeStore.getLicenseExpireSec();
                if (exp <= 0) exp = EnvelopeStore.getLeaseExpireSec();
                long now = System.currentTimeMillis() / 1000L;
                if (exp <= now) exp = 0L;
            }
        } catch (Throwable ignored) {
            exp = 0L;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date(exp * 1000L));
    }

    private static void showActivationDialog(final Activity activity, final TextView statusView,
                                             final String source) {
        if (activity == null || sAuthDialogShowing) return;
        sAuthDialogShowing = true;
        final boolean requireActivation = !isActivationReady(activity);

        final LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        box.setPadding(pad, dp(activity, 14), pad, dp(activity, 12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(226, 255, 255, 255)); // 半透明白，仍保持可读性
        cardBg.setCornerRadius(dp(activity, 22));
        box.setBackground(cardBg);

        FrameLayout topBar = new FrameLayout(activity);
        box.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 26)));

        final TextView authBack = new TextView(activity);
        authBack.setText("\u2190");
        authBack.setTextColor(Color.parseColor("#1C1C1E"));
        authBack.setTextSize(21f);
        authBack.setGravity(Gravity.CENTER);
        authBack.setClickable(true);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                dp(activity, 38), dp(activity, 26));
        backLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        topBar.addView(authBack, backLp);

        TextView badge = new TextView(activity);
        badge.setText(requireActivation ? "\u672a\u6388\u6743" : "\u5df2\u6388\u6743"); // 未授权 / 已授权
        badge.setTextSize(12f);
        badge.setGravity(Gravity.CENTER);
        badge.getPaint().setFakeBoldText(true);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(activity, 10));
        if (requireActivation) {
            badge.setTextColor(Color.parseColor("#07A85C"));
            badgeBg.setColor(Color.parseColor("#E8F7EE")); // 浅绿底 + 绿字
        } else {
            badge.setTextColor(Color.WHITE);
            badgeBg.setColor(Color.parseColor("#07A85C")); // 已授权：实心绿 + 白字（与授权码行色块一致）
        }
        badge.setBackground(badgeBg);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                dp(activity, 58), dp(activity, 22));
        badgeLp.gravity = Gravity.CENTER;
        topBar.addView(badge, badgeLp);

        TextView title = new TextView(activity);
        title.setText("\u91cf\u5b50\u5bc6\u53cb\u6388\u6743"); // 量子密友授权
        title.setTextColor(Color.parseColor("#1C1C1E"));
        title.setTextSize(18f);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(activity, 10);
        box.addView(title, titleLp);

        TextView hint = new TextView(activity);
        hint.setText(requireActivation
                ? "\u8f93\u5165\u6388\u6743\u7801\u540e\uff0c\u7ed1\u5b9a\u5f53\u524d\u5fae\u4fe1\u4e0e\u8bbe\u5907\u3002"
                : ("\u5f53\u524d\u5fae\u4fe1\u4e0e\u8bbe\u5907\u5df2\u6388\u6743\u3002\u5230\u671f\uff1a"
                        + activationExpireText(activity)
                        + "\u3002\u5982\u9700\u7eed\u671f\u6216\u6362\u7801\uff0c\u53ef\u91cd\u65b0\u8f93\u5165\u6388\u6743\u7801\u3002"));
        hint.setTextColor(Color.parseColor("#666666"));
        hint.setTextSize(13f);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(activity, 2), 1.0f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(activity, 8);
        box.addView(hint, hintLp);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine(true);
        input.setHint("\u8f93\u5165\u6388\u6743\u7801");
        input.setTextSize(16f);
        input.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#F2F2F7"));
        inputBg.setCornerRadius(dp(activity, 12));
        inputBg.setStroke(1, Color.parseColor("#E1E1E6"));
        input.setBackground(inputBg);
        input.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        ilp.topMargin = dp(activity, 14);
        box.addView(input, ilp);

        final TextView error = new TextView(activity);
        error.setTextColor(Color.parseColor("#D93025"));
        error.setTextSize(12f);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorLp.topMargin = dp(activity, 8);
        box.addView(error, errorLp);
        if (requireActivation) {
            String lastErr = EnvelopeStore.getAuthError();
            if (lastErr != null && !lastErr.isEmpty()) {
                showActivationError(error, box, lastErr);
            }
        }

        // #2 激活动效：小转圈 + "正在请求，请耐心等待"，激活中显示、结果后隐藏。
        final LinearLayout progressRow = new LinearLayout(activity);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER);
        progressRow.setVisibility(View.GONE);
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(activity);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
        spLp.rightMargin = dp(activity, 8);
        progressRow.addView(spinner, spLp);
        TextView progressText = new TextView(activity);
        progressText.setText("\u6b63\u5728\u8bf7\u6c42\uff0c\u8bf7\u8010\u5fc3\u7b49\u5f85\u2026"); // 正在请求，请耐心等待…
        progressText.setTextColor(Color.parseColor("#666666"));
        progressText.setTextSize(12f);
        progressRow.addView(progressText);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progLp.topMargin = dp(activity, 10);
        box.addView(progressRow, progLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(activity, 14);

        final Button activate = new Button(activity);
        activate.setText("\u7acb\u5373\u6fc0\u6d3b"); // 立即激活
        activate.setTextColor(Color.WHITE);
        activate.setTextSize(15f);
        activate.getPaint().setFakeBoldText(true);
        GradientDrawable actBg = new GradientDrawable();
        actBg.setColor(Color.parseColor("#07A85C"));
        actBg.setCornerRadius(dp(activity, 19));
        activate.setBackground(actBg);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                dp(activity, 142), dp(activity, 38));
        actLp.gravity = Gravity.CENTER_HORIZONTAL;
        actions.addView(activate, actLp);
        box.addView(actions, actionsLp);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .create();
        // 未授权零弹（SPEC §3 #1）：激活框可取消（BACK 键退出），不再强制堵屏——
        //   用户只在主动点「授权状态」行时才弹，且能随时退出回设置页。
        //   ←（authBack）按钮仍同时收激活框 + overlay；touch-outside 保持 false（防误触误关）。
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        authBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                try {
                    dismissOverlay((ViewGroup) activity.getWindow().getDecorView());
                } catch (Throwable ignored) {}
            }
        });
        activate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String code = input.getText() != null
                        ? input.getText().toString().trim() : "";
                if (code.isEmpty()) {
                    showActivationError(error, box, "\u8bf7\u8f93\u5165\u6388\u6743\u7801");
                    return;
                }
                error.setVisibility(View.GONE);
                activate.setEnabled(false);
                activate.setText("\u6fc0\u6d3b\u4e2d..."); // 激活中...
                progressRow.setVisibility(View.VISIBLE);   // #2 转圈：正在请求，请耐心等待
                Toast.makeText(activity, "\u6b63\u5728\u6fc0\u6d3b...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override public void run() {
                        final GuardActivation.Result result = GuardActivation.activate(code);
                        final boolean bound = result.ok && AuthManager.bindAccount(activity);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                try {
                                    if (result.ok) {
                                        progressRow.setVisibility(View.GONE);   // #2 成功收转圈
                                        if (statusView != null) {
                                            statusView.setText(activationStatusText(activity) + " \u203a");
                                        }
                                        Toast.makeText(activity,
                                                bound ? "\u6fc0\u6d3b\u6210\u529f" : "\u6fc0\u6d3b\u6210\u529f\uff0c\u5f85\u83b7\u53d6\u5f53\u524d wxid",
                                                Toast.LENGTH_SHORT).show();
                                        activate.setText("\u6fc0\u6d3b\u6210\u529f"); // 激活成功
                                        Log.i(TAG, "[SET:auth] activation ok source=" + source
                                                + " state=" + NativeBridge.getAuthState());
                                        // #2 成功后引导重启：重启让 registry / 隐藏链全新初始化、当场生效（免手动冷启）。
                                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                                        showStyledPrompt(activity, "激活成功",
                                                "授权已激活。点击重启，让密友隐藏等功能立即生效。",
                                                "立即重启",
                                                new Runnable() {
                                                    @Override public void run() { restartHost(activity); }
                                                }, "稍后");
                                    } else {
                                        progressRow.setVisibility(View.GONE);   // #2 失败收转圈
                                        activate.setEnabled(true);
                                        activate.setText("\u7acb\u5373\u6fc0\u6d3b");
                                        showActivationError(error, box,
                                                result.message != null && result.message.length() > 0
                                                        ? result.message
                                                        : "\u6388\u6743\u5f02\u5e38\uff0c\u8bf7\u8054\u7cfb\u5ba2\u670d");
                                        Log.w(TAG, "[SET:auth] activation failed source=" + source
                                                + " msg=" + result.message);
                                    }
                                } catch (Throwable t) {
                                    progressRow.setVisibility(View.GONE);   // #2 异常收转圈
                                    activate.setEnabled(true);
                                    activate.setText("\u7acb\u5373\u6fc0\u6d3b");
                                    showActivationError(error, box,
                                            "\u6fc0\u6d3b\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5");
                                    Log.w(TAG, "[SET:auth] activation ui failed: " + t);
                                }
                            }
                        });
                    }
                }, "ncl-activation").start();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.22f);
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        Log.i(TAG, "[SET:auth] dialog shown source=" + source);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                sAuthDialogShowing = false;
            }
        });
    }

    private static void showActivationError(TextView error, View target, String msg) {
        if (error != null) {
            error.setText(msg);
            error.setVisibility(View.VISIBLE);
        }
        shakeView(target);
    }

    private static void shakeView(View target) {
        if (target == null) return;
        int dx = dp(target.getContext(), 8);
        android.view.animation.TranslateAnimation anim =
                new android.view.animation.TranslateAnimation(-dx, dx, 0, 0);
        anim.setDuration(55);
        anim.setRepeatCount(5);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        target.startAnimation(anim);
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
        ver.setText("Version " + EnvelopeStore.getProductVersion(AppConfig.GUARD_PRODUCT_VERSION));
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

        final Bridge.NotifyPolicy[] applied = { br.getNotifyPolicy() };
        final int[] sel = {
                applied[0] == Bridge.NotifyPolicy.SOUND
                        ? 2
                        : (applied[0] == Bridge.NotifyPolicy.VIBRATE ? 1 : 0)
        };

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
                    sel[0] = idx;
                    if (idx == 0) {
                        applied[0] = Bridge.NotifyPolicy.OFF;
                        br.setNotifyPolicy(Bridge.NotifyPolicy.OFF);
                    } else if (idx == 1) {
                        applied[0] = Bridge.NotifyPolicy.VIBRATE;
                        br.setNotifyPolicy(Bridge.NotifyPolicy.VIBRATE);
                        // 选「震动」即时给一次震动反馈。
                        try {
                            com.ghost.assist.moduleC.NotifyRouter.fireAlert(
                                    ctx.getApplicationContext(),
                                    com.ghost.assist.moduleC.NotifyRouter.EventType.MSG);
                        } catch (Throwable ignored) {}
                    } else {
                        applied[0] = Bridge.NotifyPolicy.SOUND;
                        br.setNotifyPolicy(Bridge.NotifyPolicy.SOUND);
                        // 选「铃声」即时播放默认通知音，便于用户确认档位生效。
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
    // 备用入口用的视图树查找辅助
    // -----------------------------------------------------------------------

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
            if (text != null && text.length() > 0 && !text.toString().contains("\u5fae\u4fe1\u53f7")) {
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
