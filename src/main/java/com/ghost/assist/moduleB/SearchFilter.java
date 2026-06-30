package com.ghost.assist.moduleB;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.GuardRuntime;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.RegistryFallback;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.UiContextTracker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Global search result filter — WeChat 8.0.71.
 *
 * Two-tier strategy:
 *
 *  ⭐ Primary (View binding layer, P20 v15.2):
 *     Detect the q2/f0 adapter family, then hook f0.getView after render.
 *     Extract the rendered item's wxid/groupId and collapse hidden rows to 1px.
 *     The q2.j path remains a diagnostic/backstop, not the primary renderer.
 *
 *  Secondary (data layer, diagnostic + fallback):
 *     ArrayList.addAll first-item = fz2.e → dump fields. fz2.e.g is UIN digits
 *     (or "SOSItemRelevant:<keyword>"), NOT a wxid literal — earlier docs were wrong.
 *     LinkedList.add first-item = z15.ef6 → chat-record FTS marker class, no
 *     directly accessible wxid field. Kept only for the unlock probe (111111).
 *
 *  Unlock probe (B6 entry):
 *     tryUnlockFromSearchResults() inspects z15.ef6 / fz2.e text fields for the
 *     stored password; on match it triggers StateMachine UNLOCKING → VISIBLE and
 *     finishes the FTSMainUI activity (UiContextTracker).
 */
public class SearchFilter {

    private static final String TAG = "NCL";

    // diagnostic (2026-06-02): one-shot per-row dump to locate the gray blank card view
    private static int sRowDumpCount = 0;

    // Search result item classes (8.0.71, confirmed by dynamic_crawler 2026-05-23):
    //   z15.ef6           — chat-record FTS hits, NO wxid (kept for unlock probe)
    //   fz2.e  c≠3        — contact match, g = SOSItemRelevant carrying wxid ← PRIMARY filter
    //   fz2.e  c=3        — inline chat-record, g = UIN (needs UIN→wxid map, TODO)
    private static final String FTS_RESULT_ITEM     = "z15.ef6";
    private static final String CONTACT_RESULT_ITEM = "fz2.e";

    private static volatile long sLastUnlockAt = 0L;

    // One-shot field dump probes — separate counters per class.
    private static volatile int sEf6DumpCount = 0;
    private static volatile int sFz2DumpCount = 0;
    private static final int EF6_DUMP_LIMIT = 3;
    private static final int FZ2_DUMP_LIMIT = 5;

    // WeChat classloader — saved at install() for reflective class lookups
    private static ClassLoader sCl;

    // P_SEC1: search.gateway is a coarse registry profile. It records the
    // adapter family and render hook only; it does NOT decide hide/show and it
    // explicitly excludes SearchUnlock / 111111 entry logic.
    // C5a 归一: the DEBUG fallback below is generated from registry_8071.json
    // (RegistryFallback; debug = literal, release = ""), not hand-written.
    private static final String REGISTRY_ENTRY = "search.gateway";
    private static String sGateway = RegistryFallback.SEARCH_GATEWAY__GATEWAY;
    private static String sAdapterFamily = RegistryFallback.SEARCH_GATEWAY__ADAPTER_FAMILY;
    private static String sRenderHook = RegistryFallback.SEARCH_GATEWAY__RENDER_HOOK;
    private static String sExtractorProfile = RegistryFallback.SEARCH_GATEWAY__EXTRACTOR_PROFILE;
    private static String sScope = RegistryFallback.SEARCH_GATEWAY__SCOPE;
    private static volatile boolean sRecipesResolved = false;

    private static String recipe(String key, String fallback) {
        return GuardRuntime.getRecipeOrFallback(REGISTRY_ENTRY, key, fallback);
    }

    private static boolean resolveRecipes() {
        if (sRecipesResolved) return true;
        sGateway = recipe("gateway", sGateway);
        sAdapterFamily = recipe("adapter_family", sAdapterFamily);
        sRenderHook = recipe("render_hook", sRenderHook);
        sExtractorProfile = recipe("extractor_profile", sExtractorProfile);
        sScope = recipe("scope", sScope);
        sRecipesResolved = true;
        boolean ready = !sGateway.isEmpty()
                && !sAdapterFamily.isEmpty()
                && !sRenderHook.isEmpty()
                && !sExtractorProfile.isEmpty()
                && !sScope.isEmpty();
        boolean fbOk = AppConfig.isDevBuild() && "getView".equals(recipe("__no_such_key__", "getView"));
        Log.i(TAG, "[SF] recipes gateway=" + sGateway
                + " adapterFamily=" + sAdapterFamily
                + " renderHook=" + sRenderHook
                + " profile=" + sExtractorProfile
                + " scope=" + sScope
                + " fallbackSelfTest=" + (fbOk ? "ok" : "FAIL")
                + " ready=" + ready);
        return ready;
    }

    private static boolean adapterFamilyContains(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return false;
        String[] parts = sAdapterFamily.split(",");
        for (String p : parts) {
            if (simpleName.equals(p.trim())) return true;
        }
        return false;
    }

    // FTS adapter probe — dynamically detect the q2/f0 family and hook render method.
    private static volatile boolean sQ2Hooked = false;
    private static volatile int sQ2DumpCount = 0;
    private static final int Q2_DUMP_LIMIT = 8;

    // Most-recently-seen q2 adapter — used to push notifyDataSetChanged() from
    // StateMachine / RefreshBus listeners so the FTS list re-renders the instant
    // V↔H flips or the hidden set mutates. WeakRef so FTSMainUI can finish() and
    // be garbage-collected normally; a stale ref simply returns null and the
    // next setAdapter call repopulates it.
    private static java.lang.ref.WeakReference<Object> sCurrentQ2AdapterRef =
            new java.lang.ref.WeakReference<>(null);

    // Provider-layer probe — dump iz2.i fields inside yz2.h.a.n List, etc.
    // Each q2.a(Provider, query, boolean) dispatch hits 15 providers per search;
    // tight dump cap to avoid log flood. One log line per (provider class × first-item class).
    private static volatile int sProviderDumpCount = 0;
    private static final int PROVIDER_DUMP_LIMIT = 16;
    private static final java.util.Set<String> sProviderSeen =
            java.util.Collections.synchronizedSet(new HashSet<String>());

    // FTSMainUI view tree dump — capture multiple times (onResume + after first
    // fz2.e arrives = search results rendered). 3 dumps total to catch the real
    // RecyclerView adapter that swaps in mid-search.
    private static volatile int sFtsTreeDumpCount = 0;
    private static final int FTS_TREE_DUMP_LIMIT = 3;
    private static volatile boolean sFtsTreeReDumpScheduled = false;

    // ── B' 方案 (2026-05-27) ──
    // f0/q2 search adapter position-offset state. Eliminates the empty row gap
    // left by setVisibility(GONE)+lp.height=0 by making the adapter pretend the
    // hidden friend rows do not exist at all (getCount shrinks, getView/getItem
    // /getItemId/getItemViewType arguments are translated displayPos→origPos).
    //   sSkipMap         : sorted ascending array of *original* positions to skip.
    //   sRescanDepth     : ThreadLocal counter, >0 while we are inside getCount's
    //                      own rebuild loop calling getItem(i) — disables the
    //                      position translation hook to avoid double-translation.
    private static volatile int[] sSkipMap = new int[0];
    private static final ThreadLocal<Integer> sRescanDepth =
            new ThreadLocal<Integer>() {
                @Override protected Integer initialValue() { return 0; }
            };
    // True while a position-translating hook frame is on this thread's stack.
    // Inner adapter callbacks (getView calling getItem, etc.) must NOT translate
    // again: the outer frame has already converted displayPos → origPos.
    private static final ThreadLocal<Integer> sInTranslate =
            new ThreadLocal<Integer>() {
                @Override protected Integer initialValue() { return 0; }
            };
    private static volatile long sLastSkipLogAt = 0L;
    // Throttle for rebuildSkipMap — ListView's layout pass calls getCount
    // 10-20× per frame; without throttling the reflective getItem invocations
    // saturate the UI thread and the search Activity ANRs (final_v11 evidence).
    private static volatile long sLastRescanAt = 0L;
    private static final long RESCAN_THROTTLE_MS = 80L;
    // Per-thread cap for the f0.getItem upstream probe log (final_v11 ANR fix).
    private static volatile int sUpGetItemLogCount = 0;
    private static final int UP_GETITEM_LOG_LIMIT = 12;

    // Wider discovery probe — log every distinct first-item class on
    // ArrayList.addAll (deduped + capped). Helps locate fz2.e / kc5.y if they
    // travel a different container than LinkedList.add.
    private static volatile boolean sAddAllDumpActive = true;
    private static volatile int sAddAllSeenCount = 0;
    private static final int ADDALL_SEEN_LIMIT = 60;
    private static final java.util.Set<String> sAddAllSeenClasses =
            java.util.Collections.synchronizedSet(new HashSet<String>());

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!resolveRecipes()) {
            Log.w(TAG, "[SF] skip install: registry not ready");
            return;
        }
        sCl = lpparam.classLoader;

        // ── DISABLED 2026-05-27 v13: fts-tree view-tree dump (UI ANR root cause) ──
        // walkFtsTree() reflects through ~147 view nodes per dump + sync Log.i for
        // each. 3 dumps per search session = ~450ms UI blocking, perceived as
        // freeze. ListView-vs-RecyclerView question already settled by v11/v12,
        // probe no longer needed. Wrapped in if (false) to preserve code shape.
        if (false) try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Activity act = (Activity) p.thisObject;
                                String acn = act.getClass().getName();
                                if (!acn.contains("FTSMainUI") && !acn.contains("Fts")
                                        && !acn.contains("Search")) return;
                                if (sFtsTreeDumpCount >= FTS_TREE_DUMP_LIMIT) return;
                                sFtsTreeDumpCount++;
                                Log.i(TAG, "[SF:fts-tree] === " + acn + " view tree #"
                                        + sFtsTreeDumpCount + " (onResume) START ===");
                                android.view.View root = act.getWindow().getDecorView();
                                if (root instanceof ViewGroup) walkFtsTree((ViewGroup) root, 0);
                                Log.i(TAG, "[SF:fts-tree] === END #" + sFtsTreeDumpCount + " ===");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:fts-tree] dump fail: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[SF:fts-tree] FTSMainUI tree probe installed");
        } catch (Throwable t) {
            Log.w(TAG, "[SF:fts-tree] probe install fail: " + t);
        }

        // Static f0.getView hook removed (2026-05-27 fix):
        //   In install() main entry the FTS classes (q2 / f0) are not yet loaded
        //   by WeChat's classloader, so findClass / findAndHookMethod silently
        //   threw ClassNotFoundError → catch (Throwable) → hook never installed.
        //   getView is now hooked dynamically inside the ListView.setAdapter
        //   callback below, climbing the adapter's class hierarchy until it
        //   finds a class that *declares* getView (q2 inherits it from f0).
        //   See final_v7.log L1083-1132 for the declared-method dump that
        //   proves q2 has no declared getView and f0 declares it.

        try {
            XposedBridge.hookMethod(
                    ArrayList.class.getMethod("addAll", Collection.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"rawtypes"})
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Collection coll = (Collection) param.args[0];
                            if (coll == null || coll.isEmpty()) return;
                            Object first = coll.iterator().next();
                            if (first == null) return;

                            // Wider discovery probe — record every distinct
                            // first-item class on addAll (deduped + capped).
                            String firstCn = first.getClass().getName();
                            if (sAddAllDumpActive && sAddAllSeenCount < ADDALL_SEEN_LIMIT
                                    && sAddAllSeenClasses.add(firstCn)) {
                                sAddAllSeenCount++;
                                Log.i(TAG, "[SF:ALLseen] #" + sAddAllSeenCount
                                        + " addAll first=" + firstCn
                                        + " size=" + coll.size());
                                if (sAddAllSeenCount >= ADDALL_SEEN_LIMIT) sAddAllDumpActive = false;
                            }

                            // ─── fz2.e: dump fields + extract wxid (diagnostic only, no filter yet) ───
                            if (CONTACT_RESULT_ITEM.equals(firstCn)) {
                                for (Object o : coll) {
                                    if (o != null) dumpFz2eFields(o);
                                }
                                // Log wxid for first item
                                String wxid = extractFz2Wxid(first);
                                Log.i(TAG, "[SF:ALLseen] fz2.e extractWxid=" + (wxid != null ? wxid : "null")
                                        + " c=" + readFz2C(first));

                                // Trigger a view tree re-dump 300ms later: now the real search-result
                                // RecyclerView adapter has swapped in (it's the one we need to hook).
                                // DISABLED 2026-05-27 v13: same ANR reason as the onResume probe above.
                                if (false && sFtsTreeDumpCount < FTS_TREE_DUMP_LIMIT && !sFtsTreeReDumpScheduled) {
                                    sFtsTreeReDumpScheduled = true;
                                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override public void run() {
                                            try {
                                                Activity act = UiContextTracker.getCurrentActivity();
                                                sFtsTreeReDumpScheduled = false;
                                                if (act == null) return;
                                                String acn = act.getClass().getName();
                                                if (!acn.contains("FTSMainUI") && !acn.contains("Fts")
                                                        && !acn.contains("Search")) return;
                                                if (sFtsTreeDumpCount >= FTS_TREE_DUMP_LIMIT) return;
                                                sFtsTreeDumpCount++;
                                                Log.i(TAG, "[SF:fts-tree] === " + acn + " view tree #"
                                                        + sFtsTreeDumpCount + " (post-fz2.e) START ===");
                                                View root = act.getWindow().getDecorView();
                                                if (root instanceof ViewGroup) walkFtsTree((ViewGroup) root, 0);
                                                Log.i(TAG, "[SF:fts-tree] === END #" + sFtsTreeDumpCount + " ===");
                                            } catch (Throwable t) {
                                                Log.w(TAG, "[SF:fts-tree] re-dump fail: " + t);
                                            }
                                        }
                                    }, 300);
                                }

                                return; // don't fall through to z15.ef6 filter
                            }

                            if (!FTS_RESULT_ITEM.equals(firstCn)) return;

                            if (tryUnlockFromSearchResults(coll)) return;

                            if (!StateMachine.getInstance().isActive()) return;
                            Set<String> hidden = Bridge.getInstance().allHiddenIds();
                            if (hidden.isEmpty()) return;

                            int before = coll.size();
                            int removed = 0;
                            Iterator it = coll.iterator();
                            while (it.hasNext()) {
                                Object item = it.next();
                                if (containsHiddenWxid(item, hidden, 0, new HashSet<Integer>())) {
                                    try {
                                        it.remove();
                                        removed++;
                                    } catch (UnsupportedOperationException ignored) {
                                        // Some lists are immutable; keep hook non-fatal.
                                    }
                                }
                            }

                            Log.i(TAG, "[SF:addAll] z15.ef6 seen=" + before + " removed=" + removed);
                            if (removed > 0) {
                                Bridge.getInstance().addRawFeedLine(
                                        "[SF:addAll] z15.ef6 removed=" + removed + "/" + before);
                            }
                        }
                    });
            Log.i(TAG, "[SF] SearchFilter installed");
        } catch (Throwable t) {
            Log.w(TAG, "[SF] install fail: " + t);
        }

        // 8.0.71 search results stream through LinkedList.add(z15.ef6), NOT
        // ArrayList.addAll. So we attach BOTH the B6 unlock probe AND the
        // hidden-wxid filter to this single data path.
        try {
            XposedBridge.hookMethod(
                    LinkedList.class.getMethod("add", Object.class),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object item = param.args[0];
                            if (item == null) return;
                            String cn = item.getClass().getName();

                            // ─── fz2.e: contact / inline-chat search result ───
                            if (CONTACT_RESULT_ITEM.equals(cn)) {
                                dumpFz2eFields(item);
                                if (!StateMachine.getInstance().isActive()) return;
                                Set<String> hidden = Bridge.getInstance().allHiddenIds();
                                if (hidden.isEmpty()) return;

                                String wxid = extractFz2Wxid(item);
                                if (wxid != null && !wxid.isEmpty() && hidden.contains(wxid)) {
                                    param.setResult(false);
                                    Log.i(TAG, "[SF:LLadd] fz2.e blocked wxid=" + wxid);
                                    Bridge.getInstance().addRawFeedLine(
                                            "[SF:LLadd] fz2.e blocked " + wxid);
                                } else if (wxid != null) {
                                    Log.i(TAG, "[SF:LLadd] fz2.e seen pass wxid=" + wxid);
                                }
                                return;
                            }

                            // ─── z15.ef6: chat-record FTS (no wxid, used for unlock probe) ───
                            if (!FTS_RESULT_ITEM.equals(cn)) return;

                            dumpEf6Fields(item);

                            java.util.List<Object> single = new ArrayList<>(1);
                            single.add(item);
                            if (tryUnlockFromSearchResults(single)) return;

                            if (!StateMachine.getInstance().isActive()) return;
                            Set<String> hidden = Bridge.getInstance().allHiddenIds();
                            if (hidden.isEmpty()) return;

                            if (containsHiddenWxid(item, hidden, 0, new HashSet<Integer>())) {
                                param.setResult(false);
                                Log.i(TAG, "[SF:LLadd] z15.ef6 blocked hidden=" + hidden.size());
                                Bridge.getInstance().addRawFeedLine("[SF:LLadd] z15.ef6 blocked");
                            } else {
                                Log.i(TAG, "[SF:LLadd] z15.ef6 seen pass");
                            }
                        }
                    });
            Log.i(TAG, "[SF] LinkedList.add hook for B6 + filter installed");
        } catch (Throwable t) {
            Log.w(TAG, "[SF] LinkedList.add hook fail: " + t);
        }

        // ─── q2 adapter probe v4: hook ListView.setAdapter → detect q2 → hook getView ───
        // v1 (RecyclerView$Adapter Class.forName) — inner class not found
        // v2 (Activity.onResume + view walk) — missed, 400ms delay not enough
        // v3 (RecyclerView.setAdapter) — didn't fire; WeChat used ListView not RecyclerView for search
        // v4: search results page uses android.widget.ListView, adapter is BaseAdapter subclass
        try {
            XposedBridge.hookAllMethods(android.widget.ListView.class, "setAdapter",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object adapter = param.args[0];
                            if (adapter == null
                                    || !adapterFamilyContains(adapter.getClass().getSimpleName())) return;

                            // Instance-level housekeeping that MUST run for every new q2 adapter,
                            // not only the first one — divider on the new ListView must be cleared,
                            // and the adapter WeakRef must be refreshed so RefreshBus / StateMachine
                            // listeners can push notifyDataSetChanged() to the currently active adapter.
                            sCurrentQ2AdapterRef = new java.lang.ref.WeakReference<>(adapter);
                            Log.i(TAG, "[SF:q2] cached adapterRef inst="
                                    + System.identityHashCode(adapter));
                            try {
                                if (param.thisObject instanceof android.widget.ListView) {
                                    android.widget.ListView lv =
                                            (android.widget.ListView) param.thisObject;
                                    lv.setDivider(null);
                                    lv.setDividerHeight(0);
                                    Log.i(TAG, "[SF:lv] q2 ListView divider cleared inst="
                                            + System.identityHashCode(lv));
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:lv] divider clear fail: " + t);
                            }

                            // Class-level hook installation only needs to run once. After that the
                            // hooks fire on every q2 instance because XposedBridge.hookAllMethods is
                            // class-level (not instance-level).
                            if (sQ2Hooked) return;
                            sQ2Hooked = true;
                            Log.i(TAG, "[SF:q2] setAdapter q2 detected adapterCls="
                                    + adapter.getClass().getName());
                            // Dump all declared methods to find the real binding method
                            StringBuilder msb = new StringBuilder("[SF:q2] methods:");
                            for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                                for (Method m : c.getDeclaredMethods()) {
                                    msb.append("\n  ").append(c.getSimpleName()).append('.')
                                            .append(m.getName()).append("(");
                                    Class<?>[] pts = m.getParameterTypes();
                                    for (int i = 0; i < pts.length; i++) {
                                        if (i > 0) msb.append(",");
                                        msb.append(pts[i].getSimpleName());
                                    }
                                    msb.append(")");
                                }
                            }
                            Log.i(TAG, msb.toString());
                            final Object adpRef = adapter;
                            // q2.j(View, jz2.g, boolean) — bind method, g is the data item.
                            // 8.0.71 schema (装机实证 2026-05-27):
                            //   g.a == 0 → tz2.g0 group header (no wxid)
                            //   g.a == 1 → tz2.u1 contact → g.f.s = wxid
                            //   g.a == 2 → tz2.s1 chatroom → g.s = groupId
                            // REVIVED 2026-05-27 方案 D: 5-hook offset broke FTS data flow; back
                            // to v10's GONE + lp.height=0 strategy. Companion changes below:
                            // ① margin归零 on the GONE row; ② setDivider(null)+setDividerHeight(0)
                            // on the ListView so the residual gap shrinks to ~zero.
                            try {
                                XposedBridge.hookAllMethods(adapter.getClass(), "j",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void beforeHookedMethod(MethodHookParam p) {
                                                if (p.args.length < 2) return;
                                                Object g = p.args[1];
                                                if (g == null) return;

                                                // Diagnostic dump for first Q2_DUMP_LIMIT calls
                                                if (sQ2DumpCount < Q2_DUMP_LIMIT) {
                                                    sQ2DumpCount++;
                                                    int pos = -1;
                                                    for (int idx = 0; idx < p.args.length; idx++) {
                                                        if (p.args[idx] instanceof Integer) { pos = (int) idx; break; }
                                                    }
                                                    dumpQ2DataItem(g, pos, adpRef);
                                                }

                                                if (!StateMachine.getInstance().isActive()) return;
                                                Set<String> hidden = Bridge.getInstance().allHiddenIds();
                                                if (hidden.isEmpty()) return;

                                                String id = extractQ2BindWxid(g);
                                                if (id == null || !hidden.contains(id)) {
                                                    // Restore View visibility in case of ViewHolder
                                                    // recycling from a previously hidden friend slot.
                                                    if (p.args[0] instanceof View) {
                                                        View v0 = (View) p.args[0];
                                                        if (v0.getVisibility() != View.VISIBLE) {
                                                            v0.setVisibility(View.VISIBLE);
                                                        }
                                                        ViewGroup.LayoutParams lp0 = v0.getLayoutParams();
                                                        if (lp0 != null && lp0.height == 0) {
                                                            lp0.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                                            v0.setLayoutParams(lp0);
                                                        }
                                                    }
                                                    return;
                                                }

                                                // Hidden friend → make the View invisible & skip bind.
                                                // 方案 D 微调: also zero out top/bottom margins so the
                                                // row's surrounding gap collapses; combined with
                                                // ListView.setDividerHeight(0) below this drives the
                                                // visible residual gap to ~0px.
                                                if (p.args[0] instanceof View) {
                                                    View v = (View) p.args[0];
                                                    v.setVisibility(View.GONE);
                                                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                                                    if (lp != null) {
                                                        lp.height = 0;
                                                        if (lp instanceof ViewGroup.MarginLayoutParams) {
                                                            ViewGroup.MarginLayoutParams mlp =
                                                                    (ViewGroup.MarginLayoutParams) lp;
                                                            mlp.topMargin = 0;
                                                            mlp.bottomMargin = 0;
                                                        }
                                                        v.setLayoutParams(lp);
                                                    }
                                                }
                                                p.setResult(null);
                                                Log.i(TAG, "[SF:q2] blocked id=" + id);
                                                Bridge.getInstance().addRawFeedLine("[SF:q2] blocked " + id);
                                            }
                                        });
                                Log.i(TAG, "[SF:q2] j(View,g,boolean) filter hook installed");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:q2] j() hook fail: " + t);
                            }

                            // ─── PRIMARY: q2.getView single-hook GONE filter (v15.1 2026-05-27) ──
                            // 真相：v10 装机时 [SF:gv] blocked ×3 = q2.getView hook 拦下，q2.j 一直
                            // 陪跑没被调用（final_v15 终端 AI 实证）。v11-v14 误优化把 getView 关了，
                            // 这里加回 v10 风格的简单 afterHook GONE 路线：
                            //   - 沿继承链找到 f0（q2 父类，declares getView）
                            //   - hookAllMethods("getView") 装 afterHook
                            //   - 不动 args[0]、不 setResult、不翻译 — 让 WeChat 自然渲染
                            //   - hidden 命中 → GONE + height=0 + margin=0
                            //   - miss → restoreView 防 ViewHolder 复用污染
                            try {
                                Class<?> gvCls = null;
                                for (Class<?> c = adapter.getClass();
                                     c != null && c != Object.class
                                             && c != android.widget.BaseAdapter.class;
                                     c = c.getSuperclass()) {
                                    boolean has = false;
                                    for (Method m : c.getDeclaredMethods()) {
                                        if (sRenderHook.equals(m.getName())) { has = true; break; }
                                    }
                                    if (has) { gvCls = c; break; }
                                }
                                if (gvCls == null) {
                                    Log.w(TAG, "[SF:gv] no declared " + sRenderHook + " in hierarchy of "
                                            + adapter.getClass().getName() + " — single-hook skipped");
                                    throw new NoSuchMethodException(sRenderHook + " not declared");
                                }
                                Log.i(TAG, "[SF:gv] hooking single-hook " + sRenderHook
                                        + " on " + gvCls.getName());
                                XposedBridge.hookAllMethods(gvCls, sRenderHook, new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        if (p.args.length < 1 || !(p.args[0] instanceof Integer)) return;
                                        Object result = p.getResult();
                                        if (!(result instanceof View)) return;
                                        View v = (View) result;
                                        int pos = (Integer) p.args[0];

                                        if (!StateMachine.getInstance().isActive()) {
                                            restoreView(v);
                                            return;
                                        }
                                        Set<String> hidden = Bridge.getInstance().allHiddenIds();
                                        if (hidden.isEmpty()) {
                                            restoreView(v);
                                            return;
                                        }

                                        // Use the actual adapter receiving the getView() call. The
                                        // previous closure-captured `gvAdpRef` only pointed at the
                                        // first q2 instance seen at module-init time — after FTSMainUI
                                        // finish()→recreate the call dispatches to a new q2 instance
                                        // and the stale ref returns null getItem(), masking the hide.
                                        Object curAdp = p.thisObject;
                                        Object item = null;
                                        try {
                                            Method getItem = curAdp.getClass().getMethod("getItem", int.class);
                                            item = getItem.invoke(curAdp, pos);
                                        } catch (Throwable ignored) {
                                            try {
                                                Method getItem = curAdp.getClass().getSuperclass()
                                                        .getMethod("getItem", int.class);
                                                item = getItem.invoke(curAdp, pos);
                                            } catch (Throwable ignored2) {}
                                        }
                                        if (sRowDumpCount < 40) {
                                            sRowDumpCount++;
                                            try {
                                                ViewGroup.LayoutParams lpd = v.getLayoutParams();
                                                Log.i(TAG, "[SF:row] pos=" + pos
                                                        + " vcls=" + v.getClass().getName()
                                                        + " h=" + v.getHeight()
                                                        + " mh=" + v.getMeasuredHeight()
                                                        + " lph=" + (lpd != null ? lpd.height : -99)
                                                        + " vis=" + v.getVisibility()
                                                        + " item=" + (item == null ? "null" : item.getClass().getName())
                                                        + " id=" + (item == null ? "-" : extractAnyWxid(item)));
                                            } catch (Throwable ignored) {}
                                        }

                                        if (item == null) return;

                                        String id = extractAnyWxid(item);
                                        if (id == null || !hidden.contains(id)) {
                                            restoreView(v);
                                            return;
                                        }

                                        v.setVisibility(View.GONE);
                                        v.setMinimumHeight(0);
                                        v.setPadding(0, 0, 0, 0);
                                        ViewGroup.LayoutParams lp = v.getLayoutParams();
                                        if (lp != null) {
                                            // AbsListView 仅当 lp.height>0 才按 EXACTLY 量；=0 会走
                                            // UNSPECIFIED → 行按原高渲染，留下 173px 灰块。用 1px≈隐形。
                                            lp.height = 1;
                                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                                ViewGroup.MarginLayoutParams mlp =
                                                        (ViewGroup.MarginLayoutParams) lp;
                                                mlp.topMargin = 0;
                                                mlp.bottomMargin = 0;
                                            }
                                            v.setLayoutParams(lp);
                                        }
                                        Log.i(TAG, "[SF:gv] blocked pos=" + pos + " id=" + id);
                                        Bridge.getInstance().addRawFeedLine("[SF:gv] blocked " + id);
                                    }
                                });
                                Log.i(TAG, "[SF:gv] single-hook getView filter installed on "
                                        + gvCls.getName());
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:gv] single-hook getView install fail: " + t);
                            }

                            // ─── ★★ Path Y (2026-05-28): backing-list removeIf in ndc beforeHook
                            // 实证 (probe_yz2b0_v8): iz2.i.f 装 fz2.z, fz2.z 继承 fz2.y,
                            //   fz2.y.e = String wxid/groupId (例: 45592178108@chatroom)
                            // extractAnyWxid → scanWxidLiteral 走 superclass+recurse, 能拿 fz2.y.e
                            // 真正不渲染 → ListView getCount 自然变小 → 不留白条 slot (F-31 治本)
                            // v15.1 getView GONE 路径保留为护套, 万一某 elem 拿不到 wxid 仍能拦住
                            try {
                                XposedBridge.hookAllMethods(adapter.getClass(),
                                        "notifyDataSetChanged",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void beforeHookedMethod(MethodHookParam p) {
                                                if (!StateMachine.getInstance().isActive()) return;
                                                Set<String> hidden =
                                                        Bridge.getInstance().allHiddenIds();
                                                if (hidden.isEmpty()) return;
                                                try {
                                                    Field qf = findFieldByName(
                                                            p.thisObject.getClass(), "q");
                                                    if (qf == null) return;
                                                    qf.setAccessible(true);
                                                    Object qList = qf.get(p.thisObject);
                                                    if (!(qList instanceof java.util.List)) return;
                                                    int removed = 0;
                                                    for (Object prov : (java.util.List<?>) qList) {
                                                        if (prov == null) continue;
                                                        Object aN = readFieldValue(prov, "n");
                                                        if (!(aN instanceof java.util.List)
                                                                || ((java.util.List<?>) aN).isEmpty()) continue;
                                                        for (Object section : (java.util.List<?>) aN) {
                                                            if (section == null) continue;
                                                            Field ff = findFieldByName(
                                                                    section.getClass(), "f");
                                                            if (ff == null) continue;
                                                            ff.setAccessible(true);
                                                            Object fObj = ff.get(section);
                                                            if (!(fObj instanceof java.util.List)) continue;
                                                            Iterator<?> it =
                                                                    ((java.util.List<?>) fObj).iterator();
                                                            while (it.hasNext()) {
                                                                Object elem = it.next();
                                                                String wxid = extractAnyWxid(elem);
                                                                if (wxid != null
                                                                        && hidden.contains(wxid)) {
                                                                    it.remove();
                                                                    removed++;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (removed > 0) {
                                                        Log.i(TAG, "[SF:Y] removed=" + removed
                                                                + " inst=" + System
                                                                .identityHashCode(p.thisObject));
                                                        Bridge.getInstance().addRawFeedLine(
                                                                "[SF:Y] removed=" + removed);
                                                    }
                                                } catch (Throwable t) {
                                                    Log.w(TAG, "[SF:Y] fail: " + t);
                                                }
                                            }
                                        });
                                Log.i(TAG, "[SF:Y] backing-list filter installed on "
                                        + adapter.getClass().getName());
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:Y] install fail: " + t);
                            }

                            // ─── PRIMARY filter — B' 方案 (2026-05-27): 5-hook position offset ──
                            // DISABLED 2026-05-27 方案 D: 5-hook offset cause final_v11..v14 search
                            // result area went blank — modifying getCount via setResult on every
                            // adapter swap interfered with q2's internal data-flow. Reverting to
                            // v10's q2.j GONE strategy (proven ✅ in final_v10 5/5 ✅) and clearing
                            // the ListView divider so the GONE row leaves no visible gap.
                            // Code kept for archaeology; flip back to `try` to revive offset path.
                            if (false) try {
                                Class<?> gvCls = null;
                                int gvDeclared = 0;
                                for (Class<?> c = adapter.getClass();
                                     c != null && c != Object.class
                                             && c != android.widget.BaseAdapter.class;
                                     c = c.getSuperclass()) {
                                    int cnt = 0;
                                    for (Method m : c.getDeclaredMethods()) {
                                        if (sRenderHook.equals(m.getName())) cnt++;
                                    }
                                    if (cnt > 0) { gvCls = c; gvDeclared = cnt; break; }
                                }
                                if (gvCls == null) {
                                    Log.w(TAG, "[SF:gv] no declared " + sRenderHook + " in hierarchy of "
                                            + adapter.getClass().getName() + " — hook skipped");
                                    throw new NoSuchMethodException(sRenderHook + " not declared");
                                }
                                Log.i(TAG, "[SF:gv] hooking 5-hook offset on " + gvCls.getName()
                                        + " " + sRenderHook + " declared count=" + gvDeclared);

                                // 1) getCount: shrink + rebuild skip map (throttled)
                                XposedBridge.hookAllMethods(gvCls, "getCount",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void afterHookedMethod(MethodHookParam p) {
                                                Object result = p.getResult();
                                                if (!(result instanceof Integer)) return;
                                                int orig = (Integer) result;
                                                if (orig <= 0) {
                                                    sSkipMap = new int[0];
                                                    return;
                                                }
                                                if (!StateMachine.getInstance().isActive()) {
                                                    sSkipMap = new int[0];
                                                    return;
                                                }
                                                Set<String> hidden = Bridge.getInstance().allHiddenIds();
                                                if (hidden.isEmpty()) {
                                                    sSkipMap = new int[0];
                                                    return;
                                                }

                                                // ── ANR fix (final_v11): throttle rebuild to
                                                // protect the UI thread. Within RESCAN_THROTTLE_MS
                                                // we reuse the previous sSkipMap without invoking
                                                // adapter.getItem(i) via reflection.
                                                long now = System.currentTimeMillis();
                                                int skip;
                                                if (now - sLastRescanAt < RESCAN_THROTTLE_MS
                                                        && sSkipMap.length > 0) {
                                                    skip = sSkipMap.length;
                                                } else {
                                                    sLastRescanAt = now;
                                                    skip = rebuildSkipMap(p.thisObject, orig, hidden);
                                                }
                                                if (skip <= 0) return;
                                                p.setResult(orig - skip);
                                                if (now - sLastSkipLogAt > 800L) {
                                                    sLastSkipLogAt = now;
                                                    StringBuilder sb = new StringBuilder("[SF:cnt] orig=")
                                                            .append(orig).append(" skip=").append(skip)
                                                            .append(" exposed=").append(orig - skip)
                                                            .append(" idx=[");
                                                    int[] m = sSkipMap;
                                                    for (int i = 0; i < m.length; i++) {
                                                        if (i > 0) sb.append(',');
                                                        sb.append(m[i]);
                                                    }
                                                    sb.append(']');
                                                    Log.i(TAG, sb.toString());
                                                    Bridge.getInstance().addRawFeedLine(
                                                            "[SF:cnt] skip=" + skip + "/" + orig);
                                                }
                                            }
                                        });

                                // 2-5) Position translation for getView / getItem /
                                //      getItemId / getItemViewType — single shared callback.
                                //
                                //   sRescanDepth>0  → rebuildSkipMap is walking; pass-through.
                                //   sInTranslate>0 → an outer hook frame on this thread already
                                //                    translated; inner calls (getView → getItem)
                                //                    must NOT re-translate or we double-skip.
                                //
                                // afterHook decrements the in-translate counter to allow the
                                // next outer ListView call to translate again.
                                XC_MethodHook posHook = new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam p) {
                                        Integer rescan = sRescanDepth.get();
                                        if (rescan != null && rescan > 0) return;
                                        Integer inT = sInTranslate.get();
                                        if (inT != null && inT > 0) {
                                            // nested: just count, don't translate
                                            sInTranslate.set(inT + 1);
                                            return;
                                        }
                                        if (p.args.length < 1 || !(p.args[0] instanceof Integer)) {
                                            sInTranslate.set(1);
                                            return;
                                        }
                                        int displayPos = (Integer) p.args[0];
                                        int origPos = displayToOrig(displayPos);
                                        if (origPos != displayPos) p.args[0] = origPos;
                                        sInTranslate.set(1);
                                    }

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam p) {
                                        Integer rescan = sRescanDepth.get();
                                        if (rescan != null && rescan > 0) return;
                                        Integer inT = sInTranslate.get();
                                        if (inT != null && inT > 0) {
                                            sInTranslate.set(inT - 1);
                                        }
                                    }
                                };
                                XposedBridge.hookAllMethods(gvCls, "getView", posHook);
                                XposedBridge.hookAllMethods(gvCls, "getItem", posHook);
                                XposedBridge.hookAllMethods(gvCls, "getItemId", posHook);
                                XposedBridge.hookAllMethods(gvCls, "getItemViewType", posHook);

                                Log.i(TAG, "[SF:gv] 5-hook offset filter installed on " + gvCls.getName());
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:gv] getView 5-hook install fail: " + t);
                            }

                            // ─── Provider-layer probe (Upstream): q2.a(Provider, query, boolean) ──
                            // DISABLED 2026-05-27 v14: provider probe iterates 15 providers per search
                            // dispatch, each doing reflective field access on a/n etc. Even with
                            // dedupe + cap, the per-search reflection load is non-trivial; suspect
                            // contributor to the FTS data-flow stall.
                            if (false) try {
                                XposedBridge.hookAllMethods(adapter.getClass(), "a",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void afterHookedMethod(MethodHookParam p) {
                                                if (sProviderDumpCount >= PROVIDER_DUMP_LIMIT) return;
                                                if (p.args.length < 1 || p.args[0] == null) return;
                                                Object provider = p.args[0];
                                                String provCn = provider.getClass().getName();
                                                // Restrict to known FTS provider prefixes
                                                if (!provCn.startsWith("yz2.")
                                                        && !provCn.startsWith("i81.")
                                                        && !provCn.startsWith("nb2.")
                                                        && !provCn.startsWith("xz2.")) return;
                                                try {
                                                    Field af = findFieldRecursive(provider.getClass(), "a");
                                                    if (af == null) return;
                                                    af.setAccessible(true);
                                                    Object a = af.get(provider);
                                                    if (a == null) return;
                                                    Field nf = findFieldRecursive(a.getClass(), "n");
                                                    if (nf == null) return;
                                                    nf.setAccessible(true);
                                                    Object n = nf.get(a);
                                                    if (!(n instanceof java.util.List)) return;
                                                    java.util.List nl = (java.util.List) n;
                                                    if (nl.isEmpty()) return;
                                                    Object first = nl.get(0);
                                                    if (first == null) return;
                                                    String seenKey = provCn + "|" + first.getClass().getName();
                                                    if (!sProviderSeen.add(seenKey)) return;
                                                    sProviderDumpCount++;
                                                    Log.i(TAG, "[SF:PROV] #" + sProviderDumpCount
                                                            + " provider=" + provCn
                                                            + " a.n.size=" + nl.size()
                                                            + " first=" + first.getClass().getName());
                                                    // Dump first item all fields (1 level deep, recursive on
                                                    // non-primitive non-String fields up to depth 2)
                                                    dumpProviderItem(first, 0);
                                                } catch (Throwable ignored) {
                                                    // schema mismatch — provider may use different fields
                                                }
                                            }
                                        });
                                Log.i(TAG, "[SF:PROV] q2.a(Provider,query,bool) probe installed");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:PROV] a() hook fail: " + t);
                            }
                            // f0.getItem(int) probe — DISABLED 2026-05-27 v14: redundant with
                            // 5-hook offset which already hooks getItem; the diagnostic log is no
                            // longer needed once tz2.* types are confirmed.
                            if (false) try {
                                XposedBridge.hookAllMethods(adapter.getClass().getSuperclass(), "getItem",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                                                Integer rescan = sRescanDepth.get();
                                                if (rescan != null && rescan > 0) return;
                                                if (sUpGetItemLogCount >= UP_GETITEM_LOG_LIMIT) return;
                                                Object result = p.getResult();
                                                if (result == null) return;
                                                sUpGetItemLogCount++;
                                                Log.i(TAG, "[SF:q2:up] getItem(" + p.args[0]
                                                        + ") → " + result.getClass().getName());
                                            }
                                        });
                                Log.i(TAG, "[SF:q2:up] f0.getItem(int) probe installed");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:q2:up] getItem hook fail: " + t);
                            }
                            // f0.n(String, i3) — DISABLED 2026-05-27 v14: query trace not needed
                            // anymore; suspect of slowing the actual search-dispatch path.
                            if (false) try {
                                XposedBridge.hookAllMethods(adapter.getClass().getSuperclass(), "n",
                                        new XC_MethodHook() {
                                            @Override
                                            protected void beforeHookedMethod(MethodHookParam p) {
                                                StringBuilder sb = new StringBuilder("[SF:q2:up] n(");
                                                for (int i = 0; i < p.args.length; i++) {
                                                    if (i > 0) sb.append(", ");
                                                    Object a = p.args[i];
                                                    if (a == null) sb.append("null");
                                                    else if (a instanceof CharSequence) {
                                                        String s = a.toString();
                                                        if (s.length() > 80) s = s.substring(0, 80) + "...";
                                                        sb.append("String=\"").append(s).append("\"");
                                                    } else sb.append(a.getClass().getSimpleName());
                                                }
                                                sb.append(")");
                                                Log.i(TAG, sb.toString());
                                            }
                                        });
                                Log.i(TAG, "[SF:q2:up] f0.n(String,i3) probe installed");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:q2:up] n() hook fail: " + t);
                            }
                            // ─── End upstream probes ───

                            // Hook notifyDataSetChanged → DISABLED 2026-05-27 v14: dump probe was
                            // a one-shot diagnostic, no longer needed and a deep reflective walk
                            // of all adapter fields is heavy for a hot path.
                            if (false) try {
                                XposedBridge.hookAllMethods(adapter.getClass().getSuperclass(),
                                        "notifyDataSetChanged",
                                        new XC_MethodHook() {
                                            private volatile boolean sDumped = false;
                                            @Override
                                            protected void beforeHookedMethod(MethodHookParam p) {
                                                if (sDumped) return;
                                                sDumped = true;
                                                Log.i(TAG, "[SF:q2:ndc] notifyDataSetChanged — dumping adapter fields");
                                                for (Class<?> c = adpRef.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                                                    for (Field f : c.getDeclaredFields()) {
                                                        try {
                                                            f.setAccessible(true);
                                                            Object v = f.get(adpRef);
                                                            String fn = c.getSimpleName() + "." + f.getName();
                                                            if (v instanceof java.util.List) {
                                                                int sz = ((java.util.List<?>) v).size();
                                                                StringBuilder sb = new StringBuilder("[SF:q2:ndc] ")
                                                                        .append(fn).append(" sz=").append(sz);
                                                                if (sz > 0) {
                                                                    for (int i = 0; i < Math.min(sz, 5); i++) {
                                                                        Object item = ((java.util.List<?>) v).get(i);
                                                                        sb.append("\n  [").append(i).append("] ")
                                                                                .append(item != null ? item.getClass().getName() : "null");
                                                                    }
                                                                }
                                                                Log.i(TAG, sb.toString());
                                                            } else if (v instanceof java.util.Map) {
                                                                Log.i(TAG, "[SF:q2:ndc] " + fn + " Map sz="
                                                                        + ((java.util.Map<?,?>) v).size());
                                                            }
                                                        } catch (Throwable ignored) {}
                                                    }
                                                }
                                            }
                                        });
                                Log.i(TAG, "[SF:q2:ndc] notifyDataSetChanged dump hook installed");
                            } catch (Throwable t) {
                                Log.w(TAG, "[SF:q2:ndc] hook fail: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[SF] q2 probe v4 installed (ListView.setAdapter)");
        } catch (Throwable t) {
            Log.w(TAG, "[SF] q2 probe v4 fail: " + t);
        }

        // Hot-reload: actively push notifyDataSetChanged() to the cached q2 adapter so
        // the visible search list re-renders the instant the state machine flips (V↔H)
        // or the hidden set mutates. Without this the rows rendered before the flip
        // stay on-screen until the user manually retypes the query.
        StateMachine.getInstance().addListener("SearchFilter",
                (oldState, newState) -> {
                    Log.i(TAG, "[SF:sm] " + oldState + " → " + newState);
                    refreshCurrentQ2Adapter("sm " + oldState + "→" + newState);
                });
        RefreshBus.getInstance().register("SearchFilter", hidden -> {
            Log.i(TAG, "[SF:bus] hidden set changed hidden=" + hidden);
            refreshCurrentQ2Adapter("hidden=" + hidden);
        });
    }

    /**
     * Force the currently-cached FTS q2 adapter to re-render. Safe to call from any
     * thread (posts to main looper). Silently no-op if no adapter is cached (e.g.
     * FTSMainUI never opened, or already finish()'d and GC'd).
     */
    private static void refreshCurrentQ2Adapter(String reason) {
        final Object adp = sCurrentQ2AdapterRef.get();
        if (adp == null) {
            Log.i(TAG, "[SF:refresh] no adapter cached, skip (" + reason + ")");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Method m = adp.getClass().getMethod("notifyDataSetChanged");
                m.invoke(adp);
                Log.i(TAG, "[SF:refresh] notifyDataSetChanged forced (" + reason + ")");
            } catch (Throwable t) {
                Log.w(TAG, "[SF:refresh] fail: " + t);
            }
        });
    }

    /**
     * Hidden entry point: hidden mode + global search password → visible mode.
     *
     * WeChat 8.0.71 emits z15.ef6 result items with fields like:
     *   d=111111 e=111111 o=<em class="highlight">111111</em>
     * That stream is already proven to hit, unlike EditText hooks on the custom search UI.
     */
    private static boolean tryUnlockFromSearchResults(Collection<?> coll) {
        StateMachine sm = StateMachine.getInstance();
        if (sm.getState() != StateMachine.State.HIDDEN) return false;

        String pwd = sm.getPassword();
        if (pwd == null || pwd.isEmpty()) return false;

        boolean matched = false;
        for (Object item : coll) {
            if (hasExactStringField(item, pwd)) {
                matched = true;
                break;
            }
        }
        if (!matched) return false;

        long now = System.currentTimeMillis();
        if (now - sLastUnlockAt < 1500L) return true;
        sLastUnlockAt = now;

        Log.i(TAG, "[SF:unlock] password matched in global search, exit hidden");
        sm.beginUnlock();
        boolean ok = sm.attemptUnlock(pwd);
        Log.i(TAG, "[SF:unlock] ok=" + ok + " state=" + sm.getStateName());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Activity act = UiContextTracker.getCurrentActivity();
            if (act != null && !act.isFinishing()) {
                act.finish();
                Log.i(TAG, "[SF:unlock] activity finished=" + act.getClass().getSimpleName());
            }
        }, 120);
        return true;
    }

    private static boolean hasExactStringField(Object obj, String expected) {
        if (obj == null || expected == null) return false;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof CharSequence && expected.equals(v.toString())) return true;
                } catch (Throwable ignored) {
                    // Keep entry-point detection non-fatal.
                }
            }
        }
        return false;
    }

    private static int readFz2C(Object item) {
        try {
            Field cf = item.getClass().getDeclaredField("c");
            cf.setAccessible(true);
            Object cv = cf.get(item);
            return (cv instanceof Number) ? ((Number) cv).intValue() : -1;
        } catch (Throwable ignored) { return -1; }
    }

    /**
     * Extract wxid from fz2.e:
     *   c≠3 → g is SOSItemRelevant (or similar wrapper) carrying wxid in one of
     *         candidate fields: username / userName / field_username / wxid / d
     *   c=3 → g is UIN (no direct wxid), returns null until UIN→wxid map exists
     * Returns null on any reflection failure.
     */
    private static String extractFz2Wxid(Object item) {
        try {
            // Read c (record type discriminator)
            int c = readFz2C(item);
            if (c == 3) return null; // inline chat-record, g is UIN — skip until map

            // Read g (payload object)
            Field gf = item.getClass().getDeclaredField("g");
            gf.setAccessible(true);
            Object g = gf.get(item);
            if (g == null) return null;

            // g may itself be a String wxid (some 8.0.x variants) or a wrapper.
            if (g instanceof CharSequence) {
                String s = g.toString();
                if (s.startsWith("wxid_") || s.endsWith("@chatroom")) return s;
            }

            // Try common wxid-carrying field names on g (SOSItemRelevant etc.)
            String[] candidates = {
                    "username", "userName", "field_username",
                    "wxid", "d", "talker", "userId"
            };
            for (Class<?> cls = g.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (String name : candidates) {
                    try {
                        Field f = cls.getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(g);
                        if (v instanceof CharSequence) {
                            String s = v.toString();
                            if (!s.isEmpty()) return s;
                        }
                    } catch (NoSuchFieldException ignored) {
                        // try next candidate
                    }
                }
            }
        } catch (Throwable ignored) {
            // Schema mismatch — caller falls back to "seen pass" logging.
        }
        return null;
    }

    /**
     * One-shot dump of fz2.e field structure — runs FZ2_DUMP_LIMIT times,
     * then becomes a no-op. Used to confirm field names match the assumed schema
     * (c, g, etc.) and to discover SOSItemRelevant wxid field if extractFz2Wxid
     * keeps returning null.
     */
    private static void dumpFz2eFields(Object item) {
        if (sFz2DumpCount >= FZ2_DUMP_LIMIT) return;
        sFz2DumpCount++;
        try {
            StringBuilder sb = new StringBuilder("[SF:DUMP] fz2.e #")
                    .append(sFz2DumpCount).append(" fields:");
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(item);
                        String vs = describeValue(v);
                        sb.append("\n  ").append(c.getSimpleName()).append('.').append(f.getName())
                                .append(" : ").append(f.getType().getSimpleName())
                                .append(" = ").append(vs);
                        // If this is the g field, dump its inner fields too
                        if ("g".equals(f.getName()) && v != null
                                && !(v instanceof CharSequence) && !(v instanceof Number)) {
                            for (Class<?> gc = v.getClass(); gc != null && gc != Object.class; gc = gc.getSuperclass()) {
                                for (Field gf : gc.getDeclaredFields()) {
                                    try {
                                        gf.setAccessible(true);
                                        Object gv = gf.get(v);
                                        sb.append("\n    g/").append(gc.getSimpleName())
                                                .append('.').append(gf.getName())
                                                .append(" : ").append(gf.getType().getSimpleName())
                                                .append(" = ").append(describeValue(gv));
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[SF:DUMP] fz2.e err: " + t);
        }
    }

    /** Walk class → parent chain to find a declared field by name. */
    private static Field findFieldRecursive(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /**
     * Dump the g data item from q2.j(View,g,boolean) or q2.l(Context,g).
     * This is the search result data object that q2 binds to views.
     */
    private static void dumpQ2DataItem(Object g, int pos, Object adapter) {
        try {
            if (g == null) {
                Log.i(TAG, "[SF:q2] data g is null pos=" + pos);
                return;
            }
            StringBuilder sb = new StringBuilder("[SF:q2] data item pos=").append(pos)
                    .append(" cls=").append(g.getClass().getName());
            // Dump all fields of the data item
            for (Class<?> c = g.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(g);
                        String vs = describeValue(v);
                        if (vs.length() > 150) vs = vs.substring(0, 150) + "...";
                        sb.append("\n  ").append(c.getSimpleName()).append('.')
                                .append(f.getName()).append(" : ")
                                .append(f.getType().getSimpleName())
                                .append(" = ").append(vs);
                    } catch (Throwable ignored) {}
                }
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[SF:q2] dump err: " + t);
        }

        // On first dump, also dump adapter's backing list fields
        if (sQ2DumpCount == 1) {
            try {
                StringBuilder asb = new StringBuilder("[SF:q2:adapter] class=")
                        .append(adapter.getClass().getName());
                for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(adapter);
                            asb.append("\n  adapter/").append(c.getSimpleName())
                                    .append('.').append(f.getName())
                                    .append(" : ").append(f.getType().getSimpleName());
                            if (v instanceof java.util.List) {
                                int sz = ((java.util.List<?>) v).size();
                                asb.append(" sz=").append(sz);
                                if (sz > 0) {
                                    Object first = ((java.util.List<?>) v).get(0);
                                    asb.append(" firstCls=").append(first != null ? first.getClass().getName() : "null");
                                }
                            } else if (v instanceof java.util.Map) {
                                asb.append(" sz=").append(((java.util.Map<?,?>) v).size());
                            } else {
                                String vs = describeValue(v);
                                if (vs.length() > 100) vs = vs.substring(0, 100) + "...";
                                asb.append(" = ").append(vs);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                Log.i(TAG, asb.toString());
            } catch (Throwable t) {
                Log.w(TAG, "[SF:q2:adapter] dump err: " + t);
            }
        }
    }

    private static String describeValue(Object v) {
        if (v == null) return "null";
        if (v instanceof CharSequence) {
            String s = v.toString();
            if (s.length() > 80) s = s.substring(0, 80) + "...";
            return "\"" + s + "\"";
        }
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return v.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(v));
    }

    /**
     * One-shot dump of z15.ef6 field structure — used to discover which field
     * carries the wxid (since d/e/o are search-term/highlight strings, not wxid).
     * Logs class hierarchy + all declared fields + types + values for first
     * EF6_DUMP_LIMIT hits, then becomes a no-op.
     */
    private static void dumpEf6Fields(Object item) {
        if (sEf6DumpCount >= EF6_DUMP_LIMIT) return;
        sEf6DumpCount++;
        try {
            StringBuilder sb = new StringBuilder("[SF:DUMP] z15.ef6 #")
                    .append(sEf6DumpCount).append(" fields:");
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(item);
                        String vs;
                        if (v == null) {
                            vs = "null";
                        } else if (v instanceof CharSequence) {
                            String s = v.toString();
                            if (s.length() > 80) s = s.substring(0, 80) + "...";
                            vs = "\"" + s + "\"";
                        } else if (v instanceof Number || v instanceof Boolean) {
                            vs = v.toString();
                        } else {
                            vs = v.getClass().getSimpleName() + "@" + Integer.toHexString(
                                    System.identityHashCode(v));
                        }
                        sb.append("\n  ").append(c.getSimpleName()).append('.').append(f.getName())
                                .append(" : ").append(f.getType().getSimpleName())
                                .append(" = ").append(vs);
                    } catch (Throwable ignored) {
                        // Reflection failure on framework/protobuf fields is expected.
                    }
                }
            }
            // Deep-dump p=ch6 field (protobuf message wrapper) — contains
            // the actual chat record object that may carry talker wxid.
            try {
                Field pf = item.getClass().getDeclaredField("p");
                pf.setAccessible(true);
                Object ch6 = pf.get(item);
                if (ch6 != null) {
                    sb.append("\n  -- ch6 internals (").append(ch6.getClass().getName()).append(") --");
                    for (Class<?> gc = ch6.getClass(); gc != null && gc != Object.class; gc = gc.getSuperclass()) {
                        for (Field gf : gc.getDeclaredFields()) {
                            try {
                                gf.setAccessible(true);
                                Object gv = gf.get(ch6);
                                sb.append("\n    ch6/").append(gc.getSimpleName())
                                        .append('.').append(gf.getName())
                                        .append(" : ").append(gf.getType().getSimpleName())
                                        .append(" = ").append(describeValue(gv));
                            } catch (Throwable ignored) {}
                        }
                    }
                    // Deep-dump ch6.e (z15.g — protobuf message body, likely has talker)
                    try {
                        Field ef = ch6.getClass().getDeclaredField("e");
                        ef.setAccessible(true);
                        Object gObj = ef.get(ch6);
                        if (gObj != null) {
                            sb.append("\n    -- ch6.e internals (").append(gObj.getClass().getName()).append(") --");
                            for (Class<?> gc = gObj.getClass(); gc != null && gc != Object.class; gc = gc.getSuperclass()) {
                                for (Field gf : gc.getDeclaredFields()) {
                                    try {
                                        gf.setAccessible(true);
                                        Object gv = gf.get(gObj);
                                        sb.append("\n      ch6.e/").append(gc.getSimpleName())
                                                .append('.').append(gf.getName())
                                                .append(" : ").append(gf.getType().getSimpleName())
                                                .append(" = ").append(describeValue(gv));
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[SF:DUMP] err: " + t);
        }
    }

    /**
     * Extract wxid / groupId from a q2 bind data item (jz2.g subclass).
     * 8.0.71 schema (装机实证 2026-05-27 by user probe):
     *   g.a == 0 → tz2.g0 (group header) → no wxid
     *   g.a == 1 → tz2.u1 (contact) → g.f.s = wxid like "wxid_xxx"
     *   g.a == 2 → tz2.s1 (chatroom) → g.s = groupId like "xxx@chatroom"
     * Returns null on any schema mismatch.
     */
    private static String extractQ2BindWxid(Object g) {
        if (g == null) return null;
        try {
            Field af;
            try {
                af = g.getClass().getDeclaredField("a");
            } catch (NoSuchFieldException nsfe) {
                // 'a' may live on a superclass (jz2.g) in some builds
                Class<?> sup = g.getClass().getSuperclass();
                af = (sup != null) ? sup.getDeclaredField("a") : null;
                if (af == null) return null;
            }
            af.setAccessible(true);
            Object av = af.get(g);
            if (!(av instanceof Number)) return null;
            int a = ((Number) av).intValue();

            if (a == 1) {
                // tz2.u1.f.s = wxid
                Field ff = g.getClass().getDeclaredField("f");
                ff.setAccessible(true);
                Object f = ff.get(g);
                if (f == null) return null;
                Field sf = f.getClass().getDeclaredField("s");
                sf.setAccessible(true);
                Object s = sf.get(f);
                return (s instanceof CharSequence) ? s.toString() : null;
            } else if (a == 2) {
                // tz2.s1.s = groupId
                Field sf = g.getClass().getDeclaredField("s");
                sf.setAccessible(true);
                Object s = sf.get(g);
                return (s instanceof CharSequence) ? s.toString() : null;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Extract wxid / chatroom id from ANY search-result data item.
     * Strategy:
     *   1. If it's a tz2.* (jz2.g subclass), use the precise g.a path (extractQ2BindWxid)
     *   2. Otherwise, scan declared fields up to depth 2 for any String matching
     *      wxid_xxx or *@chatroom — fallback for unknown wrappers (iz2.i / fz2.e / ...)
     * Returns null if nothing found.
     */
    private static String extractAnyWxid(Object item) {
        if (item == null) return null;
        String cn = item.getClass().getName();
        if (cn.startsWith("tz2.")) {
            String id = extractQ2BindWxid(item);
            if (id != null) return id;
        }
        // Fallback: shallow field scan looking for wxid / chatroom literal
        return scanWxidLiteral(item, 0, new HashSet<Integer>());
    }

    // ── Path Y helpers (2026-05-28): walk the class hierarchy to find named fields ──
    private static Field findFieldByName(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Object readFieldValue(Object obj, String name) {
        if (obj == null) return null;
        Field f = findFieldByName(obj.getClass(), name);
        if (f == null) return null;
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String scanWxidLiteral(Object obj, int depth, Set<Integer> seen) {
        if (obj == null || depth > 2) return null;
        if (obj instanceof CharSequence) {
            String s = obj.toString();
            if (s.startsWith("wxid_") || s.endsWith("@chatroom") || s.endsWith("@app")) return s;
            return null;
        }
        Class<?> cls = obj.getClass();
        if (cls.isPrimitive() || cls.isEnum()) return null;
        String cn = cls.getName();
        if (cn.startsWith("java.") || cn.startsWith("android.")) return null;
        int id = System.identityHashCode(obj);
        if (!seen.add(id)) return null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try { fields = c.getDeclaredFields(); } catch (Throwable t) { continue; }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    String found = scanWxidLiteral(v, depth + 1, seen);
                    if (found != null) return found;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /**
     * Walk the FTSMainUI decor-view tree to discover the actual ListView/RecyclerView
     * that holds search results, and dump their adapter class names. One-shot.
     */
    private static void walkFtsTree(ViewGroup vg, int depth) {
        if (depth > 14) return;
        StringBuilder pad = new StringBuilder();
        for (int k = 0; k < depth; k++) pad.append("  ");
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child == null) continue;
            String cn = child.getClass().getName();
            String extra = "";
            if (child instanceof android.widget.ListView) {
                Object a = ((android.widget.ListView) child).getAdapter();
                extra = " [LV adapter=" + (a != null ? a.getClass().getName() : "null")
                        + " count=" + ((android.widget.ListView) child).getCount() + "]";
            } else if (cn.contains("RecyclerView")) {
                try {
                    Object a = child.getClass().getMethod("getAdapter").invoke(child);
                    int rvc = -1;
                    if (a != null) {
                        try {
                            rvc = (int) a.getClass().getMethod("getItemCount").invoke(a);
                        } catch (Throwable ignored) {}
                    }
                    extra = " [RV adapter=" + (a != null ? a.getClass().getName() : "null")
                            + " count=" + rvc + "]";
                } catch (Throwable ignored) {
                    extra = " [RV adapter=?]";
                }
            }
            Log.i(TAG, "[SF:fts-tree] " + pad.toString() + "[" + i + "] " + cn
                    + " vis=" + child.getVisibility()
                    + " w=" + child.getWidth() + " h=" + child.getHeight()
                    + extra);
            if (child instanceof ViewGroup) walkFtsTree((ViewGroup) child, depth + 1);
        }
    }

    /** Restore a previously-hidden ListView row so ViewHolder recycling does not leak. */
    private static void restoreView(View v) {
        try {
            if (v.getVisibility() != View.VISIBLE) v.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            // collapsed sentinel is now 1px (was 0); accept both when restoring
            if (lp != null && (lp.height == 0 || lp.height == 1)) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Translate ListView's displayPos to the original list index. The skip map is
     * a sorted-ascending array of original positions to hide. For each original
     * position s in the map, if s ≤ origPos, bump origPos by 1.
     *   skipMap=[2,5,7], displayPos=4 → orig: 4→5(skip2)→6(skip5)→6(7>6 stop) = 6
     */
    private static int displayToOrig(int displayPos) {
        int[] skip = sSkipMap;
        if (skip == null || skip.length == 0) return displayPos;
        int orig = displayPos;
        for (int s : skip) {
            if (s <= orig) orig++;
            else break;
        }
        return orig;
    }

    /**
     * Rebuild sSkipMap for the given adapter (f0/q2 subclass). Walks 0..orig-1
     * calling adapter.getItem(i) via reflection, marking positions whose item
     * carries a hidden wxid / chatroom id. sRescanDepth is bumped during the
     * walk so the getItem position-translation hook short-circuits and we read
     * the original list slot, not the translated one.
     *
     * Returns the new skip count. Caller (getCount afterHook) should adjust the
     * exposed count by subtracting this value.
     */
    private static int rebuildSkipMap(Object adapter, int orig, Set<String> hidden) {
        if (orig <= 0 || hidden == null || hidden.isEmpty()) {
            sSkipMap = new int[0];
            return 0;
        }
        Method getItem = null;
        for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                getItem = c.getDeclaredMethod("getItem", int.class);
                getItem.setAccessible(true);
                break;
            } catch (NoSuchMethodException ignored) {}
        }
        if (getItem == null) {
            sSkipMap = new int[0];
            return 0;
        }
        int[] tmp = new int[Math.min(orig, 64)];
        int n = 0;
        Integer depthBefore = sRescanDepth.get();
        sRescanDepth.set((depthBefore == null ? 0 : depthBefore) + 1);
        try {
            for (int i = 0; i < orig; i++) {
                try {
                    Object item = getItem.invoke(adapter, i);
                    String id = extractAnyWxid(item);
                    if (id != null && hidden.contains(id)) {
                        if (n == tmp.length) {
                            int[] grown = new int[tmp.length * 2];
                            System.arraycopy(tmp, 0, grown, 0, n);
                            tmp = grown;
                        }
                        tmp[n++] = i;
                    }
                } catch (Throwable ignored) {
                    // schema mismatch on a single row — pass-through
                }
            }
        } finally {
            sRescanDepth.set(depthBefore == null ? 0 : depthBefore);
        }
        int[] arr = new int[n];
        System.arraycopy(tmp, 0, arr, 0, n);
        sSkipMap = arr;
        return n;
    }

    /**
     * Provider-layer item dump — iz2.i and similar wrappers inside yz2.h.a.n etc.
     * Logs declared fields and drills 1 level into FTS / storage subobjects to
     * discover the wxid path. Called only PROVIDER_DUMP_LIMIT times in total.
     */
    private static void dumpProviderItem(Object item, int depth) {
        if (item == null || depth > 1) return;
        try {
            StringBuilder sb = new StringBuilder();
            String indent = (depth == 0) ? "  " : "      ";
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(item);
                        String vs = describeValue(v);
                        sb.append("\n").append(indent).append(c.getSimpleName())
                                .append('.').append(f.getName())
                                .append(" : ").append(f.getType().getSimpleName())
                                .append(" = ").append(vs);
                        // Drill 1 level into FTS / storage subobjects to find wxid
                        if (depth == 0 && v != null
                                && !(v instanceof CharSequence)
                                && !(v instanceof Number)
                                && !(v instanceof Boolean)
                                && !(v instanceof java.util.Collection)
                                && !v.getClass().isArray()) {
                            String vcn = v.getClass().getName();
                            if (vcn.startsWith("iz2.") || vcn.startsWith("yz2.")
                                    || vcn.startsWith("tz2.") || vcn.startsWith("nb2.")
                                    || vcn.startsWith("com.tencent.mm.storage.")) {
                                for (Class<?> ic = v.getClass(); ic != null && ic != Object.class; ic = ic.getSuperclass()) {
                                    for (Field inf : ic.getDeclaredFields()) {
                                        try {
                                            inf.setAccessible(true);
                                            Object iv = inf.get(v);
                                            sb.append("\n        ").append(ic.getSimpleName())
                                                    .append('.').append(inf.getName())
                                                    .append(" : ").append(inf.getType().getSimpleName())
                                                    .append(" = ").append(describeValue(iv));
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            Log.i(TAG, "[SF:PROV-DUMP]" + sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[SF:PROV-DUMP] err: " + t);
        }
    }

    private static boolean containsHiddenWxid(Object obj, Set<String> hidden,
                                              int depth, Set<Integer> seen) {
        if (obj == null || depth > 3) return false;

        if (obj instanceof CharSequence) {
            String s = obj.toString();
            for (String wxid : hidden) {
                if (wxid != null && !wxid.isEmpty() && s.contains(wxid)) return true;
            }
            return false;
        }

        Class<?> cls = obj.getClass();
        if (cls.isPrimitive() || cls.isEnum()) return false;
        String cn = cls.getName();
        if (cn.startsWith("java.lang.") && !(obj instanceof Collection)) return false;

        int id = System.identityHashCode(obj);
        if (!seen.add(id)) return false;

        if (obj instanceof Collection) {
            for (Object child : (Collection<?>) obj) {
                if (containsHiddenWxid(child, hidden, depth + 1, seen)) return true;
            }
            return false;
        }

        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }

            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (containsHiddenWxid(v, hidden, depth + 1, seen)) return true;
                } catch (Throwable ignored) {
                    // Reflection failures are expected on some framework/protobuf fields.
                }
            }
        }
        return false;
    }
}
