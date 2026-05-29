package com.ghost.assist.moduleD;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * P_CV1 V1 — 动态发现通讯录 LiveList / Adapter（2026-05-27 接 V0 诊断失败后）
 *
 * 【背景】v0/v1/v2/v3 诊断装机已验证：
 *   - MvvmAddressUIFragment.lifecycle  → 0 hits（class 在 8.0.71 仍存在但不被调用）
 *   - AddressLiveList.ctor             → 0 hits（实例预先存在，模块 install 晚于 UI 构造）
 *   - MvvmList 基类 ctor               → 0 hits（同上时序）
 *   - RecyclerView.setAdapter          → 0 hits（adapter 预先绑定）
 *   - ik3.t0.notifyDataSetChanged      → 0 hits（contact adapter 不走此方法）
 *   - 唯一点火：ArrayList.addAll(fc5.g)（global hook）— 但 thisObject 是 backing list 非 LiveList 本身
 *
 * 【结论】所有 "未来生命周期" hook 都来不及；只能事后扫 View 树捞实例。
 *
 * 【本类做什么】
 *   - registerActivityLifecycleCallbacks（与 TriggerGuard 同一接入点）
 *   - onActivityResumed 时 post 600ms 扫 Activity DecorView View 树
 *   - 找 ViewGroup 子节点是 androidx.recyclerview.widget.RecyclerView 的
 *   - 调 RV.getAdapter() → 在 adapter 实例字段里找 AddressLiveList（或 fc5.g List）
 *   - 找到后写 ContactFilter.sLiveListRef + sAdapterRef → 让 ContactHotReload.handleBusVisible 走通
 *   - 首次写盘后加 latch，重复 Activity Resume 不再重扫（防 KPI 抖）
 *
 * 【本文件门控锁定】（与 ContactHotReload 同口径）
 *   允许：写 ContactFilter.sLiveListRef / sAdapterRef（package-private，跨类直访）
 *         读 ContactFilter.MVVMLIST_DATA / ADDR_ITEM_CLS
 *         读 Application Activity 生命周期回调
 *   禁止：调 StateMachine.* / AuthManager.* / RefreshBus.fire* / NativeBridge.* / Bridge 写字段
 *         反射 ActivityThread.mActivities（用 Application.registerActivityLifecycleCallbacks 替代，铁律 7）
 *         在被发现的 LiveList / Adapter 上额外 hook（只读捞引用）
 *
 * 【KPI 控制】
 *   - 仅当当前 Activity 短类名命中白名单（LauncherUI / MainTabUI / MainUI / Launcher / Address / Contact / Chatroom）
 *     才扫；其它 Activity 不扫。
 *   - 写盘 latch 锁死：首次成功 → 不再扫。
 *   - 扫描深度 ≤ 6；查到 RecyclerView 即取，不深挖。
 */
public class ContactDiscoveryHook {

    private static final String[] ACT_WHITELIST = {
            "LauncherUI", "MainTabUI", "MainUI", "Launcher",
            "Address", "Contact", "Chatroom"
    };
    private static final String RV_CLS = "androidx.recyclerview.widget.RecyclerView";
    private static final String LV_CLS = "android.widget.ListView";
    private static final long SCAN_DELAY_MS = 800L;

    private static volatile boolean sInstalled = false;
    private static volatile boolean sFoundLiveList = false;
    private static volatile boolean sFoundAdapter = false;
    private static volatile int sTreeDumpRemaining = 4; // 失败前 4 轮 dump View 树类名（V1.3 扩 30→80 节点）
    private static volatile WeakReference<Activity> sCurrentActivity = null;
    private static volatile long sLastScanFromAddAllMs = 0L;
    private static final long SCAN_FROM_ADDALL_COOLDOWN_MS = 2000L; // 防 addAll 抖
    private static final Handler sMain = new Handler(Looper.getMainLooper());

    /** 通讯录 RecyclerView / ListView host 弱引用。View 比 adapter WeakRef 更不易 GC，
     *  用于 sAdapterRef 失活时免延迟重捞 adapter 并 notify。 */
    static volatile WeakReference<View> sAdapterHostRef = null;

    public     static void install(Application app) {
        if (sInstalled || app == null) return;
        sInstalled = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) {
                sCurrentActivity = new WeakReference<>(a);
                String shortName = a.getClass().getSimpleName();
                // P_CV1-G 群聊探针（2026-05-29）：ChatroomContactUI 独立页，不受主列表 latch 限制。
                if (shortName.contains("Chatroom")) {
                    final WeakReference<Activity> cref = new WeakReference<>(a);
                    sMain.postDelayed(new Runnable() {
                        @Override public void run() {
                            Activity act = cref.get();
                            if (act != null) captureChatroomAdapter(act); // 装群聊隐藏 hook（自带 latch）
                        }
                    }, SCAN_DELAY_MS);
                    // 方案 B：进群聊页后多时机改 footer「N个群聊」计数（隐藏态扣掉隐藏群数）
                    scheduleGroupFooterFix();
                }
                if (sFoundLiveList && sFoundAdapter) return;
                if (!matchWhitelist(shortName)) return;
                final WeakReference<Activity> ref = new WeakReference<>(a);
                sMain.postDelayed(new Runnable() {
                    @Override public void run() {
                        Activity act = ref.get();
                        if (act == null) return;
                        scanActivity(act);
                    }
                }, SCAN_DELAY_MS);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        Log.i(ContactFilter.TAG, "[CDH] install (lifecycle callbacks)");
    }

    /**
     * 由 ContactFilter.installAddAllHook 在 fc5.g 命中时调用 —— 这是最强 L1 信号：
     * 此时通讯录数据流确实在跑、目标 Fragment + RecyclerView/ListView 必然 attached。
     * 200ms 后扫前台 Activity，找 RecyclerView/ListView，回写 sLiveListRef + sAdapterRef。
     * 2 秒冷却防 addAll 抖（多个 batch 连续触发）。
     */
    static void scheduleScanFromAddAll() {
        if (sFoundLiveList && sFoundAdapter) return;
        long now = System.currentTimeMillis();
        if (now - sLastScanFromAddAllMs < SCAN_FROM_ADDALL_COOLDOWN_MS) return;
        sLastScanFromAddAllMs = now;
        scanAtDelay(200L, "T200");
        scanAtDelay(800L, "T800");
        scanAtDelay(2000L, "T2000");
    }

    private static void scanAtDelay(long ms, final String tag) {
        sMain.postDelayed(new Runnable() {
            @Override public void run() {
                if (sFoundLiveList && sFoundAdapter) return;
                Activity act = sCurrentActivity != null ? sCurrentActivity.get() : null;
                if (act == null) {
                    Log.i(ContactFilter.TAG, "[CDH:fromAddAll:" + tag + "] no current activity");
                    return;
                }
                Log.i(ContactFilter.TAG, "[CDH:fromAddAll:" + tag + "] act="
                        + act.getClass().getSimpleName() + " scan");
                scanActivity(act);
            }
        }, ms);
    }

    // 方案 B：群聊页 footer「N个群聊」计数器直接改文本（不依赖 jadx 类名）。
    private static final java.util.regex.Pattern GROUP_COUNT_PAT =
            java.util.regex.Pattern.compile("(\\d+)\\s*个群聊");

    static void scheduleGroupFooterFix() {
        for (long d : new long[]{300L, 900L, 1800L}) {
            sMain.postDelayed(new Runnable() {
                @Override public void run() { correctGroupFooterCount(); }
            }, d);
        }
    }

    /** 找到群聊页 footer 上「N个群聊」的 TextView，把 N 改成 N − 隐藏群数。 */
    private static void correctGroupFooterCount() {
        try {
            int hidden = ContactGroupHide.sGroupHiddenRemoved;
            int target = ContactGroupHide.sGroupVisibleCount;   // 绝对目标 = 过滤后可见群数（幂等）
            if (hidden <= 0 || target < 0) return; // 仅隐藏态且本页有隐藏群、且已知可见数时才改
            Activity a = sCurrentActivity != null ? sCurrentActivity.get() : null;
            if (a == null || !a.getClass().getName().contains("ChatroomContactUI")) return;
            Window w = a.getWindow();
            if (w == null) return;
            View root = w.getDecorView();
            if (root == null) return;
            Deque<View> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                View v = stack.pop();
                if (v instanceof TextView) {
                    CharSequence cs = ((TextView) v).getText();
                    if (cs != null) {
                        java.util.regex.Matcher m = GROUP_COUNT_PAT.matcher(cs.toString());
                        if (m.find()) {
                            int n = Integer.parseInt(m.group(1));
                            // 幂等：直接设成绝对目标 target，不在当前值上减（避免重复触发递减到 0）。
                            // 仅当当前 != target 且当前正好是 target+hidden（即原始总数）时才改，防误改。
                            if (n != target && n == target + hidden) {
                                ((TextView) v).setText(target + "个群聊");
                                Log.i(ContactFilter.TAG, "[CGF:footerB] " + n + "→" + target
                                        + " (hidden=" + hidden + ")");
                            }
                        }
                    }
                }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
                }
            }
        } catch (Throwable t) {
            Log.w(ContactFilter.TAG, "[CGF:footerB] err: " + t);
        }
    }

    private static boolean matchWhitelist(String shortName) {
        if (shortName == null) return false;
        for (String key : ACT_WHITELIST) {
            if (shortName.contains(key)) return true;
        }
        return false;
    }

    private static void scanActivity(Activity a) {
        try {
            Window window = a.getWindow();
            if (window == null) return;
            View root = window.getDecorView();
            if (root == null) return;
            List<View> hosts = findAdapterHosts(root, 24);
            if (hosts.isEmpty()) {
                if (sTreeDumpRemaining > 0) {
                    sTreeDumpRemaining--;
                    dumpTree(root, a.getClass().getSimpleName());
                }
                Log.i(ContactFilter.TAG, "[CDH:scan] act=" + a.getClass().getSimpleName()
                        + " no-rv-no-lv");
                return;
            }
            int probed = 0;
            for (View host : hosts) {
                probed += inspectAdapterHost(host, a.getClass().getSimpleName());
                if (sFoundLiveList && sFoundAdapter) break;
            }
            Log.i(ContactFilter.TAG, "[CDH:scan] act=" + a.getClass().getSimpleName()
                    + " hosts=" + hosts.size() + " probed=" + probed
                    + " ll=" + sFoundLiveList + " ad=" + sFoundAdapter);
        } catch (Throwable t) {
            Log.w(ContactFilter.TAG, "[CDH:scan] err: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // P_CV1-G 群聊 adapter 捕获（2026-05-29）：进 ChatroomContactUI 时扫到 live s0 adapter，
    // 用它的真 Class 装隐藏 hook（绕 classloader 分裂）。诊断 dump 已隔离到 debug/ContactGroupProbe
    // （默认关闭）。
    // -----------------------------------------------------------------------
    private static void captureChatroomAdapter(Activity a) {
        try {
            Window w = a.getWindow();
            if (w == null) return;
            View root = w.getDecorView();
            if (root == null) return;
            for (View host : findAdapterHosts(root, 24)) {
                Object adapter;
                try {
                    adapter = host.getClass().getMethod("getAdapter").invoke(host);
                } catch (Throwable t) { continue; }
                if (adapter == null) continue;
                // ListView 常被 HeaderViewListAdapter 包一层 → 解包拿真 adapter
                Object real = adapter;
                for (int guard = 0; guard < 3; guard++) {
                    try {
                        Object wrapped = real.getClass().getMethod("getWrappedAdapter").invoke(real);
                        if (wrapped == null || wrapped == real) break;
                        real = wrapped;
                    } catch (Throwable t) { break; }
                }
                if ("com.tencent.mm.ui.contact.s0".equals(real.getClass().getName())) {
                    ContactGroupHide.hookGroupAdapterFromLive(real); // 真功能：装群聊隐藏 hook
                    com.ghost.assist.debug.ContactGroupProbe.dumpAdapter(real); // 诊断（默认 no-op）
                }
            }
        } catch (Throwable t) {
            Log.w(ContactFilter.TAG, "[CGF] captureChatroomAdapter err: " + t);
        }
    }

    /** RecyclerView (含子类) + ListView (含子类，AbsListView 后裔)。
     *
     * V4.1：8.0.71 R8 把 androidx.recyclerview.widget.RecyclerView 重命名为 f2/g2/…，
     * 用全限定名硬比 + 走 getSuperclass 找原名永远 false（实证：V1.1-V1.3 13 次扫
     * 全 no-rv-no-lv 即此 Bug）。改成沿父类链按 simpleName 含 "RecyclerView" / "ListView"
     * 识别——R8 默认保留用户自定义子类 simpleName（WxRecyclerView / WxListView / 
     * OverScrollMultiTaskRecyclerView 实证）。
     */
    private static List<View> findAdapterHosts(View root, int maxDepth) {
        List<View> out = new ArrayList<>();
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{root, 0});
        while (!stack.isEmpty()) {
            Object[] cur = stack.pop();
            View v = (View) cur[0];
            int depth = (Integer) cur[1];
            if (v == null || depth > maxDepth) continue;
            if (isAdapterHost(v.getClass())) {
                out.add(v);
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    stack.push(new Object[]{g.getChildAt(i), depth + 1});
                }
            }
        }
        return out;
    }

    /** 父类链上任意一层 simpleName 含 "RecyclerView" / "ListView" 即认定 host。 */
    private static boolean isAdapterHost(Class<?> cls) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            String sn = cur.getSimpleName();
            if (sn != null && (sn.contains("RecyclerView") || sn.contains("ListView"))) {
                return true;
            }
            cur = cur.getSuperclass();
        }
        return false;
    }

    /** 失败兜底诊断：打印 View 树前 80 个节点的类短名（含父类链），帮我们看用什么列表容器。 */
    private static void dumpTree(View root, String actName) {
        try {
            StringBuilder sb = new StringBuilder("[CDH:tree] act=").append(actName)
                    .append(" rootCls=").append(root.getClass().getSimpleName())
                    .append(" rootCC=").append(root instanceof ViewGroup
                            ? ((ViewGroup) root).getChildCount() : -1)
                    .append(" | ");
            int count = 0;
            Deque<Object[]> stack = new ArrayDeque<>();
            stack.push(new Object[]{root, 0});
            while (!stack.isEmpty() && count < 80) {
                Object[] cur = stack.pop();
                View v = (View) cur[0];
                int depth = (Integer) cur[1];
                if (v == null) continue;
                String cn = v.getClass().getSimpleName();
                String pn = v.getClass().getSuperclass() != null
                        ? v.getClass().getSuperclass().getSimpleName() : "?";
                int cc = v instanceof ViewGroup ? ((ViewGroup) v).getChildCount() : -1;
                sb.append("d").append(depth).append(":").append(cn)
                        .append("<").append(pn).append(">");
                if (cc >= 0) sb.append("cc=").append(cc);
                sb.append(" ");
                count++;
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    int n = g.getChildCount();
                    for (int i = 0; i < n; i++) {
                        stack.push(new Object[]{g.getChildAt(i), depth + 1});
                    }
                }
            }
            Log.i(ContactFilter.TAG, sb.toString());
        } catch (Throwable ignored) {}
    }

    private static boolean isSubclassOf(Class<?> cls, String parentName) {
        Class<?> cur = cls.getSuperclass();
        while (cur != null) {
            if (parentName.equals(cur.getName())) return true;
            cur = cur.getSuperclass();
        }
        return false;
    }

    /**
     * 在 host (RecyclerView / ListView) 的 adapter 字段图里找：
     *   - MvvmList 子类（含 fc5.g item 的 List 持有者）
     *   - 写 sLiveListRef + sAdapterRef
     * 返回 1 表示有写盘动作。
     */
    private static int inspectAdapterHost(View host, String actName) {
        try {
            Object adapter;
            try {
                adapter = host.getClass().getMethod("getAdapter").invoke(host);
            } catch (NoSuchMethodException nm) {
                return 0;
            }
            if (adapter == null) return 0;
            String adapterCls = adapter.getClass().getName();
            Log.i(ContactFilter.TAG, "[CDH:host] act=" + actName
                    + " hostCls=" + host.getClass().getSimpleName()
                    + " adapterCls=" + adapterCls);
            Object liveList = findContactLiveListInObject(adapter, 0, 3);
            if (liveList != null) {
                if (!sFoundLiveList) {
                    ContactFilter.sLiveListRef = liveList;
                    sFoundLiveList = true;
                    Log.i(ContactFilter.TAG, "[CDH:found] liveListCls="
                            + liveList.getClass().getName()
                            + " owner=" + adapterCls + " ref-set=1");
                }
                if (!sFoundAdapter) {
                    ContactFilter.sAdapterRef = new WeakReference<>(adapter);
                    sFoundAdapter = true;
                    Log.i(ContactFilter.TAG, "[CDH:found] adapterCls=" + adapterCls
                            + " ref-set=1");
                }
                // Always keep the host ref fresh so forceNotify can recover a GC'd sAdapterRef.
                sAdapterHostRef = new WeakReference<>(host);
                return 1;
            }
        } catch (Throwable t) {
            Log.w(ContactFilter.TAG, "[CDH:host] err: " + t);
        }
        return 0;
    }

    /**
     * 在 obj 的字段图（最深 maxDepth 层）找一个 List 字段 / MvvmList 子类，
     * 其内部 backing 第一个元素 == fc5.g。
     */
    @SuppressWarnings("unchecked")
    private static Object findContactLiveListInObject(Object obj, int depth, int maxDepth) {
        if (obj == null || depth > maxDepth) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields;
            try { fields = cls.getDeclaredFields(); }
            catch (Throwable ignored) { break; }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;
                    // 直查：是 List 且首元素 = fc5.g  →  这是 backing 但我们要 MvvmList 本体
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        if (!list.isEmpty()
                                && list.get(0) != null
                                && ContactFilter.ADDR_ITEM_CLS
                                        .equals(list.get(0).getClass().getName())) {
                            // 找到 backing list；它本身不是 MvvmList，跳过；要继续扫 obj 父链找持有它的 MvvmList
                            // 不立刻返回 list 本身（不能用作 sLiveListRef，restoreToLiveList 要反射 f135087o）
                            // 但 obj 本身可能就是 MvvmList — 检查 obj
                            if (isMvvmListLike(obj)) return obj;
                        }
                    }
                    // 嵌套：扫子对象（仅限 Tencent / mvvm 命名空间，避免乱扫）
                    if (depth < maxDepth && shouldRecurse(v)) {
                        Object found = findContactLiveListInObject(v, depth + 1, maxDepth);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean isMvvmListLike(Object o) {
        if (o == null) return false;
        Class<?> c = o.getClass();
        while (c != null && c != Object.class) {
            String n = c.getName();
            if (n.equals("com.tencent.mm.plugin.mvvmlist.MvvmList")) return true;
            if (n.contains("Address") && n.contains("LiveList")) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean hasContactItems(Object adapter) {
        return findContactLiveListInObject(adapter, 0, 3) != null;
    }

    private static boolean shouldRecurse(Object o) {
        if (o == null) return false;
        String n = o.getClass().getName();
        return n.startsWith("com.tencent.")
                || n.startsWith("ik3.")
                || n.startsWith("fc5.")
                || n.startsWith("kc5.")
                || n.startsWith("ic5.")
                || n.startsWith("dc5.");
    }

    // -----------------------------------------------------------------------
    // forceNotify — 当 sAdapterRef 失活时，从 sAdapterHostRef (View) 重捞 adapter 并立即 notify。
    // 由 ContactFilter.notifyContactAdapter 在 adapter==null 时同步调用（已在主线程）。
    // -----------------------------------------------------------------------

    /**
     * 无延迟地从 sAdapterHostRef (RecyclerView/ListView) 重捞通讯录 adapter 并 notify。
     * 成功时顺便刷新 sAdapterRef / sFoundAdapter，防止下次再 GC。
     * 必须在主线程调用。
     *
     * @return true 表示成功 notify。
     */
    static boolean forceNotify(String tag) {
        // 1. Try sAdapterHostRef first (cheapest path)
        View host = sAdapterHostRef != null ? sAdapterHostRef.get() : null;
        if (host != null) {
            try {
                Object adapter = host.getClass().getMethod("getAdapter").invoke(host);
                if (adapter != null) {
                    ContactFilter.sAdapterRef = new WeakReference<>(adapter);
                    sFoundAdapter = true;
                    adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
                    Log.i(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] via host ok "
                            + adapter.getClass().getSimpleName());
                    return true;
                }
            } catch (Throwable t) {
                Log.w(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] host err: " + t);
            }
        }

        // 2. Fall back to scanning current activity
        Activity act = sCurrentActivity != null ? sCurrentActivity.get() : null;
        if (act == null) {
            Log.w(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] no host no activity");
            return false;
        }
        try {
            Window window = act.getWindow();
            if (window == null) return false;
            View root = window.getDecorView();
            List<View> hosts = findAdapterHosts(root, 24);
            for (View h : hosts) {
                try {
                    Object adapter = h.getClass().getMethod("getAdapter").invoke(h);
                    if (adapter == null) continue;
                    // Quick check: does this adapter hold fc5.g items?
                    if (!hasContactItems(adapter)) continue;
                    sAdapterHostRef = new WeakReference<>(h);
                    ContactFilter.sAdapterRef = new WeakReference<>(adapter);
                    sFoundAdapter = true;
                    adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
                    Log.i(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] via scan ok "
                            + adapter.getClass().getSimpleName());
                    return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] scan err: " + t);
        }
        Log.w(ContactFilter.TAG, "[CDH:forceNotify:" + tag + "] not found");
        return false;
    }

}
