package com.ghost.assist.moduleB;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

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

    // B2: Xposed adapter hooks state
    private static volatile boolean               sAdapterHooked   = false;
    private static volatile WeakReference<Object> sKnownAdapterRef = null;
    private static volatile boolean               sHeaderVisible   = false;

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
                            if (!MAIN_SETTINGS_CLASS.equals(activity.getClass().getName())) return;
                            try {
                                syncEntry(activity);
                            } catch (Throwable t) {
                                Log.w(TAG, "[SET] syncEntry error: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[SET] entry installed class=" + MAIN_SETTINGS_CLASS);
        } catch (Throwable t) {
            Log.w(TAG, "[SET] install failed: " + t);
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

        boolean shouldShow = StateMachine.getInstance().getState() == StateMachine.State.VISIBLE;

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
    // Path B dispatch
    // -----------------------------------------------------------------------

    private static void handleRecyclerView(View rv, Activity activity, boolean shouldShow) {
        // B2 hooks active: just sync visibility + notify
        if (sAdapterHooked) {
            if (sHeaderVisible != shouldShow) {
                sHeaderVisible = shouldShow;
                refreshAdapterNotify();
            }
            return;
        }

        // B1: fixed header until B2 is ready
        ViewGroup ll = findRvContainerLinearLayout(rv);
        if (ll != null) {
            syncLlHeader(ll, activity, shouldShow);
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

    private static void installAdapterHooks(final Class<?> adapterCls, boolean initialShow) {
        try {
            // 1. getItemCount
            XposedBridge.hookAllMethods(adapterCls, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!sHeaderVisible) return;
                    Object r = param.getResult();
                    if (r instanceof Integer) param.setResult((Integer) r + 1);
                }
            });

            // 2. getItemViewType — shift positions for items after the header
            XposedBridge.hookAllMethods(adapterCls, "getItemViewType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!sHeaderVisible
                            || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) return;
                    int pos = (int) param.args[0];
                    if (pos > 0) param.args[0] = pos - 1;
                    // pos 0: no-op → original returns type of item[0]; WeChat creates matching ViewHolder
                }
            });

            // 3. onBindViewHolder — pos 0: override; pos 1..N: shift
            XposedBridge.hookAllMethods(adapterCls, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!sHeaderVisible
                            || param.args.length < 2
                            || !(param.args[1] instanceof Integer)) return;
                    int pos = (int) param.args[1];
                    if (pos == 0) {
                        Object holder = param.args[0];
                        try {
                            View itemView = (View) holder.getClass()
                                    .getField("itemView").get(holder);
                            customizeGuardHeader(itemView);
                        } catch (Throwable t) {
                            Log.w(TAG, "[SET:hook] customize failed: " + t);
                        }
                        param.setResult(null); // skip original binding for position 0
                    } else {
                        param.args[1] = pos - 1;
                    }
                }
            });

            // 4. getItemId — give header a unique stable id (-1)
            XposedBridge.hookAllMethods(adapterCls, "getItemId", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!sHeaderVisible
                            || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) return;
                    int pos = (int) param.args[0];
                    if (pos == 0) {
                        param.setResult(-1L);
                    } else {
                        param.args[0] = pos - 1;
                    }
                }
            });

            sAdapterHooked = true;
            sHeaderVisible = initialShow;
            Log.i(TAG, "[SET:hook] adapter hooks installed on " + adapterCls.getName()
                    + " visible=" + initialShow);

            // Remove B1 fixed header (B2 takes over)
            removeLlHeader();
            // Trigger redraw with new item count
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
                showGuardDialog(v.getContext());
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
            if (knownLl == ll && knownRow != null && knownRow.getParent() == ll) return;
            if (knownRow != null && knownRow.getParent() != null) {
                try { ((ViewGroup) knownRow.getParent()).removeView(knownRow); }
                catch (Throwable ignored) {}
            }
            View row = buildGuardRow(activity);
            int idx = Math.min(1, ll.getChildCount());
            ll.addView(row, idx);
            sLlRef        = new WeakReference<>(ll);
            sHeaderRowRef = new WeakReference<>(row);
            Log.i(TAG, "[SET] B1 entry row added idx=" + idx
                    + " llChildCount=" + ll.getChildCount());
        } else {
            if (knownRow != null && knownRow.getParent() != null) {
                try {
                    ((ViewGroup) knownRow.getParent()).removeView(knownRow);
                    Log.i(TAG, "[SET] B1 entry row removed (HIDDEN)");
                } catch (Throwable ignored) {}
                sHeaderRowRef = null;
                sLlRef = null;
            }
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

    private static View buildGuardRow(Context context) {
        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setBackgroundColor(Color.WHITE);

        TextView tv = new TextView(context);
        tv.setText("\u5bc6\u53cb\u8bbe\u7f6e \u203a"); // 密友设置 ›
        tv.setTextColor(Color.parseColor("#191919"));
        tv.setTextSize(16f);
        int ph = dp(context, 16);
        int pv = dp(context, 14);
        tv.setPadding(ph, pv, ph, pv);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "[SET] entry clicked");
                showGuardDialog(v.getContext());
            }
        });
        wrapper.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#e5e5e5"));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        dlp.gravity = android.view.Gravity.BOTTOM;
        wrapper.addView(divider, dlp);

        return wrapper;
    }

    // -----------------------------------------------------------------------
    // AlertDialog (v1 click behavior)
    // -----------------------------------------------------------------------

    private static void showGuardDialog(final Context context) {
        try {
            final StateMachine sm     = StateMachine.getInstance();
            final Bridge       br     = Bridge.getInstance();
            final boolean      isH    = sm.getState() == StateMachine.State.HIDDEN;
            final boolean      featOn = br.isFeatureEnabled();
            final String       policy = br.getNotifyPolicy().name();
            final int          count  = br.getCount();

            String msg = "\u5f53\u524d\u72b6\u6001\uff1a" + sm.getStateName() + "\n"
                    + "\u5bc6\u53cb\u6570\u91cf\uff1a" + count + " \u4e2a\n"
                    + "\u5bc6\u53cb\u529f\u80fd\uff1a" + (featOn ? "\u5df2\u5f00\u542f" : "\u5df2\u5173\u95ed") + "\n"
                    + "\u901a\u77e5\u7b56\u7565\uff1a" + policy + "  (Phase 2 \u63a5\u5165)";

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("\u5bc6\u53cb\u8bbe\u7f6e");
            builder.setMessage(msg);

            if (isH) {
                builder.setNegativeButton("\u5207\u6362\u663e\u5f62",
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                sm.exitHidden(false);
                                sHeaderVisible = true;
                                if (sAdapterHooked) refreshAdapterNotify();
                                Log.i(TAG, "[SET] dialog -> exitHidden");
                            }
                        });
            } else {
                builder.setNegativeButton("\u5207\u6362\u9690\u85cf",
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                sm.enterHidden();
                                sHeaderVisible = false;
                                if (sAdapterHooked) refreshAdapterNotify();
                                Log.i(TAG, "[SET] dialog -> enterHidden");
                            }
                        });
            }

            builder.setNeutralButton(
                    "\u5bc6\u53cb\u529f\u80fd: " + (featOn ? "ON\u2192\u5173" : "OFF\u2192\u5f00"),
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            br.setFeatureEnabled(!featOn);
                            Log.i(TAG, "[SET] dialog -> feature=" + !featOn);
                        }
                    });

            builder.setPositiveButton("\u5173\u95ed", null);
            builder.show();
            Log.i(TAG, "[SET] guard dialog shown state=" + sm.getStateName()
                    + " count=" + count);
        } catch (Throwable t) {
            Log.w(TAG, "[SET] dialog show failed: " + t);
        }
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
