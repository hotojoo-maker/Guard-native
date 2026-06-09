package com.ghost.assist.moduleD;

import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 朋友圈好友动态小红点屏蔽（P21 优先 #1，差异化点）.
 *
 * 背景：
 *   iOS 蜘蛛密友/Catfish 都 hook 了朋友圈时间线 VC 过滤内容，但小红点 badge
 *   走独立通知路径未覆盖 → 隐藏密友后仍冒红点。
 *   Android 端 8.0.71 上提前解决。
 *
 * 实施策略：保守探针 — 同时挂多个候选 hook，装机时看哪个真的命中，
 *           留下命中的、删未命中的（不一次性押单点）。
 *
 * 候选 hook（用户 8.0.71 调研，2026-05-21）:
 *   A) com.tencent.mm.plugin.sns.storage.SnsCommentStorage.E1(...)
 *      after → 隐藏态返回 0（评论数 = 0 → 主界面无红点）
 *   B) FriendSnsPreference 相关字段（待装机定位完整类名）
 *   C) FindMoreFriendsUI.M1() / l0() after → 返回 false / 0
 *   D) Event 通道拦截:
 *      - FindMoreFriendEntryRedDotEvent
 *      - EnterSnsTimeLineUIEvent  (← 仅记录,不阻断)
 *      - NotifyTabTipsToShowEvent
 *      - ResetBadgeCountEvent
 *
 * 早返铁律：StateMachine.isActive() == false → 全部透传，不动微信
 */
public class MomentsRedDotGuard {

    private static final String TAG = "NCL";

    // 全限定类名（用户调研提供）
    private static final String SNS_COMMENT_STORAGE =
            "com.tencent.mm.plugin.sns.storage.w1"; // 8.0.71 混淆真名

    // FindMoreFriendsUI 候选完整路径（待装机确认）
    private static final String[] FIND_MORE_FRIENDS_UI_CANDIDATES = {
            "com.tencent.mm.plugin.findersdk.tmp.FindMoreFriendsUI",
            "com.tencent.mm.plugin.subapp.ui.pluginapp.FindMoreFriendsUI",
            "com.tencent.mm.ui.contact.FindMoreFriendsUI",
    };

    // SnsCommentStorage 上要 hook 的方法名（用户给 E1，但保守起见挂多个 0-param int 候选）
    private static final String[] COMMENT_COUNT_GETTERS = {
            "E1", "D1", "F1", "G1", "getUnreadCount", "getNewCount",
    };

    // EventBus 红点事件类名（用户调研，挂全简名匹配）
    private static final String[] RED_DOT_EVENT_SIMPLE_NAMES = {
            "FindMoreFriendEntryRedDotEvent",
            "NotifyTabTipsToShowEvent",
            "ResetBadgeCountEvent",
            "FinderRedDotEraseEvent",
    };

    private static boolean sInstalled = false;
    private static final Set<String> sDiagSeen = new HashSet<>();

    // w1 (SnsCommentStorage) 单例缓存
    private static volatile Object sW1Instance = null;

    // FindMoreFriendsUI 实例缓存（L1/onResume 首次触发时填充）
    // LauncherUI.onResume 用它来调 g1() 清视觉红点
    private static volatile Object sFMFInstance = null;

    // 冷启动时 FMF 懒加载未就绪：LauncherUI.onResume 设 pending=true，
    // FMF.onResume 首次触发时发现 pending=true 则立即清除红点。
    private static volatile boolean sPendingClearBadge = false;

    // 8.0.71 dump 实证（2026-05-21）：红点 Event 没有全局 EventCenter,
    // 每个 Event 类有自己的发布渠道。下面是 4 个目标类，先 dump 字段/方法，
    // 下一轮根据 dump 结果精准 hook。
    private static final String[] RED_DOT_TARGET_CLASSES = {
            "com.tencent.mm.plugin.brandservice.ui.timeline.preference.WeChatTabRedDotEvent",
            "com.tencent.mm.plugin.brandservice.ui.timeline.preference.TabRedDotChangeEvent",
            // 不知道完整路径，用 dump 兜底
            "WeChatTabRedDotEvent",
            "TabRedDotChangeEvent",
            "FinderRedDotTrigger",
            "FinderRedDotTextView",
    };

    // 8.0.71 实证完整路径（用户 dump 2026-05-21）
    private static final String CLS_WECHAT_TAB_EVT =
            "com.tencent.mm.autogen.events.WeChatTabRedDotEvent";
    private static final String CLS_TAB_CHANGE_EVT =
            "com.tencent.mm.autogen.events.TabRedDotChangeEvent";
    private static final String CLS_FINDER_RED_DOT_VIEW =
            "com.tencent.mm.plugin.finder.view.FinderRedDotTextView";

    // v2 实证零命中：红点不走 Java ctor / View 方法。v3 改 hook IListener 回调。
    // 用户 Frida dump 找到 4 个 IListener inner class，pattern 匹配：
    private static final String[] LISTENER_NEEDLES = {
            "DiscoveryFinderRedDotManager$",
            "FinderRedDotTrigger$",
            "FinderRedDotExpiredHandler$",
            "FinderRedDotAvatarManager$",
    };

    // v5 候选 wxid 字段名（朋友圈评论/赞 item 上的 wxid 持有字段）
    // iOS 8.0.71 实证（2026-05-21）：SnsAction.fromUserName / WCUserComment.commentUsername
    private static final String[] SMSG_WXID_FIELDS = {
            "fromUserName",                                   // ★ iOS SnsAction 实证（最高优先）
            "commentUsername",                                // ★ iOS WCUserComment 实证
            "d", "f435583d",                                  // e56 风格（D2/D3 已实证）
            "commentSrc", "src", "from", "talker",            // 评论者/发起人
            "userName", "username", "field_userName",         // 通用
            "p1", "k1",                                       // 单字母候选
    };

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;
        sClassLoader = lpparam.classLoader;

        // v2 兜底：Event ctor + View（精准 wxid 过滤）
        installEventBlocker(lpparam, CLS_WECHAT_TAB_EVT);
        installEventBlocker(lpparam, CLS_TAB_CHANGE_EVT);
        installFinderRedDotViewBlocker(lpparam);

        // v8/v9 主路径：ns.c 聚合桶 + FindMoreFriendsUI.onResume 主动刷新
        installNsCAggregationBlocker(lpparam);

        // v10 精准路径：w1 写入拦截
        installW1InteractionFilter(lpparam);

        // v22 互动列表游标过滤（非破坏「压制」）：hook w1.O1/a2 返回的 Cursor，
        // 跳过 talker∈密友 的行 → 与我相关 + 全部互动消息 都不显示密友，DB 记录不删。
        installInteractionListCursorFilter(lpparam);
        // P21B L1: WithAll 屏幕真源是 bm/rm -> s9.f(ValueCursor)，不走 w1.N1/O1/a2。
        installSnsMsgLiveCursorFilter(lpparam);

        // v11 出口封堵：SnsCommentStorage int/long getter → 0 + 捕获 sW1Instance
        installSnsCommentStorageHook(lpparam);

        // v15 Catfish 主线：hookSnsMsgList → addBlackList2（黑名单注入）+ SnsMsgUI 消费层
        installCatfishSnsMsgListHook(lpparam);
        installSnsMsgUIFilter(lpparam);
        installAdapterGetCountBlocker(lpparam);
        installSnsMsgAdapterFilter(lpparam); // v24: 过滤 bm/rm 适配器内部 c 列表(互动列表显示层真源)

        // v18 tab 角标计数器清零：TabRedDotChangeEvent int 字段 → 0（发现 tab 数字）
        installTabBadgeCounterSuppressor(lpparam);

        // v19 B 精准：显示层拦截 —— 发现 tab(osw)/朋友圈行(o58)红点数字改成非密友数
        installBadgePreciseFilter(lpparam);

        // boot-time 追溯清零
        retroactiveZeroOnBoot(lpparam);

        // [tl-bubble] 朋友圈顶部"X条新消息"互动气泡: 预热非密友计数缓存（供 n_t.setText 重算用）
        installTimelineBubblePrewarm(lpparam);
        // [list-dump] 只读: 互动列表(WithRelevance/WithAll) 真 adapter 结构 dump，供下一步过滤定位
        installSnsMsgListDump(lpparam);

        Log.i(TAG, "[MRD] install done (v26dbg3: + full field dump of real adapter bm)");
    }

    // -------------------------------------------------------------------------
    // P21B: 互动列表 bm/rm live Cursor 过滤.
    //   L1 2026-06-09: bm -> super com.tencent.mm.ui.s9.f = ValueCursor,
    //   cols include talker; AA熵 wxid_lzd2va16jd1622 is in talker.
    //   s9.t(Cursor) is the cursor setter; s9.g() returns current cursor.
    // -------------------------------------------------------------------------
    private static void installSnsMsgLiveCursorFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> s9 = lpparam.classLoader.loadClass("com.tencent.mm.ui.s9");
            int hooked = 0;
            for (Method m : s9.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 1 && android.database.Cursor.class.isAssignableFrom(pt[0])
                        && m.getReturnType() == void.class) {
                    final String mn = m.getName();
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!isSnsMsgAdapter(param.thisObject)) return;
                                if (!isFilteringActive()) return;
                                Object curObj = param.args[0];
                                if (!(curObj instanceof android.database.Cursor)) return;
                                android.database.Cursor wrapped = wrapSnsMsgCursor(
                                        (android.database.Cursor) curObj,
                                        "set:" + param.thisObject.getClass().getSimpleName() + "." + mn);
                                if (wrapped != curObj) param.args[0] = wrapped;
                            } catch (Throwable t) {
                                if (sDiagSeen.add("livecur_set_err"))
                                    Log.w(TAG, "[MRD:cursor:live] set err: " + t);
                            }
                        }
                    });
                    hooked++;
                } else if (pt.length == 0
                        && android.database.Cursor.class.isAssignableFrom(m.getReturnType())) {
                    final String mn = m.getName();
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!isSnsMsgAdapter(param.thisObject)) return;
                                if (!isFilteringActive()) return;
                                Object res = param.getResult();
                                if (!(res instanceof android.database.Cursor)) return;
                                android.database.Cursor wrapped = wrapSnsMsgCursor(
                                        (android.database.Cursor) res,
                                        "get:" + param.thisObject.getClass().getSimpleName() + "." + mn);
                                if (wrapped != res) param.setResult(wrapped);
                            } catch (Throwable t) {
                                if (sDiagSeen.add("livecur_get_err"))
                                    Log.w(TAG, "[MRD:cursor:live] get err: " + t);
                            }
                        }
                    });
                    hooked++;
                }
            }
            Log.i(TAG, "[MRD:cursor:live] s9 cursor hooks=" + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:cursor:live] install failed: " + t);
        }
    }

    private static boolean isSnsMsgAdapter(Object obj) {
        if (obj == null) return false;
        String cn = obj.getClass().getName();
        return "com.tencent.mm.plugin.sns.ui.bm".equals(cn)
                || "com.tencent.mm.plugin.sns.ui.rm".equals(cn);
    }

    private static int wrapSnsMsgCursorFields(Object adapter, String source) {
        if (!isSnsMsgAdapter(adapter)) return 0;
        int total = 0;
        Class<?> cls = adapter.getClass();
        for (int d = 0; cls != null && d < 6; d++) {
            if (cls.getName().startsWith("android.") || cls.getName().startsWith("java.")) break;
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(adapter);
                    if (!(v instanceof android.database.Cursor)) continue;
                    android.database.Cursor orig = (android.database.Cursor) v;
                    if (orig instanceof TalkerFilterCursor) continue;
                    android.database.Cursor wrapped = wrapSnsMsgCursor(orig, source + "." + f.getName());
                    if (wrapped == orig) continue;
                    int removed = orig.getCount() - wrapped.getCount();
                    f.set(adapter, wrapped);
                    total += Math.max(removed, 0);
                } catch (Throwable t) {
                    if (sDiagSeen.add("livecur_field_err"))
                        Log.w(TAG, "[MRD:cursor:live] field err: " + t);
                }
            }
            cls = cls.getSuperclass();
        }
        return total;
    }

    private static android.database.Cursor wrapSnsMsgCursor(android.database.Cursor cursor, String source) {
        if (cursor == null || cursor instanceof TalkerFilterCursor) return cursor;
        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
        if (hidden == null || hidden.isEmpty()) return cursor;
        TalkerFilterCursor fc = new TalkerFilterCursor(cursor, hidden);
        if (!fc.didFilter()) return cursor;
        if (sDiagSeen.add("livecur_" + source)) {
            Log.i(TAG, "[MRD:cursor:live] " + source + " "
                    + cursor.getCount() + "→" + fc.getCount());
        }
        InterceptCounter.getInstance().incF05("MRD-live-cursor");
        return fc;
    }

    // -------------------------------------------------------------------------
    // v15 Catfish 翻译：MainEntry.hookSnsMsgList() → addBlackList2(ArrayList)
    //   隐藏态：把密友 wxid 并入微信侧黑名单 List，供 SnsMsg 过滤/计数使用
    //   参考：refs/MainEntry.java hookSnsMsgList + P19 brief addBlackList2 语义
    // -------------------------------------------------------------------------
    private static final String[] CATFISH_SNSMSG_SCAN_CLASSES = {
            SNS_COMMENT_STORAGE,                                           // w1
            "com.tencent.mm.plugin.sns.ui.SnsMsgUI",                       // 父类
            "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithAll",
            "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance",
            "com.tencent.mm.plugin.sns.model.SnsLogic",
    };

    private static void installCatfishSnsMsgListHook(XC_LoadPackage.LoadPackageParam lpparam) {
        int total = 0;
        for (String cn : CATFISH_SNSMSG_SCAN_CLASSES) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(cn);
                for (Method m : cls.getDeclaredMethods()) {
                    if (!snsMsgMergeCandidate(m)) continue;
                    final String mn = m.getName();
                    final String fcn = cn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!shouldCatfishSnsMsgFilter()) return;
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof java.util.ArrayList) {
                                    @SuppressWarnings("unchecked")
                                    java.util.ArrayList<String> al =
                                            (java.util.ArrayList<String>) param.args[i];
                                    int before = al.size();
                                    purgeMergeList(al);
                                    if (sDiagSeen.add("mrg_" + fcn + "." + mn)) {
                                        Log.i(TAG, "[MRD:merge] merge " + fcn + "." + mn
                                                + " sz " + before + "→" + al.size());
                                    }
                                } else if (param.args[i] instanceof java.util.List) {
                                    @SuppressWarnings("unchecked")
                                    java.util.List<String> li = (java.util.List<String>) param.args[i];
                                    int before = li.size();
                                    purgeMergeListItems(li);
                                    if (sDiagSeen.add("mrgL_" + fcn + "." + mn)) {
                                        Log.i(TAG, "[MRD:merge] mergeList " + fcn + "." + mn
                                                + " sz " + before + "→" + li.size());
                                    }
                                }
                            }
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!shouldCatfishSnsMsgFilter()) return;
                            Object r = param.getResult();
                            if (r instanceof java.util.ArrayList) {
                                @SuppressWarnings("unchecked")
                                java.util.ArrayList<String> al = (java.util.ArrayList<String>) r;
                                int before = al.size();
                                purgeMergeList(al);
                                if (before != al.size() && sDiagSeen.add("ret_" + fcn + "." + mn)) {
                                    Log.i(TAG, "[MRD:merge] ret-merge " + fcn + "." + mn
                                            + " sz " + before + "→" + al.size());
                                }
                            }
                        }
                    });
                    total++;
                }
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "[MRD:merge] class not found: " + cn);
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:merge] " + cn + " failed: " + t);
            }
        }
        Log.i(TAG, "[MRD:merge] snsmsg blacklist hooks=" + total);
    }

    private static boolean snsMsgMergeCandidate(Method m) {
        Class<?>[] pt = m.getParameterTypes();
        if (pt.length == 1 && (java.util.ArrayList.class.isAssignableFrom(pt[0])
                || java.util.List.class.isAssignableFrom(pt[0]))) {
            return true;
        }
        if (pt.length == 0 && java.util.ArrayList.class.isAssignableFrom(m.getReturnType())) {
            String mn = m.getName().toLowerCase();
            return mn.contains("black") || mn.contains("filter") || mn.contains("msg")
                    || mn.contains("list");
        }
        return false;
    }

    private static boolean shouldCatfishSnsMsgFilter() {
        return AppConfig.getInstance().isMomentsRedDotEnabled()
                && StateMachine.getInstance().isActive()
                && !Bridge.getInstance().getWxids().isEmpty();
    }

    /** Catfish addBlackList2：密友 wxid 并入黑名单（不是 remove） */
    private static void purgeMergeList(java.util.ArrayList<String> list) {
        if (list == null) return;
        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
        for (String wxid : hidden) {
            if (wxid != null && !wxid.isEmpty() && !list.contains(wxid)) {
                list.add(wxid);
            }
        }
    }

    private static void purgeMergeListItems(java.util.List<String> list) {
        if (list == null) return;
        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
        for (String wxid : hidden) {
            if (wxid != null && !wxid.isEmpty() && !list.contains(wxid)) {
                list.add(wxid);
            }
        }
    }

    /**
     * v15：hook SnsMsgUI 父类生命周期（hookAllMethods，覆盖 WithAll/WithRelevance 子类）。
     * Catfish 消费链：进互动列表 → 读 w1 计数归零 → tab/气泡红点灭。
     */
    private static void installSnsMsgUIFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        // v17: 与 UiContextTracker 同款 — hookAllMethods(Activity.class, "onResume")
        // 再按 class name 过滤 SnsMsgUI*。
        // 实证：hookAllMethods(子类, onResume) 在 ART JIT 下不触发（v15/v16 均证伪），
        //       Activity.class 级别 hook 是唯一已实证有效的路径。
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String cn = param.thisObject.getClass().getName();
                            if (!cn.contains("SnsMsgUI")) return;
                            Log.i(TAG, "[MRD:smsg:enter] " + cn + ".onResume");
                            handleSnsMsgUIEnter(param.thisObject, "onResume", cn);
                        }
                    });
            Log.i(TAG, "[MRD:smsg] Activity.onResume → SnsMsgUI* filter installed (v17)");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:smsg] v17 hook failed: " + t);
        }
    }

    private static void handleSnsMsgUIEnter(Object instance, String method, String className) {
        // 一次性 dump 实例字段类型（找 List/Adapter/Storage 引用）
        if (sDiagSeen.add("smsg_dump_" + className)) {
            dumpInstanceFieldTypes(instance, className);
        }

        // gate：仅 HIDDEN + 有密友
        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
        if (!StateMachine.getInstance().isActive()) return;
        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
        if (hidden.isEmpty()) return;

        int totalRemoved = filterListFields(instance, hidden);
        if (totalRemoved > 0) {
            Log.i(TAG, "[MRD:smsg:filter] " + method + "@" + className
                    + " removed=" + totalRemoved);
            InterceptCounter.getInstance().incF05("MRD-smsg(x" + totalRemoved + ")");
        }
        // 进互动列表后主动清零 badge（无论是否过滤到密友，均尝试归零）
        // 因为 w1.y 由 :push 进程写入（Layer1 不可达），必须进列表时主动清零
        zeroSmsgBadge(instance, totalRemoved);
    }

    /**
     * 进互动列表后清零 badge 计数（w1.y → 0）。
     * 两条路径：
     *   1. SnsMsgUI.s 字段（类型 e8/k4，可能 = SnsCommentStorage）
     *   2. sW1Instance（由 getter 或 v2 hook 捕获）
     * 激进策略（v1）：有密友且 isActive 即清零，非精准递减。
     */
    private static void zeroSmsgBadge(Object smsgInstance, int removedCount) {
        if (!isFilteringActive()) return;
        boolean done = false;
        // 路径1：SnsMsgUI.s 字段（SnsCommentStorage 或其包装类）
        try {
            Object s = getFieldRecursive(smsgInstance, "s");
            if (s != null && !(s instanceof String)) {
                zeroW1FieldY(s);
                Log.i(TAG, "[MRD:smsg:badge] zeroed via smsg.s="
                        + s.getClass().getSimpleName() + " removed=" + removedCount);
                done = true;
            }
        } catch (Throwable ignored) {}
        // 路径2：全局 sW1Instance
        if (sW1Instance != null) {
            zeroW1FieldY(sW1Instance);
            if (!done) {
                Log.i(TAG, "[MRD:smsg:badge] zeroed via sW1Instance removed=" + removedCount);
            }
            done = true;
        }
        if (done) {
            com.ghost.assist.debug.DebugTelemetry.getInstance().addBlocked("badge");
            com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                    "badge", "smsg-filter",
                    com.ghost.assist.debug.DebugTelemetry.fields(
                            "removed", String.valueOf(removedCount)));
        } else {
            Log.w(TAG, "[MRD:smsg:badge] no storage instance — badge not zeroed yet");
        }
    }

    private static void dumpInstanceFieldTypes(Object instance, String tag) {
        StringBuilder sb = new StringBuilder("[MRD:smsg:dump] " + tag + ":\n");
        Class<?> cls = instance.getClass();
        for (int d = 0; cls != null && d < 6; d++) {
            if (cls.getName().startsWith("android.")) break;
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(instance);
                    if (v == null) continue;
                    String fullClass = v.getClass().getName();
                    String vDesc;
                    if (v instanceof java.util.List) {
                        java.util.List<?> ll = (java.util.List<?>) v;
                        vDesc = "List(" + ll.size() + ")";
                        if (!ll.isEmpty()) vDesc += "[" + ll.get(0).getClass().getName() + "]";
                    } else if (v instanceof android.widget.Adapter) {
                        vDesc = "Adapter[" + fullClass + "]";
                    } else if (v instanceof android.database.Cursor) {
                        vDesc = "Cursor[" + fullClass + " count=" + ((android.database.Cursor)v).getCount() + "]";
                    } else {
                        vDesc = fullClass;
                    }
                    sb.append("  ").append(cls.getSimpleName()).append(".")
                      .append(f.getName()).append(":").append(f.getType().getSimpleName())
                      .append(" = ").append(vDesc).append("\n");

                    // ★ 关键：标记疑似 Storage / Counter 类型 → 8.0.71 真名探测
                    String fcLow = fullClass.toLowerCase();
                    if (fcLow.contains("storage") || fcLow.contains("commentstorage")
                            || fcLow.contains("unread") || fcLow.contains("counter")
                            || fullClass.matches(".*\\bl4\\b.*")) {
                        Log.i(TAG, "[MRD:smsg:STORAGE?] " + f.getName() + " = " + fullClass);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        Log.i(TAG, sb.toString());
    }

    // -------------------------------------------------------------------------
    // [tl-bubble] 朋友圈顶部"X条新消息"互动气泡 (TextView id=n_t) 精准过滤.
    //   气泡=互动(点赞/评论)未读数, 点它→与我的互动→全部互动. 数据=SnsComment(talker=互动方wxid).
    //   做法: 拦 n_t.setText → HIDDEN 态重算为"非密友未读数"(复用 computeNonMiyouSnsUnread);
    //         >0 改写文字, =0/不可算 隐藏整条气泡. 计数走 1.5s 缓存+后台刷新, 不在 UI 线程查 DB(防 ANR).
    // -------------------------------------------------------------------------
    /** 进朋友圈时后台预热"非密友未读数"缓存, 让气泡 setText 时直接命中, 不卡 UI / 不闪. */
    private static void installTimelineBubblePrewarm(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final Object act = param.thisObject;
                            if (!act.getClass().getName().contains("SnsTimelineUI")) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            sBgExec.execute(new Runnable() {
                                @Override public void run() {
                                    try { computeNonMiyouSnsUnread(act); } catch (Throwable ignored) {}
                                }
                            });
                        }
                    });
            Log.i(TAG, "[MRD:tl] bubble prewarm installed");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:tl] prewarm install failed: " + t);
        }
    }

    /** 返回非密友未读数缓存; 过期则后台刷新, 本次返回旧值(可能 -1=从未算过), 绝不阻塞 UI 线程. */
    private static int nonMiyouCachedOrRefresh(final Object view) {
        long now = System.currentTimeMillis();
        int cached = sNonMiyouCache;
        if (cached >= 0 && now - sNonMiyouCacheTime < 1500L) return cached;
        sBgExec.execute(new Runnable() {
            @Override public void run() {
                try { computeNonMiyouSnsUnread(view); } catch (Throwable ignored) {}
            }
        });
        return cached;
    }

    /** 隐藏气泡药丸: 从 n_t 向上找 id=txa 的祖先(药丸容器)GONE; 找不到退回直接父. 每次都执行(微信会重建). */
    private static void hideBubbleContainer(android.view.View bubbleTv) {
        try {
            android.view.ViewParent p = bubbleTv.getParent();
            android.view.View firstParent = null;
            android.view.View pill = null;
            for (int i = 0; i < 6 && p instanceof android.view.View; i++) {
                android.view.View pv = (android.view.View) p;
                if (firstParent == null) firstParent = pv;
                if ("txa".equals(viewIdName(pv))) { pill = pv; break; }
                p = pv.getParent();
            }
            android.view.View target = (pill != null) ? pill : firstParent;
            if (target != null) {
                target.setVisibility(android.view.View.GONE);
                if (sDiagSeen.add("bubble_hide_target")) {
                    Log.i(TAG, "[MRD:tl] hide container=" + target.getClass().getName()
                            + " id=" + viewIdName(target));
                }
            }
        } catch (Throwable t) {
            if (sDiagSeen.add("bubble_hide_err")) Log.w(TAG, "[MRD:tl] hideContainer err: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // [list-dump] 只读: 进 SnsMsgUIWithRelevance/WithAll 后延迟 3s, 找屏上真 RecyclerView/ListView
    //   → getAdapter() → dump 适配器内 List 字段 + 首元素字段树(找 wxid). 解决 v24e removed=0
    //   (之前只猜 bm/rm, 没从 view 拿真 adapter). 纯日志, 不改显示.
    // -------------------------------------------------------------------------
    private static void installSnsMsgListDump(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final Object act = param.thisObject;
                            final String cn = act.getClass().getName();
                            if (!cn.contains("SnsMsgUI")) return;
                            if (!sDiagSeen.add("smsglist_deepdump_" + cn)) return;
                            Log.i(TAG, "[MRD:list] enter " + cn + " — deep dump @3s");
                            try {
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(new Runnable() {
                                            @Override public void run() { deepDumpSnsMsgList(act, cn); }
                                        }, 3000);
                            } catch (Throwable ignored) {}
                        }
                    });
            Log.i(TAG, "[MRD:list] SnsMsgUI deep-dump installed");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:list] install failed: " + t);
        }
    }

    private static void deepDumpSnsMsgList(Object act, String cn) {
        Log.i(TAG, "[MRD:list] === deep dump " + cn + " ===");
        try {
            android.view.View root = ((android.app.Activity) act).getWindow().getDecorView();
            java.util.List<android.view.View> lists = new java.util.ArrayList<>();
            findListContainers(root, lists, 0);
            Log.i(TAG, "[MRD:list] found " + lists.size() + " list container(s)");
            for (android.view.View lv : lists) {
                Object adapter = null;
                try { adapter = XposedHelpers.callMethod(lv, "getAdapter"); } catch (Throwable ignored) {}
                String acn = (adapter == null) ? "null" : adapter.getClass().getName();
                Log.i(TAG, "[MRD:list] " + lv.getClass().getName() + "#" + viewIdName(lv)
                        + " adapter=" + acn);
                // HeaderViewListAdapter(android 包装类) → 拆出里面的真 adapter
                if (adapter instanceof android.widget.HeaderViewListAdapter) {
                    Object real = ((android.widget.HeaderViewListAdapter) adapter).getWrappedAdapter();
                    Log.i(TAG, "[MRD:list]   unwrapped -> " + (real == null ? "null" : real.getClass().getName()));
                    adapter = real;
                }
                if (adapter != null) dumpAdapterListAndItem(adapter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:list] deep dump err: " + t);
        }
    }

    private static void findListContainers(android.view.View v, java.util.List<android.view.View> out, int depth) {
        if (v == null || depth > 30) return;
        String c = v.getClass().getName();
        if (c.contains("RecyclerView") || v instanceof android.widget.ListView) out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) findListContainers(g.getChildAt(i), out, depth + 1);
        }
    }

    // 拆 adapter 全字段: 名/类型/值类/集合或数组 size; 对任意非空集合/数组首元素再 dump wxid 路径.
    private static void dumpAdapterListAndItem(Object adapter) {
        try {
            Class<?> cls = adapter.getClass();
            for (int d = 0; cls != null && d < 5; d++) {
                String cn = cls.getName();
                if (cn.startsWith("android.") || cn.startsWith("java.")) break;
                Log.i(TAG, "[MRD:list]   -- fields of " + cn + " --");
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    try {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object v = f.get(adapter);
                        if (v == null) continue;
                        Object firstItem = null;
                        String desc;
                        if (v instanceof java.util.Collection) {
                            java.util.Collection<?> col = (java.util.Collection<?>) v;
                            desc = v.getClass().getName() + " size=" + col.size();
                            for (Object o : col) { if (o != null) { firstItem = o; break; } }
                        } else if (v instanceof java.util.Map) {
                            desc = v.getClass().getName() + " mapSize=" + ((java.util.Map<?, ?>) v).size();
                        } else if (v.getClass().isArray()) {
                            int len = java.lang.reflect.Array.getLength(v);
                            desc = v.getClass().getName() + " arrLen=" + len;
                            for (int i = 0; i < len; i++) {
                                Object o = java.lang.reflect.Array.get(v, i);
                                if (o != null) { firstItem = o; break; }
                            }
                        } else {
                            desc = v.getClass().getName();
                        }
                        Log.i(TAG, "[MRD:list]     " + f.getName() + ":" + f.getType().getSimpleName() + " = " + desc);
                        if (firstItem != null) {
                            Log.i(TAG, "[MRD:list]       " + f.getName() + "[0]=" + firstItem.getClass().getName());
                            dumpItemFieldsForWxid(firstItem, "         ", 0);
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) { Log.w(TAG, "[MRD:list]   adapter dump err: " + t); }
    }

    private static String viewIdName(android.view.View v) {
        try {
            int id = v.getId();
            if (id == android.view.View.NO_ID) return "NO_ID";
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable t) { return "?"; }
    }

    private static String parentChain(android.view.View v, int up) {
        StringBuilder sb = new StringBuilder();
        try {
            android.view.ViewParent p = v.getParent();
            for (int i = 0; i < up && p instanceof android.view.View; i++) {
                android.view.View pv = (android.view.View) p;
                sb.append(pv.getClass().getSimpleName()).append("#")
                  .append(viewIdName(pv)).append(" < ");
                p = pv.getParent();
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int filterListFields(Object instance, java.util.Set<String> hidden) {
        int total = 0;
        Class<?> cls = instance.getClass();
        for (int d = 0; cls != null && d < 5; d++) {
            if (cls.getName().startsWith("android.")) break;
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object listObj = f.get(instance);
                    if (!(listObj instanceof java.util.List)) continue; // v24e: 按值判断, 不靠声明类型(c 声明非List)
                    java.util.List rawList = (java.util.List) listObj;
                    if (rawList.isEmpty()) continue;

                    int removed = 0;
                    try {
                        for (java.util.Iterator it = rawList.iterator(); it.hasNext(); ) {
                            Object item = it.next();
                            if (item == null) continue;
                            String wxid = extractWxidFromSmsgItem(item);
                            if (wxid != null && hidden.contains(wxid)) {
                                try { it.remove(); removed++; }
                                catch (UnsupportedOperationException ignored) {}
                            }
                        }
                    } catch (java.util.ConcurrentModificationException cme) {
                        // 复制一份重试
                        java.util.ArrayList copy = new java.util.ArrayList(rawList);
                        java.util.Iterator it2 = copy.iterator();
                        while (it2.hasNext()) {
                            Object item = it2.next();
                            if (item == null) continue;
                            String wxid = extractWxidFromSmsgItem(item);
                            if (wxid != null && hidden.contains(wxid)) {
                                try { rawList.remove(item); removed++; }
                                catch (Throwable ignored) {}
                            }
                        }
                    }

                    if (removed > 0) {
                        total += removed;
                        if (sDiagSeen.add("smsg_f_" + f.getName())) {
                            Log.i(TAG, "[MRD:smsg:list] " + f.getName()
                                    + " removed=" + removed);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return total;
    }

    private static String extractWxidFromSmsgItem(Object item) {
        if (item == null) return null;
        // 直接字段
        for (String fn : SMSG_WXID_FIELDS) {
            Object v = getFieldRecursive(item, fn);
            if (v instanceof String) {
                String s = (String) v;
                if (s.startsWith("wxid_") || s.endsWith("@chatroom")) return s;
            }
        }
        // 嵌套字段：item.d 是个对象, 它上面再找 wxid
        Object inner = getFieldRecursive(item, "d");
        if (inner != null && !(inner instanceof String)) {
            for (String fn : SMSG_WXID_FIELDS) {
                Object v = getFieldRecursive(inner, fn);
                if (v instanceof String) {
                    String s = (String) v;
                    if (s.startsWith("wxid_") || s.endsWith("@chatroom")) return s;
                }
            }
        }
        return null;
    }

    private static Object getFieldRecursive(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 6; d++) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return null; }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Dump SnsMsgUIWithRelevance 字段 + 方法签名。
     * 用户进入此界面后，logcat 自动列出关键 getter / 列表字段，
     * 下一轮 v5 据此精准 hook"新消息列表过滤密友"。
     */
    private static void installSnsMsgUIDump(XC_LoadPackage.LoadPackageParam lpparam) {
        final String cn = "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance";
        try {
            Class<?> cls = lpparam.classLoader.loadClass(cn);

            // 字段
            StringBuilder fields = new StringBuilder("[MRD:smsg:fields] ");
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                fields.append(f.getName()).append(":").append(f.getType().getSimpleName()).append(" ");
            }
            Log.i(TAG, fields.toString());

            // 父类字段（很多 list 在父类）
            Class<?> sup = cls.getSuperclass();
            if (sup != null && !sup.getName().startsWith("android.")
                    && !sup.getName().equals("java.lang.Object")) {
                StringBuilder supFields = new StringBuilder("[MRD:smsg:superFields] "
                        + sup.getSimpleName() + ": ");
                for (java.lang.reflect.Field f : sup.getDeclaredFields()) {
                    supFields.append(f.getName()).append(":").append(f.getType().getSimpleName()).append(" ");
                }
                Log.i(TAG, supFields.toString());
            }

            // 方法（0-2 param，所有返回值）
            StringBuilder methods = new StringBuilder("[MRD:smsg:methods] ");
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterTypes().length > 2) continue;
                methods.append(m.getName())
                       .append("(").append(m.getParameterTypes().length).append("):")
                       .append(m.getReturnType().getSimpleName()).append(" ");
            }
            Log.i(TAG, methods.toString());

            // 实时探针：8.0.71 方法名全混淆，hook 所有 0-param void 方法兜底
            int probeHooked = 0;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != void.class) continue;
                final String mn = m.getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!sDiagSeen.add("smsg_enter_" + mn)) return;
                            Log.i(TAG, "[MRD:smsg:enter] " + mn + " thisObject="
                                    + param.thisObject.getClass().getSimpleName());
                            // dump 本类 + 父类字段（父类有 D:ArrayList, t:SnsCmdList）
                            java.util.List<Class<?>> dumpClasses = new java.util.ArrayList<>();
                            for (Class<?> c = param.thisObject.getClass();
                                    c != null && c != Object.class
                                    && !c.getName().startsWith("android.");
                                    c = c.getSuperclass()) {
                                dumpClasses.add(c);
                            }
                            for (Class<?> c : dumpClasses) {
                                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                                    try {
                                        f.setAccessible(true);
                                        Object v = f.get(param.thisObject);
                                        if (v == null) continue;
                                        String vs;
                                        if (v instanceof java.util.List) {
                                            vs = "List size=" + ((java.util.List) v).size();
                                        } else if (v instanceof java.util.Map) {
                                            vs = "Map size=" + ((java.util.Map) v).size();
                                        } else if (v instanceof Number || v instanceof Boolean) {
                                            vs = v.toString();
                                        } else {
                                            vs = v.getClass().getSimpleName();
                                        }
                                        Log.i(TAG, "[MRD:smsg:field] " + c.getSimpleName()
                                                + "." + f.getName() + " = " + vs);
                                    } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "[MRD:smsg:enter] failed: " + t);
                        }
                    }
                });
                probeHooked++;
            }
            Log.i(TAG, "[MRD:smsg] dump probe installed on " + cn
                    + ", " + probeHooked + " probes");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:smsg] load failed: " + t);
        }
    }

    /**
     * 扫 dex 找出所有名字含 RedDot listener pattern 的 inner class，
     * hook 它们的所有方法：在 hidden 模式下立即 setResult 短路（不调原方法）。
     */
    private static void installListenerBlocker(XC_LoadPackage.LoadPackageParam lpparam) {
        java.util.List<String> matched = scanClassesByContains(lpparam, LISTENER_NEEDLES);
        Log.i(TAG, "[MRD:listener] matched " + matched.size() + " classes");

        for (String fullName : matched) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(fullName);
                int hooked = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    // 合成 / 桥接方法跳过
                    if (m.isSynthetic() || m.isBridge()) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    // 接口实现回调通常 0-2 param
                    if (pt.length > 2) continue;

                    final String mn = m.getName();
                    final Class<?> rt = m.getReturnType();
                    final String shortFull = fullName;
                    StringBuilder ptDesc = new StringBuilder();
                    for (Class<?> p : pt) ptDesc.append(p.getSimpleName()).append(",");
                    if (ptDesc.length() > 0) ptDesc.setLength(ptDesc.length() - 1);

                    Log.i(TAG, "[MRD:listener:hook] " + shortFull + "." + mn
                            + "(" + ptDesc + "):" + rt.getSimpleName());

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 诊断：每个 hook 首次进入时记录，无论模式（验证 hook 在正确方法上）
                            if (sDiagSeen.add("ENTER_" + shortFull + "." + mn)) {
                                Log.i(TAG, "[MRD:listener:enter] " + shortFull + "." + mn
                                        + " called, state=" + StateMachine.getInstance().getStateName());
                            }

                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;

                            // 短路：直接 setResult，不调原方法
                            if (rt == void.class) param.setResult(null);
                            else if (rt == boolean.class) param.setResult(false);
                            else if (rt == int.class) param.setResult(0);
                            else if (rt == long.class) param.setResult(0L);
                            else param.setResult(null);

                            if (sDiagSeen.add("L_" + shortFull + "." + mn)) {
                                Log.i(TAG, "[MRD:listener] " + shortFull + "." + mn + " short-circuit");
                            }
                            InterceptCounter.getInstance().incF05("MRD-listener");
                        }
                    });
                    hooked++;
                }
                Log.i(TAG, "[MRD:listener] " + fullName + " hooked " + hooked);
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:listener] " + fullName + " failed: " + t);
            }
        }
    }

    /**
     * 扫所有 dex entries，返回含任意 needle 子串的类全名。
     */
    private static java.util.List<String> scanClassesByContains(
            XC_LoadPackage.LoadPackageParam lpparam, String[] needles) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        try {
            Object cl = lpparam.classLoader;
            java.lang.reflect.Field pathListField =
                    Class.forName("dalvik.system.BaseDexClassLoader")
                            .getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);
            java.lang.reflect.Field elemsField = pathList.getClass().getDeclaredField("dexElements");
            elemsField.setAccessible(true);
            Object[] elements = (Object[]) elemsField.get(pathList);

            for (Object elem : elements) {
                java.lang.reflect.Field dexFileField = elem.getClass().getDeclaredField("dexFile");
                dexFileField.setAccessible(true);
                Object dexFile = dexFileField.get(elem);
                if (dexFile == null) continue;
                java.lang.reflect.Method entries = dexFile.getClass().getMethod("entries");
                java.util.Enumeration<String> en =
                        (java.util.Enumeration<String>) entries.invoke(dexFile);
                while (en.hasMoreElements()) {
                    String name = en.nextElement();
                    for (String needle : needles) {
                        if (name.contains(needle)) {
                            result.add(name);
                            break;
                        }
                    }
                    if (result.size() > 80) {  // 防爆量
                        Log.w(TAG, "[MRD:scan] >80 matches, stopping");
                        return result;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:scan] failed: " + t);
        }
        return result;
    }

    /**
     * 拦截 RedDot Event 构造 — 精准 wxid 过滤版（非盲拦截）。
     *
     * 策略：
     *   1. 第一次命中时 dump 所有构造参数 + 实例字段，用于确认 wxid 字段名
     *   2. 尝试从构造参数或字段中提取 wxid_xxx
     *   3. 只有 wxid 在密友名单时才阻断；未找到 wxid 字段 → 保守放行（不盲断）
     *   4. 找到字段名后更新 EVENT_WXID_FIELD_CANDIDATES（依 iOS/dump 分析补全）
     *
     * iOS 8.0.71 实证字段：SnsAction.fromUserName（与 SMSG_WXID_FIELDS 统一）
     */
    private static void installEventBlocker(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass(className);
            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object evt = param.thisObject;

                    // 诊断：第一次构造时 dump args + fields（不受 isActive 控制）
                    if (sDiagSeen.add("evt_first_" + className)) {
                        dumpEventCtorInfo(evt, param.args, className);
                    }

                    if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                    if (!StateMachine.getInstance().isActive()) return;

                    // 精准：从事件中提取 wxid
                    String wxid = extractWxidFromEvent(evt, param.args);
                    if (wxid == null) {
                        // wxid 字段尚未确认（待 iOS 分析）→ 保守放行，不盲断
                        if (sDiagSeen.add("evt_no_wxid_" + className)) {
                            Log.w(TAG, "[MRD:evt] " + className
                                    + ": wxid field unknown, NOT blocking (待iOS分析确认字段名)");
                        }
                        return;
                    }

                    if (!Bridge.getInstance().shouldHideId(wxid)) return; // 非密友，放行

                    // 密友事件 → 清空 g 字段阻断渲染
                    try {
                        java.lang.reflect.Field f = evt.getClass().getDeclaredField("g");
                        f.setAccessible(true);
                        f.set(evt, null);
                    } catch (Throwable ignored) {}

                    Log.i(TAG, "[MRD:evt] " + className + " blocked wxid=" + wxid);
                    InterceptCounter.getInstance().incF05("MRD-evt-" + className);
                }
            });
            Log.i(TAG, "[MRD:evt] " + className + " ctor blocker installed (precise)");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:evt] " + className + " hook failed: " + t);
        }
    }

    /**
     * 从 Event 对象（构造参数 + 实例字段）中尝试提取 wxid_xxx。
     * 候选字段名依 iOS 分析结论补充（当前为保守列表）。
     */
    private static final String[] EVENT_WXID_FIELD_CANDIDATES = {
            // iOS 8.0.71 实证（2026-05-21）：SnsAction.fromUserName / WCUserComment.commentUsername
            "fromUserName", "commentUsername",
            "friend_username", "talker",
            "d", "f", "a",                          // 混淆单字母候选
            "userName", "username", "field_userName",
            "src", "from", "commentSrc",
    };

    private static String extractWxidFromEvent(Object evt, Object[] ctorArgs) {
        // 1. 先扫构造参数（最直接）
        if (ctorArgs != null) {
            for (Object arg : ctorArgs) {
                if (arg instanceof String) {
                    String s = (String) arg;
                    if (s.startsWith("wxid_") && s.length() > 5) return s;
                }
            }
        }
        // 2. 扫实例字段（含父类，最多 4 层）
        Class<?> cls = evt.getClass();
        for (int depth = 0; cls != null && cls != Object.class && depth < 4; depth++) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (f.getType() != String.class) continue;
                // 优先检查已知候选字段名
                boolean isCandidate = false;
                for (String c : EVENT_WXID_FIELD_CANDIDATES) {
                    if (c.equals(f.getName())) { isCandidate = true; break; }
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(evt);
                    if (!(v instanceof String)) continue;
                    String s = (String) v;
                    if (s.startsWith("wxid_") && s.length() > 5) {
                        if (sDiagSeen.add("evt_wxid_field_" + f.getName())) {
                            Log.i(TAG, "[MRD:evt] wxid found: field="
                                    + f.getName() + " val=" + s);
                        }
                        return s;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /** 第一次构造时打印 args + 所有字段，用于确认 wxid 字段名 */
    private static void dumpEventCtorInfo(Object evt, Object[] args, String className) {
        StringBuilder sb = new StringBuilder("[MRD:evt:dump] " + className + "\n");
        sb.append("  ctor args (").append(args == null ? 0 : args.length).append("):\n");
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String v = args[i] == null ? "null"
                        : args[i].toString().length() > 60
                                ? args[i].toString().substring(0, 57) + "..."
                                : args[i].toString();
                sb.append("    [").append(i).append("] ")
                  .append(args[i] == null ? "null" : args[i].getClass().getSimpleName())
                  .append(" = ").append(v).append("\n");
            }
        }
        sb.append("  fields:\n");
        Class<?> cls = evt.getClass();
        for (int d = 0; cls != null && cls != Object.class && d < 4; d++) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(evt);
                    String vs = v == null ? "null"
                            : v.toString().length() > 60
                                    ? v.toString().substring(0, 57) + "..."
                                    : v.toString();
                    String marker = (v instanceof String
                            && ((String) v).startsWith("wxid_")) ? " ★WXID" : "";
                    sb.append("    [d").append(d).append("] ")
                      .append(f.getName()).append(" (")
                      .append(f.getType().getSimpleName()).append(") = ")
                      .append(vs).append(marker).append("\n");
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        Log.i(TAG, sb.toString());
    }

    /**
     * FinderRedDotTextView 视图层兜底：
     *   - setRowCount(int) before: 强制 0（无数字红点）
     *   - onFinishInflate() after: setVisibility(GONE)（彻底不显示）
     *   - l(int) before: 强制 0（未知 setter，签名匹配，noop）
     */
    private static void installFinderRedDotViewBlocker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass(CLS_FINDER_RED_DOT_VIEW);
            int hooked = 0;
            for (Method m : cls.getDeclaredMethods()) {
                String mn = m.getName();
                Class<?>[] pt = m.getParameterTypes();

                if ("setRowCount".equals(mn) && pt.length == 1 && pt[0] == int.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            param.args[0] = 0;
                            if (sDiagSeen.add("view_setRowCount"))
                                Log.i(TAG, "[MRD:view] setRowCount → 0");
                            InterceptCounter.getInstance().incF05("MRD-view-rowCount");
                        }
                    });
                    hooked++;
                } else if ("setDropStat".equals(mn) && pt.length == 1) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            param.args[0] = 0;
                            if (sDiagSeen.add("view_setDropStat"))
                                Log.i(TAG, "[MRD:view] setDropStat → 0");
                        }
                    });
                    hooked++;
                } else if ("onFinishInflate".equals(mn) && pt.length == 0) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            try {
                                android.view.View v = (android.view.View) param.thisObject;
                                v.setVisibility(android.view.View.GONE);
                                if (sDiagSeen.add("view_onFinishInflate"))
                                    Log.i(TAG, "[MRD:view] onFinishInflate → GONE");
                                InterceptCounter.getInstance().incF05("MRD-view-gone");
                            } catch (Throwable t) {
                                Log.w(TAG, "[MRD:view] GONE failed: " + t);
                            }
                        }
                    });
                    hooked++;
                } else if ("l".equals(mn) && pt.length == 1 && pt[0] == int.class
                        && m.getReturnType() == void.class) {
                    // l(int):void —— 未知 setter，签名匹配，强制 0（仅 HIDDEN）
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            param.args[0] = 0;
                        }
                    });
                    hooked++;
                }
            }
            Log.i(TAG, "[MRD:view] FinderRedDotTextView hooked " + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:view] hook failed: " + t);
        }
    }

    /**
     * Dump 红点目标类的字段 + 0/1-param 方法，给下一轮 hook 决策用。
     * 不实际 hook，纯诊断。
     */
    private static void dumpRedDotTargetClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        // 第一步：在 ClassLoader 全集里，按 simpleName 后缀匹配找到完整路径
        java.util.HashMap<String, String> simpleToFull = scanFullPathsBySimpleName(lpparam,
                new String[]{"WeChatTabRedDotEvent", "TabRedDotChangeEvent",
                             "FinderRedDotTrigger", "FinderRedDotTextView",
                             "ResetBadgeCountEvent"});

        for (java.util.Map.Entry<String, String> e : simpleToFull.entrySet()) {
            String simpleName = e.getKey();
            String fullName = e.getValue();
            try {
                Class<?> cls = lpparam.classLoader.loadClass(fullName);
                Log.i(TAG, "[MRD:tgt] " + simpleName + " = " + fullName);

                // 字段
                StringBuilder fields = new StringBuilder("[MRD:tgt:fields] " + simpleName + ": ");
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    fields.append(f.getName())
                          .append(":")
                          .append(f.getType().getSimpleName())
                          .append(" ");
                }
                Log.i(TAG, fields.toString());

                // 方法（0/1 参 + bool/int 返回）
                StringBuilder methods = new StringBuilder("[MRD:tgt:methods] " + simpleName + ": ");
                for (Method m : cls.getDeclaredMethods()) {
                    Class<?> ret = m.getReturnType();
                    if (ret != boolean.class && ret != int.class
                            && ret != void.class
                            && ret != Boolean.class && ret != Integer.class) continue;
                    if (m.getParameterTypes().length > 1) continue;
                    methods.append(m.getName())
                           .append("(").append(m.getParameterTypes().length).append(")")
                           .append(":").append(ret.getSimpleName()).append(" ");
                }
                Log.i(TAG, methods.toString());
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:tgt] " + fullName + " failed: " + t);
            }
        }
    }

    private static java.util.HashMap<String, String> scanFullPathsBySimpleName(
            XC_LoadPackage.LoadPackageParam lpparam, String[] targetSimpleNames) {
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        try {
            Object cl = lpparam.classLoader;
            java.lang.reflect.Field pathListField =
                    Class.forName("dalvik.system.BaseDexClassLoader")
                            .getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);
            java.lang.reflect.Field elemsField = pathList.getClass().getDeclaredField("dexElements");
            elemsField.setAccessible(true);
            Object[] elements = (Object[]) elemsField.get(pathList);

            for (Object elem : elements) {
                java.lang.reflect.Field dexFileField = elem.getClass().getDeclaredField("dexFile");
                dexFileField.setAccessible(true);
                Object dexFile = dexFileField.get(elem);
                if (dexFile == null) continue;
                java.lang.reflect.Method entries = dexFile.getClass().getMethod("entries");
                java.util.Enumeration<String> en =
                        (java.util.Enumeration<String>) entries.invoke(dexFile);
                while (en.hasMoreElements()) {
                    String name = en.nextElement();
                    for (String target : targetSimpleNames) {
                        if (result.containsKey(target)) continue;
                        if (name.endsWith("." + target) || name.equals(target)) {
                            result.put(target, name);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:scan] failed: " + t);
        }
        return result;
    }

    /**
     * 一次性扫描 BaseDexClassLoader 内部 DexFile，
     * 找出含 SnsComment / RedDot / EventCenter / FindMoreFriends 关键字的类。
     * 这是 Frida enumerateLoadedClasses 的 Xposed 替代。
     */
    private static void dumpCandidateClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Object cl = lpparam.classLoader;
            // BaseDexClassLoader 内部链：pathList → dexElements[] → dexFile.entries()
            java.lang.reflect.Field pathListField =
                    Class.forName("dalvik.system.BaseDexClassLoader")
                            .getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);

            java.lang.reflect.Field elemsField = pathList.getClass().getDeclaredField("dexElements");
            elemsField.setAccessible(true);
            Object[] elements = (Object[]) elemsField.get(pathList);

            String[] needles = {"SnsComment", "RedDot", "EventCenter", "FindMoreFriends",
                                "NotifyTabTips", "ResetBadgeCount", "IListener"};
            int total = 0;
            int matched = 0;
            for (Object elem : elements) {
                java.lang.reflect.Field dexFileField = elem.getClass().getDeclaredField("dexFile");
                dexFileField.setAccessible(true);
                Object dexFile = dexFileField.get(elem);
                if (dexFile == null) continue;

                java.lang.reflect.Method entries = dexFile.getClass().getMethod("entries");
                java.util.Enumeration<String> en =
                        (java.util.Enumeration<String>) entries.invoke(dexFile);
                while (en.hasMoreElements()) {
                    String name = en.nextElement();
                    total++;
                    for (String needle : needles) {
                        if (name.contains(needle)) {
                            Log.i(TAG, "[MRD:dump] " + needle + " ⊃ " + name);
                            matched++;
                            break;
                        }
                    }
                    if (matched > 200) {  // 防爆量
                        Log.w(TAG, "[MRD:dump] >200 matches, stopping early");
                        return;
                    }
                }
            }
            Log.i(TAG, "[MRD:dump] scanned " + total + " classes, matched " + matched);
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:dump] failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // v7 主路径：hook bm.getCount() → HIDDEN 态返回 0
    //   bm = SnsMsgUI 的 RecyclerView Adapter（ContactFilter 已实证）
    // -------------------------------------------------------------------------
    private static void installAdapterGetCountBlocker(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] adapterClasses = {
                "com.tencent.mm.plugin.sns.ui.rm",
                "com.tencent.mm.plugin.sns.ui.bm",
        };
        for (String adapterClass : adapterClasses) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(adapterClass);
                // hook 构造器 → dump adapter 内部数据源
                XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "[MRD:adapter:ctor] " + adapterClass + " created");
                        dumpObjectFields(param.thisObject, "adapter", 2);
                    }
                });
                // hook getItem(int) → dump 每个 item 的字段和 wxid
                for (Method m : cls.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    String mn = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    // getItem / get / getData 等 (int) 返回 Object
                    if (pt.length == 1 && pt[0] == int.class) {
                        final String fmn = mn;
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object result = param.getResult();
                                if (result == null) return;
                                if (sDiagSeen.add("adapter_" + fmn + "_" + result.getClass().getName())) {
                                    Log.i(TAG, "[MRD:adapter:" + fmn + "] pos=" + param.args[0]
                                            + " → " + result.getClass().getName());
                                    dumpObjectFields(result, "item", 3);
                                }
                            }
                        });
                    }
                }
                // getCount：Catfish 不做盲归零，只记录；计数由 blacklist 注入 + 列表过滤承担
                for (Method m : cls.getMethods()) {
                    if (!"getCount".equals(m.getName())) continue;
                    if (m.getParameterTypes().length != 0) continue;
                    if (m.getReturnType() != int.class) continue;
                    if (m.getDeclaringClass() == Object.class) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sDiagSeen.add("adapter_getCount_" + adapterClass)) {
                                Log.i(TAG, "[MRD:adapter] getCount=" + param.getResult()
                                        + " state=" + StateMachine.getInstance().getStateName());
                            }
                        }
                    });
                }
                // getItem：Catfish 列表项过滤 — 命中密友 wxid 打日志（下一轮 setResult 待探针确认 item 类）
                for (Method m : cls.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    String mn = m.getName();
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length != 1 || pt[0] != int.class) continue;
                    if (m.getReturnType() == void.class) continue;
                    if (!"getItem".equals(mn) && !"get".equals(mn) && !"l".equals(mn)) continue;
                    final String fmn = mn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!shouldCatfishSnsMsgFilter()) return;
                            Object result = param.getResult();
                            if (result == null) return;
                            String wxid = extractWxidFromSmsgItem(result);
                            if (wxid == null || !Bridge.getInstance().shouldHideId(wxid)) return;
                            Log.i(TAG, "[MRD:adapter:" + fmn + "] hidden wxid=" + wxid
                                    + " item=" + result.getClass().getName());
                            InterceptCounter.getInstance().incF05("MRD-adapter-item");
                        }
                    });
                }
                Log.i(TAG, "[MRD:adapter] " + adapterClass + " probed");
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:adapter] " + adapterClass + " failed: " + t);
            }
        }
    }

    // -------------------------------------------------------------------------
    // v24: 互动列表显示层真源过滤
    //   bm(全部互动) / rm(与我的互动) 适配器内部的 c(LinkedList) 才是屏幕上那几十行数据
    //   (w1.N1/O1/a2 是小查询, 非显示源)。
    //   做法: hook BaseAdapter.notifyDataSetChanged, 仅对 bm/rm 生效 —— 通知前先把密友项
    //   从适配器内部 List 删掉, 随后微信自己的 notify 让 ListView 按过滤后的 c 重画。
    //   合规: clear-before-notify(铁律#21); 不自调 notify(避#17 SIGSEGV); 不 hook getCount/getItem(避#14);
    //         按类名精确门控, 非 bm/rm 立即返回(低开销)。
    // -------------------------------------------------------------------------
    private static void installSnsMsgAdapterFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        final java.util.Set<String> targets = new java.util.HashSet<>(java.util.Arrays.asList(
                "com.tencent.mm.plugin.sns.ui.bm", "com.tencent.mm.plugin.sns.ui.rm"));
        try {
            XposedHelpers.findAndHookMethod("android.widget.BaseAdapter", lpparam.classLoader,
                    "notifyDataSetChanged", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String cn = param.thisObject.getClass().getName();
                        if (!targets.contains(cn)) return;
                        if (sDiagSeen.add("adapter_notify_seen_" + cn))
                            Log.i(TAG, "[MRD:adapter:flt] notify fired on " + cn);
                        if (!isFilteringActive()) return;
                        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
                        if (hidden.isEmpty()) return;
                        int cursorRemoved = wrapSnsMsgCursorFields(param.thisObject, "notify:" + cn);
                        int removed = filterListFields(param.thisObject, hidden);
                        int total = cursorRemoved + removed;
                        if (total > 0) {
                            Log.i(TAG, "[MRD:adapter:flt] " + cn + " removed=" + total
                                    + " (cursor=" + cursorRemoved + ", list=" + removed + ", before notify)");
                            InterceptCounter.getInstance().incF05("MRD-adapter-flt");
                        } else if (sDiagSeen.add("adapter_struct_" + cn)) {
                            Log.i(TAG, "[MRD:dump] " + cn + " removed=0 — dumping list/item structure:");
                            dumpSmsgAdapterStructure(param.thisObject);
                        }
                    } catch (Throwable t) {
                        if (sDiagSeen.add("adapterflt_err")) Log.w(TAG, "[MRD:adapter:flt] err: " + t);
                    }
                }
            });
            Log.i(TAG, "[MRD:adapter:flt] BaseAdapter.notifyDataSetChanged hook installed (gated bm/rm)");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:adapter:flt] install failed: " + t);
        }
    }

    // v24d 一次性诊断：removed=0 时把适配器的 List 字段 + 第一条 item 的字段全打出来，找 wxid 真路径。
    private static void dumpSmsgAdapterStructure(Object adapter) {
        try {
            Class<?> cls = adapter.getClass();
            for (int d = 0; cls != null && d < 5; d++) {
                if (cls.getName().startsWith("android.")) break;
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object lv = f.get(adapter);
                    if (!(lv instanceof java.util.List)) continue;
                    java.util.List<?> list = (java.util.List<?>) lv;
                    Log.i(TAG, "[MRD:dump] List '" + f.getName() + "':" + f.getType().getSimpleName()
                            + " size=" + list.size());
                    Object item = null;
                    for (Object o : list) { if (o != null) { item = o; break; } }
                    if (item == null) continue;
                    Log.i(TAG, "[MRD:dump]   item0 = " + item.getClass().getName());
                    dumpItemFieldsForWxid(item, "      ", 0);
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) { Log.w(TAG, "[MRD:dump] err " + t); }
    }

    private static void dumpItemFieldsForWxid(Object item, String indent, int depth) {
        if (item == null || depth > 1) return;
        Class<?> ic = item.getClass();
        for (int d = 0; ic != null && d < 4; d++) {
            String icn = ic.getName();
            if (icn.startsWith("android.") || icn.startsWith("java.")) break;
            for (java.lang.reflect.Field f : ic.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v == null) continue;
                    String tn = f.getType().getSimpleName();
                    if (v instanceof String) {
                        String s = (String) v;
                        Log.i(TAG, indent + f.getName() + ":" + tn + " = \""
                                + (s.length() > 48 ? s.substring(0, 48) : s) + "\"");
                    } else if (f.getType().isPrimitive()) {
                        Log.i(TAG, indent + f.getName() + ":" + tn + " = " + v);
                    } else {
                        String vcn = v.getClass().getName();
                        if (!vcn.startsWith("java.") && !vcn.startsWith("android.") && depth < 1) {
                            Log.i(TAG, indent + f.getName() + ":" + tn + " = [" + vcn + "] ↓");
                            dumpItemFieldsForWxid(v, indent + "    ", depth + 1);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            ic = ic.getSuperclass();
        }
    }

    /**
     * boot-time 追溯清零（仅 HIDDEN 态）。
     *
     * 两条清零：
     *   1. ns.c.b（新帖红点）— 静态字段，可同步清零
     *   2. w1.y（互动计数，jadx: f178674y）— 实例字段，通过挂 E1() once-hook 捕获实例后清零
     *      iOS 实证：StatusAffManager.getToNotifyCount ↔ w1.E1()，底层字段 = y
     *      v1 策略：激进清零（非密友的互动红点也清，重开发现 tab 后 L1() 恢复 → 可接受）
     */
    private static void retroactiveZeroOnBoot(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
        if (!StateMachine.getInstance().isActive()) return;

        // ── 1. ns.c.b（新帖红点，静态字段） ─────────────────────────────────
        try {
            Class<?> nsC = lpparam.classLoader.loadClass("ns.c");
            java.lang.reflect.Field b = nsC.getDeclaredField("b");
            b.setAccessible(true);
            boolean current = b.getBoolean(null);
            if (current) {
                b.setBoolean(null, false);
                Log.i(TAG, "[MRD:boot] ns.c.b true → false");
                try {
                    Class<?> ww2C = lpparam.classLoader.loadClass("ww2.c");
                    java.lang.reflect.Field wb = ww2C.getDeclaredField("b");
                    wb.setAccessible(true);
                    wb.setBoolean(null, false);
                } catch (Throwable ignored) {}
                InterceptCounter.getInstance().incF05("MRD-boot-nsc");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:boot] ns.c.b zero failed: " + t);
        }

        // ── 2. w1.y（互动计数，jadx: f178674y）────────────────────────────────
        // 实例字段，boot 时单例未必就绪。
        // 触发时机改为 L1() after-hook 首次触发（L1 内部先调 E1 → sW1Instance 已就绪）
        // 见 installNsCAggregationBlocker → doW1RetroactiveZero()
        Log.i(TAG, "[MRD:boot:w1] zero deferred to first L1() trigger");
    }

    /**
     * 首次 L1() after-hook 触发时调用：清零 w1.y（互动计数，jadx: f178674y）。
     * 前提：sW1Instance 已由 E1 cache-hook 填充（L1 内部会先调 E1，顺序有保证）。
     */
    private static void doW1RetroactiveZero() {
        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
        if (!StateMachine.getInstance().isActive()) return;
        if (Bridge.getInstance().getWxids().isEmpty()) return;

        Object inst = sW1Instance;
        if (inst == null) {
            Log.w(TAG, "[MRD:boot:w1] sW1Instance null at L1 trigger — E1 cache not fired yet?");
            return;
        }
        zeroW1FieldY(inst);
    }

    /**
     * 反射置零 w1 实例上的字段 "y"（DEX 真实名，jadx: f178674y）。
     * iOS 实证：StatusAffManager 底层计数字段 ↔ E1()/getToNotifyCount。
     * 激进策略（v1）：有密友名单就清零，含非密友互动；进发现 tab 后 L1 会从 DB 恢复非密友部分。
     */
    /**
     * 反射置零 w1 实例上的 unread 计数字段。
     * 8.0.71 实证：w1 直接字段为 d(i0)/e(boolean)/f(String[])，无 int 字段 y。
     *   → 策略改为：扫所有层 int/long 字段，值 > 0 即清零（不限字段名）。
     *   → 兜底：若目标是 w1，也尝试进入 d(i0) 查找 int 计数。
     */
    private static void zeroW1FieldY(Object inst) {
        if (inst == null) return;
        boolean zeroed = false;
        Class<?> cls = inst.getClass();
        for (int depth = 0; cls != null && cls != Object.class && depth < 4; depth++) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (f.getType() != int.class && f.getType() != long.class) continue;
                try {
                    f.setAccessible(true);
                    long before = f.getLong(inst);
                    if (before > 0) {
                        f.setLong(inst, 0L);
                        Log.i(TAG, "[MRD:w1:zero] " + inst.getClass().getSimpleName()
                                + "." + f.getName() + " " + before + "→0");
                        InterceptCounter.getInstance().incF05("MRD-boot-w1");
                        zeroed = true;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        // 8.0.71: w1.d 是包装对象(i0)，递归一层查 int 字段
        if (!zeroed) {
            try {
                java.lang.reflect.Field dField = inst.getClass().getDeclaredField("d");
                dField.setAccessible(true);
                Object inner = dField.get(inst);
                if (inner != null) {
                    Class<?> ic = inner.getClass();
                    for (java.lang.reflect.Field f : ic.getDeclaredFields()) {
                        if (f.getType() != int.class && f.getType() != long.class) continue;
                        try {
                            f.setAccessible(true);
                            long v = f.getLong(inner);
                            if (v > 0) {
                                f.setLong(inner, 0L);
                                Log.i(TAG, "[MRD:w1:zero] w1.d." + f.getName() + " " + v + "→0");
                                zeroed = true;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored2) {}
        }
        if (!zeroed && sDiagSeen.add("w1_zero_noop")) {
            Log.w(TAG, "[MRD:w1:zero] no int/long field found on "
                    + inst.getClass().getName() + " (no-op)");
        }
    }

    // -------------------------------------------------------------------------
    // v9 主路径：FindMoreFriendsUI.L1() after → 精准密友判断 → 有选择地压 ns.c.b
    //
    //   jadx 静态分析结论（2026-05-21，8.0.71）：
    //   • this.x  (field "x") = newer snsobj = 密友新帖的 wxid（1491行作头像用，已确认）
    //   • this.y  (field "y") = SnsCommentStorage.E1() = 自己帖子的评论/赞未读数（与密友无关）
    //   • z19 = (!empty(x) || y!=0)  → 写入 ns.c.b (1518) 和 ww2.c.b (1517)
    //   • 只压 ns.c.b / ww2.c.b，g 不动（g=y=自己未读，不该屏蔽）
    //   • 同时回调 g1("album_dyna_photo_ui_title", newZ19) 刷新页内指示器
    // -------------------------------------------------------------------------
    private static void installNsCAggregationBlocker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> nsC = lpparam.classLoader.loadClass("ns.c");
            dumpNsCFields(nsC, "init");

            Class<?> ww2C = null;
            try { ww2C = lpparam.classLoader.loadClass("ww2.c"); }
            catch (Throwable t) { Log.w(TAG, "[MRD:ns.c] ww2.c not found (non-fatal): " + t); }
            final Class<?> finalWw2C = ww2C;

            // fmfClass 提升到外层作用域（供 LauncherUI.onResume 里 findFMFFromLauncher 使用）
            Class<?> fmfClassRef = null;
            try { fmfClassRef = lpparam.classLoader.loadClass("com.tencent.mm.ui.FindMoreFriendsUI"); }
            catch (Throwable t) { Log.w(TAG, "[MRD:ns.c] FMF class not found: " + t); }
            final Class<?> finalFmfClass = fmfClassRef;

            // hook FindMoreFriendsUI.L1() + onResume → 精准密友判断 + 主动刷新
            try {
                Class<?> fmfUi = lpparam.classLoader.loadClass(
                        "com.tencent.mm.ui.FindMoreFriendsUI");
                int fmfHooked = 0;
                for (Method m : fmfUi.getDeclaredMethods()) {
                    if ("L1".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                // 缓存 FMF 实例（供 LauncherUI.onResume 使用）
                                if (sFMFInstance == null) sFMFInstance = param.thisObject;
                                if (sDiagSeen.add("w1_boot_zero_l1_fired")) {
                                    doW1RetroactiveZero();
                                }
                                suppressRedDotIfHiddenFriend(param.thisObject, nsC, finalWw2C);
                            }
                        });
                        Log.i(TAG, "[MRD:ns.c] FindMoreFriendsUI.L1() hooked (v9 precise)");
                        fmfHooked++;
                    }
                }
                // v16: FMF 是 Fragment，不是 Activity → 直接 hookAllMethods(fmfUi, "onResume")
                try {
                    final String fmfCn = "com.tencent.mm.ui.FindMoreFriendsUI";
                    Class<?> fmfUiForResume = lpparam.classLoader.loadClass(fmfCn);
                    XposedBridge.hookAllMethods(fmfUiForResume, "onResume",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    try {
                                        if (sFMFInstance == null) sFMFInstance = param.thisObject;
                                        boolean en2 = AppConfig.getInstance().isMomentsRedDotEnabled();
                                        boolean act2 = StateMachine.getInstance().isActive();
                                        int wxN2 = Bridge.getInstance().getWxids().size();
                                        Log.i(TAG, "[MRD:fmf:onResume] en=" + en2 + " act=" + act2
                                                + " wxids=" + wxN2 + " pending=" + sPendingClearBadge);
                                        if (sPendingClearBadge && en2 && act2 && wxN2 > 0) {
                                            sPendingClearBadge = false;
                                            clearFMFBadgeIfNeeded(param.thisObject, "fmf-pending");
                                        }
                                        if (!en2 || !act2 || wxN2 == 0) return;
                                        suppressRedDotIfHiddenFriend(param.thisObject, nsC, finalWw2C);
                                    } catch (Throwable inner) {
                                        Log.w(TAG, "[MRD:fmf:onResume] ex: " + inner);
                                    }
                                }
                            });
                    Log.i(TAG, "[MRD:ns.c] FindMoreFriendsUI.onResume() hooked (v16 Fragment direct)");
                    fmfHooked++;
                } catch (Throwable t2) {
                    Log.w(TAG, "[MRD:ns.c] FMF.onResume hook failed: " + t2);
                }
                if (fmfHooked == 0) Log.w(TAG, "[MRD:ns.c] FindMoreFriendsUI: L1 not found");
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:ns.c] L1/onResume hook failed: " + t);
            }

            // v14d: Activity.onResume hookAllMethods（与 UiContextTracker 同款，保证触发）
            // findAndHookMethod 在 LauncherUI 无自身 onResume 时 hook 中间类可能条件失败，
            // hookAllMethods(Activity.class, onResume) 是唯一已实证能在 LauncherUI 触发的写法。
            try {
                final Class<?> fFmfUi = finalFmfClass;
                final String launcherCn = "com.tencent.mm.ui.LauncherUI";
                XposedBridge.hookAllMethods(android.app.Activity.class, "onResume",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                // 只处理 LauncherUI 进入前台
                                if (!launcherCn.equals(
                                        param.thisObject.getClass().getName())) return;
                                try {
                                    boolean en = AppConfig.getInstance().isMomentsRedDotEnabled();
                                    boolean act = StateMachine.getInstance().isActive();
                                    int wxN = Bridge.getInstance().getWxids().size();
                                    Object fmfSnap = sFMFInstance;
                                    String ts0 = new java.text.SimpleDateFormat(
                                            "HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date());
                                    String entryLine = ts0 + " [MRD:launcher:entry] en=" + en
                                            + " act=" + act + " wxids=" + wxN
                                            + " fmf=" + (fmfSnap != null ? "ready" : "null");
                                    Bridge.getInstance().addRawFeedLine(entryLine);
                                    Log.i(TAG, "[MRD:launcher:entry] en=" + en + " act=" + act
                                            + " wxids=" + wxN + " fmf="
                                            + (fmfSnap != null ? "ready" : "null"));
                                    if (!en || !act || wxN == 0) return;
                                    Object fmf = sFMFInstance;
                                    if (fmf != null) {
                                        clearFMFBadgeIfNeeded(fmf, "launcher-immediate");
                                        return;
                                    }
                                    // FMF 懒加载未就绪 → 每 300ms 重试一次，最多 15 次（4.5s）
                                    // WeChat 初始化完成后约 1s 会自己把红点重新设上，
                                    // 必须在 FMF 出现且 E=true 时立刻清。
                                    sPendingClearBadge = true;
                                    Log.i(TAG, "[MRD:launcher] fmf null, pending=true, starting retry loop");
                                    final Object launcherInst = param.thisObject;
                                    final android.os.Handler h2 = new android.os.Handler(
                                            android.os.Looper.getMainLooper());
                                    final int[] attempts = {0};
                                    final int MAX_ATTEMPTS = 15;
                                    final Runnable[] retryRef = {null};
                                    retryRef[0] = new Runnable() {
                                        @Override public void run() {
                                            if (!sPendingClearBadge) return; // 已被消费
                                            if (attempts[0]++ >= MAX_ATTEMPTS) {
                                                Log.w(TAG, "[MRD:retry] gave up after " + MAX_ATTEMPTS + " attempts");
                                                return;
                                            }
                                            Object fmf2 = sFMFInstance;
                                            if (fmf2 == null) {
                                                fmf2 = findFMFFromLauncher(launcherInst, fFmfUi);
                                                if (fmf2 != null) sFMFInstance = fmf2;
                                            }
                                            if (fmf2 != null) {
                                                boolean fmfE = getBooleanFieldOnInstance(fmf2, "E");
                                                String tsR = new java.text.SimpleDateFormat(
                                                        "HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date());
                                                Bridge.getInstance().addRawFeedLine(
                                                        tsR + " [MRD:retry#" + attempts[0] + "] fmfE=" + fmfE);
                                                Log.i(TAG, "[MRD:retry#" + attempts[0] + "] fmfE=" + fmfE);
                                                if (fmfE) {
                                                    sPendingClearBadge = false;
                                                    clearFMFBadgeIfNeeded(fmf2, "retry-" + attempts[0]);
                                                    return;
                                                }
                                                // FMF 存在但 E=false，红点还没设 → 继续等
                                            }
                                            h2.postDelayed(retryRef[0], 300);
                                        }
                                    };
                                    h2.postDelayed(retryRef[0], 300);
                                } catch (Throwable inner) {
                                    Log.w(TAG, "[MRD:launcher:entry] ex: " + inner);
                                }
                            }
                        });
                Log.i(TAG, "[MRD:ns.c] LauncherUI.onResume hooked via Activity hookAllMethods (v14d)");
            } catch (Throwable t2) {
                Log.w(TAG, "[MRD:ns.c] LauncherUI.onResume hook failed: " + t2);
            }

            Log.i(TAG, "[MRD:ns.c] aggregation blocker v12b installed");

            // ── v14f: hook FMF.g1(String,boolean) 本体 ──────────────────────────────
            // 以前我们靠"找到 FMF 实例再调 g1(false)"来清除红点，
            // 但 FMF 懒加载导致实例永远找不到。
            // 正确做法：直接 hook g1() 入口，当 show=true 且 key=album 时直接 setResult(null) 跳过。
            // 这样无论谁、何时调 g1(true)，都被我们拦在门口。
            try {
                final String FMF_CN = "com.tencent.mm.ui.FindMoreFriendsUI";
                final String BADGE_KEY = "album_dyna_photo_ui_title";
                Class<?> fmfClz = lpparam.classLoader.loadClass(FMF_CN);
                int g1Hooked = 0;
                for (java.lang.reflect.Method m : fmfClz.getDeclaredMethods()) {
                    if (!"g1".equals(m.getName())) continue;
                    java.lang.reflect.Parameter[] params = m.getParameters();
                    if (params.length != 2) continue;
                    if (!params[0].getType().equals(String.class)) continue;
                    if (!params[1].getType().equals(boolean.class)) continue;
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                String key  = (String) param.args[0];
                                boolean show = (Boolean) param.args[1];
                                // 记入 rawfeed 实时可见
                                String ts = new java.text.SimpleDateFormat(
                                        "HH:mm:ss.SSS", java.util.Locale.US)
                                        .format(new java.util.Date());
                                boolean fmfE = getBooleanFieldOnInstance(param.thisObject, "E");
                                com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                        ts + " [MRD:g1] key=" + key + " show=" + show + " fmfE=" + fmfE);
                                Log.i(TAG, "[MRD:g1] key=" + key + " show=" + show + " fmfE=" + fmfE);
                                if (!BADGE_KEY.equals(key) || !show) return;
                                boolean en = AppConfig.getInstance().isMomentsRedDotEnabled();
                                boolean act = StateMachine.getInstance().isActive();
                                int wxN = Bridge.getInstance().getWxids().size();
                                Log.i(TAG, "[MRD:g1:intercept] en=" + en + " act=" + act + " wxids=" + wxN);
                                com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                        ts + " [MRD:g1:intercept] en=" + en + " act=" + act + " wxids=" + wxN);
                                if (!en || !act || wxN == 0) return;
                                // 拦截：跳过 show=true，直接 suppress
                                param.setResult(null);
                                sPendingClearBadge = false;
                                InterceptCounter.getInstance().incF05("MRD-g1-block");
                                Log.i(TAG, "[MRD:g1:intercept] BLOCKED g1(" + key + ",true)");
                                com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                        ts + " [MRD:g1:BLOCKED] key=" + key);
                                com.ghost.assist.debug.DebugTelemetry.getInstance().addBlocked("badge");
                                com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                                        "badge", "g1-blocked",
                                        com.ghost.assist.debug.DebugTelemetry.fields(
                                                "key", key, "wxids", String.valueOf(wxN)));
                            } catch (Throwable inner) {
                                Log.w(TAG, "[MRD:g1] ex: " + inner);
                            }
                        }
                    });
                    Log.i(TAG, "[MRD:g1] hooked FMF.g1(String,boolean)");
                    g1Hooked++;
                    break;
                }
                if (g1Hooked == 0) Log.w(TAG, "[MRD:g1] g1 method not found in FMF");
            } catch (Throwable tg1) {
                Log.w(TAG, "[MRD:g1] hook failed: " + tg1);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:ns.c] ns.c not found: " + t);
        }
    }

    /**
     * v13 红点压制逻辑（修复 8.0.71 实证：ns.c.b 不控制视觉红点，必须调 g1()）
     *
     * 8.0.71 真正控制字段（find_reddot_field.js 实证 2026-05-21）：
     *   FMF.E = true               ← FindMoreFriendsUI 实例字段
     *   AbstractTabChildPreference.m/p = true ← 父类字段
     *   视觉刷新：必须调 g1("album_dyna_photo_ui_title", false)
     *
     * 精准路径（this.x 有值）：
     *   - this.x = hidden friend wxid → 调 g1(false)（当 y==0 时压掉新帖红点）
     *   - this.x = non-hidden friend wxid → 透传，不动
     *
     * 冷启动/x 为空路径（v1 策略）：
     *   - FMF.E=true + this.x 为空 + this.y==0 → 上次 session 遗留新帖红点 → 激进清除
     *   - FMF.E=true + this.x 为空 + this.y>0  → 互动红点（有人赞/评论你的帖）→ 保留
     */
    private static void suppressRedDotIfHiddenFriend(Object ui, Class<?> nsC, Class<?> ww2C) {
        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
        if (!StateMachine.getInstance().isActive()) return;
        if (Bridge.getInstance().getWxids().isEmpty()) return;

        try {
            // ── 读 FMF.E（真正的视觉红点标志） ────────────────────────────
            boolean fmfE = getBooleanFieldOnInstance(ui, "E");
            if (!fmfE) {
                // 红点本来就没亮，不需要处理
                return;
            }

            // ── 读 this.x（新帖 wxid）────────────────────────────────────
            String newSnsWxid = getStringField(ui, "x");
            int commentCount = getIntField(ui, "y");

            Log.i(TAG, "[MRD:v13] fmfE=" + fmfE + " x=" + newSnsWxid + " y=" + commentCount);
            if (!android.text.TextUtils.isEmpty(newSnsWxid)) {
                // ── 精准路径：有明确 wxid ────────────────────────────────
                if (!Bridge.getInstance().shouldHideId(newSnsWxid)) {
                    // 非密友的新帖，红点应保留
                    Log.i(TAG, "[MRD:v13] 非密友 x=" + newSnsWxid + " 保留红点");
                    return;
                }
                // 密友新帖 → 清除（y 是新帖总数，不用做 guard）
                callG1OnUi(ui, false);
                if (sDiagSeen.add("v13_precise_" + newSnsWxid)) {
                    Log.i(TAG, "[MRD:v13] 精准压制: wxid=" + newSnsWxid + " y=" + commentCount);
                }
                InterceptCounter.getInstance().incF05("MRD-v13-precise");
            } else {
                // ── 冷启动/x 为空路径 ────────────────────────────────────
                // FMF.E=true 但 this.x 为空：激进清除（v1）
                callG1OnUi(ui, false);
                if (sDiagSeen.add("v13_cold_clear")) {
                    Log.i(TAG, "[MRD:v13] 冷启动激进清除: x=empty y=" + commentCount + " E=true");
                }
                InterceptCounter.getInstance().incF05("MRD-v13-cold");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:v13] suppress failed: " + t);
        }
    }

    /**
     * 当 FMF.E == true 时调 g1(false) 清红点。
     * 供 LauncherUI.onResume 内（立即 + 延迟）调用。
     *
     * 注意：FMF.y = 新帖总数（不是互动数），不能用 y>0 做 guard。
     * v1 策略：隐藏模式下、有密友列表、FMF.E=true → 直接清除（激进）。
     * 精准过滤由 L1() afterHook 里的 suppressRedDotIfHiddenFriend() 负责。
     */
    private static void clearFMFBadgeIfNeeded(Object fmf, String tag) {
        try {
            boolean fmfE = getBooleanFieldOnInstance(fmf, "E");
            int y = getIntField(fmf, "y");
            String x = getStringField(fmf, "x");
            Log.i(TAG, "[MRD:" + tag + "] FMF.E=" + fmfE + " x=" + x + " y=" + y);
            if (!fmfE) {
                com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                        "badge", "clear-skip",
                        com.ghost.assist.debug.DebugTelemetry.fields("tag", tag, "fmfE", "false"));
                return; // 红点没亮，不需要处理
            }
            // 直接清除（激进 v1）
            callG1OnUi(fmf, false);
            if (sDiagSeen.add("fmf_badge_cleared_" + tag)) {
                Log.i(TAG, "[MRD:" + tag + "] FMF.E=true → g1(false) called (y=" + y + " x=" + x + ")");
            }
            InterceptCounter.getInstance().incF05("MRD-" + tag);
            com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                    "badge", "g1-cleared",
                    com.ghost.assist.debug.DebugTelemetry.fields("tag", tag, "y", String.valueOf(y), "x", x != null ? x : ""));
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:" + tag + "] clearFMFBadgeIfNeeded failed: " + t);
        }
    }

    /**
     * 从 LauncherUI 实例字段中扫描 FindMoreFriendsUI 实例（最后手段）。
     * LauncherUI 持有各 tab fragment/activity 引用，FMF 必然在其字段里。
     */
    private static Object findFMFFromLauncher(Object launcher, Class<?> fmfClass) {
        if (launcher == null || fmfClass == null) return null;
        for (Class<?> c = launcher.getClass();
                c != null && !c.getName().equals("java.lang.Object");
                c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!fmfClass.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(launcher);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** 读实例上的 boolean 字段（遍历继承链） */
    private static boolean getBooleanFieldOnInstance(Object obj, String fieldName) {
        if (obj == null) return false;
        for (Class<?> c = obj.getClass();
                c != null && !c.getName().equals("java.lang.Object");
                c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                if (f.getType() != boolean.class && f.getType() != Boolean.class) continue;
                f.setAccessible(true);
                Object v = f.get(obj);
                return Boolean.TRUE.equals(v) || (v instanceof Boolean && (Boolean) v);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return false; }
        }
        return false;
    }

    /**
     * 反射调用 FindMoreFriendsUI.g1(String, boolean) 触发视觉红点刷新。
     * key = "album_dyna_photo_ui_title"（朋友圈 tab 红点路由 key，jadx 8.0.71 确认）
     *
     * 8.0.71 实证：ns.c.b 不控制视觉红点，必须调 g1() 才能让 LauncherUI tab bar 刷新。
     * 修复：遍历整个继承链（原代码只搜 getDeclaredMethods，g1 可能在父类）。
     */
    private static void callG1OnUi(Object ui, boolean show) {
        if (ui == null) return;
        try {
            for (Class<?> c = ui.getClass();
                    c != null && !c.getName().equals("java.lang.Object");
                    c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (!"g1".equals(m.getName())) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 2 && pt[0] == String.class && pt[1] == boolean.class) {
                        m.setAccessible(true);
                        boolean eBefore = getBooleanFieldOnInstance(ui, "E");
                        m.invoke(ui, "album_dyna_photo_ui_title", show);
                        boolean eAfter = getBooleanFieldOnInstance(ui, "E");
                        String tsG = new java.text.SimpleDateFormat(
                                "HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date());
                        com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                tsG + " [MRD:g1] key=album_dyna_photo_ui_title show=" + show
                                        + " fmfE_before=" + eBefore + " fmfE_after=" + eAfter);
                        Log.i(TAG, "[MRD:g1] g1(album_dyna_photo_ui_title, " + show
                                + ") fmfE " + eBefore + "→" + eAfter + " on " + c.getSimpleName());
                        return;
                    }
                }
            }
            if (sDiagSeen.add("g1_not_found")) {
                Log.w(TAG, "[MRD:g1] g1(String,boolean) not found in FMF hierarchy");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:g1] call failed: " + t);
        }
    }

    private static String getStringField(Object obj, String fieldName) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(obj);
                return (v instanceof String) ? (String) v : null;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return null; }
        }
        return null;
    }

    private static int getIntField(Object obj, String fieldName) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return 0; }
        }
        return 0;
    }

    private static void dumpNsCFields(Class<?> nsC, String tag) {
        try {
            StringBuilder sb = new StringBuilder("[MRD:ns.c:" + tag + "] ");
            for (java.lang.reflect.Field f : nsC.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object v = f.get(null);
                    sb.append(f.getName()).append("=").append(v).append(" ");
                } catch (Throwable ignored) {}
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:ns.c] dump failed: " + t);
        }
    }

    private static void dumpObjectFields(Object obj, String tag, int maxDepth) {
        if (obj == null) return;
        StringBuilder sb = new StringBuilder("[MRD:" + tag + "] " + obj.getClass().getName() + ":\n");
        for (Class<?> c = obj.getClass(); c != null && c != Object.class
                && !c.getName().startsWith("android."); c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;
                    String desc;
                    if (v instanceof String) {
                        String s = (String) v;
                        desc = s.length() > 60 ? s.substring(0, 57) + "..." : s;
                        // 标记 wxid
                        if (s.startsWith("wxid_") || s.endsWith("@chatroom"))
                            desc = "★WXID:" + s;
                    } else if (v instanceof java.util.List) {
                        java.util.List<?> l = (java.util.List<?>) v;
                        desc = "List(" + l.size() + ")";
                        if (!l.isEmpty()) desc += "[" + l.get(0).getClass().getName() + "]";
                    } else if (v instanceof Number || v instanceof Boolean) {
                        desc = v.toString();
                    } else {
                        desc = v.getClass().getSimpleName();
                    }
                    sb.append("  ").append(c.getSimpleName()).append(".")
                      .append(f.getName()).append(" = ").append(desc).append("\n");
                } catch (Throwable ignored) {}
            }
        }
        Log.i(TAG, sb.toString());
    }
    /**
     * v10 — w1 (SnsCommentStorage) 写入拦截，精准密友过滤。
     *
     * iOS 8.0.71 实证调用链（2026-05-21）：
     *   SnsCommentCgi → StatusModelXmlParser
     * 入链路（密友给你点赞时）：
     *   push 进程收到点赞推送
     *   → w1.insertLike(SnsAction)  ← SnsAction.fromUserName = 点赞者 wxid
     *   → w1.y (f178674y) += 1      ← 互动计数 +1
     *   → ns.c.b = true             ← 发现 tab 红点置位
     *   → ww2.c.b = true            ← 镜像字段
     *   → 底部 tab "发现" 出现红点
     *
     * 截断点：beforeHookedMethod → setResult(null) 跳过 insert
     *         → w1.y 不递增 → ns.c.b 不被置 true → 红点不出现
     */
    private static void installW1InteractionFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> w1 = lpparam.classLoader.loadClass(SNS_COMMENT_STORAGE);
            int hooked = 0;

            // 探针实证 (8.0.71 probe_w1_fields.js)：
            //   v2(long, String, int, String) → boolean
            //   arg[1] = String = 互动者 wxid（直接就是 wxid，不在 SnsAction 对象里）
            //   w2(long, boolean) 紧随其后（镜像写入，同样需要拦截）
            for (Method m : w1.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!"v2".equals(mn) && !"w2".equals(mn)) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sW1Instance == null && param.thisObject != null) {
                            sW1Instance = param.thisObject;
                        }
                        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                        if (!StateMachine.getInstance().isActive()) return;
                        if (Bridge.getInstance().getWxids().isEmpty()) return;

                        // v2: arg[1] = wxid (String)
                        // w2: arg[0] = long（无 wxid），跳过精准检查，依赖 v2 已拦截
                        if (!"v2".equals(mn)) return;
                        if (param.args.length < 2) return;
                        String wxid = (param.args[1] instanceof String) ? (String) param.args[1] : null;

                        String ts2 = new java.text.SimpleDateFormat(
                                "HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date());
                        boolean isHidden = wxid != null && Bridge.getInstance().shouldHideId(wxid);
                        // 每次 v2 调用都写 rawfeed（供控制台实时观测）
                        com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                ts2 + " [MRD:w1:v2] wxid=" + wxid + " hidden=" + isHidden);
                        if (sDiagSeen.add("w1_v2_first")) {
                            Log.i(TAG, "[MRD:w1] v2 arg[1]=" + wxid + " hidden=" + isHidden);
                        }
                        if (!isHidden) return;

                        // 密友互动 → 跳过 → w1.y 不递增 → ns.c.b 不被置 true → 红点不出现
                        param.setResult(Boolean.FALSE);
                        Log.i(TAG, "[MRD:w1] blocked v2 wxid=" + wxid);
                        com.ghost.assist.core.Bridge.getInstance().addRawFeedLine(
                                ts2 + " [MRD:w1:BLOCKED] wxid=" + wxid);
                        InterceptCounter.getInstance().incF05("MRD-w1-v2");
                        com.ghost.assist.debug.DebugTelemetry.getInstance().addBlocked("badge");
                        com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                                "badge", "w1-v2-blocked",
                                com.ghost.assist.debug.DebugTelemetry.fields("wxid", wxid));
                    }
                });
                Log.i(TAG, "[MRD:w1] hooked w1." + mn + "()");
                hooked++;
            }

            if (hooked == 0) {
                Log.w(TAG, "[MRD:w1] v2/w2 not found");
            } else {
                Log.i(TAG, "[MRD:w1] interaction filter installed: " + hooked + " methods");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:w1] filter failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // v22 互动列表游标过滤（非破坏性「压制」，不删历史）
    //   w1.O1(int)/a2(int) 返回互动列表 Cursor（SnsMsgUIWithRelevance + WithAll 共用）。
    //   afterHook 包一层 TalkerFilterCursor，跳过 talker∈密友 的行 → 列表不显示密友，
    //   SnsComment 记录原样保留（用户自己关模块仍能看到全部历史）。
    // -------------------------------------------------------------------------
    private static void installInteractionListCursorFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> w1 = lpparam.classLoader.loadClass(SNS_COMMENT_STORAGE);
            int hooked = 0;
            for (Method m : w1.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!"N1".equals(mn) && !"O1".equals(mn) && !"a2".equals(mn)) continue; // v23b: 加 N1(与我的互动)
                if (m.getReturnType() != android.database.Cursor.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!isFilteringActive()) return;
                            java.util.Set<String> hidden = Bridge.getInstance().getWxids();
                            if (hidden.isEmpty()) return;
                            Object res = param.getResult();
                            if (!(res instanceof android.database.Cursor)) return;
                            if (res instanceof TalkerFilterCursor) return;
                            android.database.Cursor orig = (android.database.Cursor) res;
                            TalkerFilterCursor fc = new TalkerFilterCursor(orig, hidden);
                            if (fc.didFilter()) {
                                param.setResult(fc);
                                if (sDiagSeen.add("cursorflt_" + mn)) {
                                    Log.i(TAG, "[MRD:cursor] " + mn + " filtered "
                                            + orig.getCount() + "→" + fc.getCount());
                                }
                                InterceptCounter.getInstance().incF05("MRD-cursor-" + mn);
                            }
                        } catch (Throwable t) {
                            if (sDiagSeen.add("cursorflt_err_" + mn))
                                Log.w(TAG, "[MRD:cursor] " + mn + " wrap err: " + t);
                        }
                    }
                });
                hooked++;
            }
            Log.i(TAG, "[MRD:cursor] interaction list cursor filter installed: " + hooked + " methods");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:cursor] install failed: " + t);
        }
    }

    /**
     * 非破坏性游标过滤：跳过 talker∈密友 的行，DB 记录不动。
     * 位置重映射，不复制数据。
     */
    static final class TalkerFilterCursor extends android.database.CursorWrapper {
        private final int[] map;     // 过滤后位置 → 原始位置
        private int pos = -1;
        private final boolean filtered;

        TalkerFilterCursor(android.database.Cursor c, java.util.Set<String> hidden) {
            super(c);
            int n = c.getCount();
            int talkerIdx = c.getColumnIndex("talker");
            if (talkerIdx < 0) {
                // 列名不是 talker → 打一次列名表（供下一版定位），全保留（安全不误伤）
                if (sDiagSeen.add("cursor_cols")) {
                    Log.i(TAG, "[MRD:cursor] no 'talker' col; cols="
                            + java.util.Arrays.toString(c.getColumnNames()));
                }
                int[] all = new int[n];
                for (int i = 0; i < n; i++) all[i] = i;
                map = all;
                filtered = false;
                return;
            }
            int saved = c.getPosition();
            java.util.ArrayList<Integer> keep = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                c.moveToPosition(i);
                String t = c.getString(talkerIdx);
                if (t == null || !hidden.contains(t)) keep.add(i);
            }
            c.moveToPosition(saved);
            map = new int[keep.size()];
            for (int i = 0; i < map.length; i++) map[i] = keep.get(i);
            filtered = (map.length != n);
        }

        boolean didFilter() { return filtered; }

        @Override public int getCount() { return map.length; }
        @Override public int getPosition() { return pos; }
        @Override public boolean moveToPosition(int p) {
            if (p < 0) { pos = -1; super.moveToPosition(-1); return false; }
            if (p >= map.length) { pos = map.length; return false; }
            pos = p;
            return super.moveToPosition(map[p]);
        }
        @Override public boolean moveToFirst() { return moveToPosition(0); }
        @Override public boolean moveToLast() { return moveToPosition(map.length - 1); }
        @Override public boolean moveToNext() { return moveToPosition(pos + 1); }
        @Override public boolean moveToPrevious() { return moveToPosition(pos - 1); }
        @Override public boolean move(int offset) { return moveToPosition(pos + offset); }
        @Override public boolean isBeforeFirst() { return map.length == 0 || pos < 0; }
        @Override public boolean isAfterLast() { return map.length == 0 || pos >= map.length; }
        @Override public boolean isFirst() { return pos == 0 && map.length > 0; }
        @Override public boolean isLast() { return pos == map.length - 1 && map.length > 0; }
    }

    /** 从 w1.insertLike/insertComment 参数中读 SnsAction.fromUserName */
    private static String getSnsActionFromUserName(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            // 直接读 fromUserName 字段（protobuf 字段名，全版本不变）
            try {
                java.lang.reflect.Field f = arg.getClass().getDeclaredField("fromUserName");
                f.setAccessible(true);
                Object v = f.get(arg);
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored2) {}
        }
        return null;
    }

    /**
     * 从互动通知参数中提取 wxid。
     * 优先字段顺序（iOS 实证 → 通用候选）：
     *   fromUserName > commentUsername > d/f435583d > username 等
     */
    private static String extractWxidFromInteractionArg(Object arg) {
        if (arg == null) return null;
        if (arg instanceof String) {
            String s = (String) arg;
            return (s.startsWith("wxid_") && s.length() > 5) ? s : null;
        }
        // 扫 SMSG_WXID_FIELDS（fromUserName 排第一）
        for (String fn : SMSG_WXID_FIELDS) {
            Object v = getFieldRecursive(arg, fn);
            if (v instanceof String) {
                String s = (String) v;
                if (s.startsWith("wxid_") && s.length() > 5) {
                    if (sDiagSeen.add("w1_wxid_field_" + fn)) {
                        Log.i(TAG, "[MRD:w1] wxid via field=" + fn + " val=" + s);
                    }
                    return s;
                }
            }
        }
        // 如果参数是 List，扫第一个元素（likeUsers / commentUsers）
        if (arg instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) arg;
            if (!list.isEmpty()) return extractWxidFromInteractionArg(list.get(0));
        }
        return null;
    }

    private static void installSnsCommentStorageHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass(SNS_COMMENT_STORAGE);

            // v19 修复：int getter 实测不被微信调用 → sW1Instance 一直 null。
            // 改用构造器捕获实例（w1 在 SNS 存储首次访问时创建，晚于模块装载）。
            try {
                XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (sW1Instance == null && param.thisObject != null) {
                            sW1Instance = param.thisObject;
                            Log.i(TAG, "[MRD:scs] sW1Instance captured via <init>");
                        }
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:scs] ctor capture fail: " + t);
            }

            // 一次性 dump 字段 + 方法（给后续精准 hook 提供证据）
            StringBuilder fsb = new StringBuilder("[MRD:scs:fields] ");
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                fsb.append(f.getName()).append(":").append(f.getType().getSimpleName()).append(" ");
            }
            Log.i(TAG, fsb.toString());

            int hooked = 0;
            StringBuilder msb = new StringBuilder("[MRD:scs:methods] ");
            for (Method m : cls.getDeclaredMethods()) {
                String mn = m.getName();
                Class<?> rt = m.getReturnType();
                Class<?>[] pt = m.getParameterTypes();

                // 记录所有 0-1 param 方法（含诊断）
                if (pt.length <= 1) {
                    msb.append(mn).append("(").append(pt.length).append("):")
                       .append(rt.getSimpleName()).append(" ");
                }

                // hook：0-1 param + 返回 int/long（最可能的计数 getter）
                if (pt.length > 1) continue;

                // v19: Cursor 方法（N1/O1/a2，互动列表读取时调用）捕获 sW1Instance 兜底
                if (rt == android.database.Cursor.class) {
                    final String cmn = mn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sW1Instance == null && param.thisObject != null) {
                                sW1Instance = param.thisObject;
                                Log.i(TAG, "[MRD:scs] sW1Instance captured via " + cmn + " (cursor)");
                            }
                        }
                    });
                    hooked++;
                    continue;
                }

                if (rt != int.class && rt != long.class
                        && rt != Integer.class && rt != Long.class) continue;

                final String fmn = mn;
                final Class<?> fRt = rt;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 捕获 sW1Instance（主进程 getter 调用时拿到实例，供 badge zero 用）
                        if (sW1Instance == null && param.thisObject != null) {
                            sW1Instance = param.thisObject;
                            Log.i(TAG, "[MRD:scs] sW1Instance captured via " + fmn);
                        }
                        // 诊断：每个 hook 首次调用时记录，无论模式
                        Object original = param.getResult();
                        if (sDiagSeen.add("scs_" + fmn)) {
                            Log.i(TAG, "[MRD:scs:diag] SnsCommentStorage." + fmn
                                    + " → " + original + " (first call)");
                        }

                        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                        if (!StateMachine.getInstance().isActive()) return;

                        param.setResult(0);
                        if (sDiagSeen.add("scs_block_" + fmn)) {
                            Log.i(TAG, "[MRD:scs] SnsCommentStorage." + fmn
                                    + " " + original + " → 0");
                        }
                        InterceptCounter.getInstance().incF05("MRD-scs-" + fmn);
                    }
                });
                hooked++;
            }
            Log.i(TAG, msb.toString());
            Log.i(TAG, "[MRD:scs] SnsCommentStorage hooked " + hooked + " methods");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:scs] hook failed: " + t);
        }
    }

    // -------------------------------------------------------------------------
    // 候选 C：FindMoreFriendsUI 入口红点
    // -------------------------------------------------------------------------
    private static void installFindMoreFriendsUIHook(XC_LoadPackage.LoadPackageParam lpparam) {
        for (String candidate : FIND_MORE_FRIENDS_UI_CANDIDATES) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(candidate);
                int hooked = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    String mn = m.getName();
                    if (!("M1".equals(mn) || "l0".equals(mn))) continue;
                    if (m.getParameterTypes().length > 1) continue;
                    Class<?> ret = m.getReturnType();
                    // 仅 hook 返回 bool / int 的 getter（avoid 误伤）
                    if (ret != boolean.class && ret != int.class
                            && ret != Boolean.class && ret != Integer.class) continue;

                    final String fmn = mn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isFilteringActive()) return;
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;

                            Object original = param.getResult();
                            if (ret == boolean.class || ret == Boolean.class) {
                                param.setResult(false);
                            } else {
                                param.setResult(0);
                            }
                            if (sDiagSeen.add("C_" + fmn)) {
                                Log.i(TAG, "[MRD:C] " + candidate + "." + fmn
                                        + " " + original + " → blocked");
                            }
                            InterceptCounter.getInstance().incF05("MRD-C-" + fmn);
                        }
                    });
                    hooked++;
                }
                if (hooked > 0) {
                    Log.i(TAG, "[MRD:C] " + candidate + " hooked " + hooked);
                }
                return; // 找到一个候选类就停（避免重复 hook 同语义类）
            } catch (ClassNotFoundException ignored) {
                // 下一个候选
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:C] " + candidate + " failed: " + t);
            }
        }
        Log.w(TAG, "[MRD:C] no FindMoreFriendsUI candidate matched");
    }

    // -------------------------------------------------------------------------
    // 候选 D：EventBus 红点事件拦截
    //
    // 微信用 com.tencent.mm.sdk.event.EventCenter (或 IEvent.publish())
    // 拦截 publish() 时，如果事件类名匹配红点事件 simple name → 隐藏态阻断
    // -------------------------------------------------------------------------
    private static final String[] EVENT_CENTER_CANDIDATES = {
            "com.tencent.mm.sdk.event.EventCenter",
            "com.tencent.mm.sdk.event.IEvent",
    };

    private static void installEventBusHook(XC_LoadPackage.LoadPackageParam lpparam) {
        for (String candidate : EVENT_CENTER_CANDIDATES) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(candidate);
                int hooked = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    String mn = m.getName();
                    if (!("publish".equals(mn) || "post".equals(mn))) continue;
                    if (m.getParameterTypes().length != 1) continue;

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object evt = param.args[0];
                            if (evt == null) return;
                            String simpleName = evt.getClass().getSimpleName();

                            if (sDiagSeen.add("D_evt_" + simpleName)) {
                                Log.i(TAG, "[MRD:D] event=" + simpleName);
                            }

                            if (!isRedDotEvent(simpleName)) return;
                            if (!isFilteringActive()) return;
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;

                            param.setResult(null); // 阻断 publish
                            Log.i(TAG, "[MRD:D] blocked event=" + simpleName);
                            InterceptCounter.getInstance().incF05("MRD-D-" + simpleName);
                        }
                    });
                    hooked++;
                }
                if (hooked > 0) {
                    Log.i(TAG, "[MRD:D] " + candidate + " hooked " + hooked);
                    return;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:D] " + candidate + " failed: " + t);
            }
        }
        Log.w(TAG, "[MRD:D] no EventCenter candidate matched");
    }

    private static boolean isRedDotEvent(String simpleName) {
        for (String n : RED_DOT_EVENT_SIMPLE_NAMES) {
            if (n.equals(simpleName)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // v18  tab 角标计数器清零
    //
    // 问题根因（2026-05-21 实证）：
    //   badge "20" = w1.y，在 :push 进程写入，主进程不可 hook 写入路径（Layer1 跨进程）。
    //   tab 角标数字通过 TabRedDotChangeEvent / WeChatTabRedDotEvent 广播，
    //   不走 w1.E1() getter（zero-call 验证）。
    //
    // 策略：
    //   A) TabRedDotChangeEvent / WeChatTabRedDotEvent  ctor 后置：
    //      清零所有 int/long 字段 → 角标数字归 0。
    //   B) 同步截获 sW1Instance（event 内部字段可能持有 SnsCommentStorage 引用）
    //      → 一旦拿到 → zeroW1FieldY → w1.y=0 → 冷启动下次展示也为 0。
    // -------------------------------------------------------------------------
    private static void installTabBadgeCounterSuppressor(
            XC_LoadPackage.LoadPackageParam lpparam) {
        int installed = 0;
        for (String evtCls : new String[]{CLS_TAB_CHANGE_EVT, CLS_WECHAT_TAB_EVT}) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(evtCls);
                final String simpleName = cls.getSimpleName();
                XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                        if (!StateMachine.getInstance().isActive()) return;
                        if (Bridge.getInstance().getWxids().isEmpty()) return;

                        Object evt = param.thisObject;
                        Class<?> ec = evt.getClass();
                        boolean zeroed = false;
                        for (int d = 0; ec != null && ec != Object.class && d < 4; d++) {
                            for (java.lang.reflect.Field f : ec.getDeclaredFields()) {
                                // 捕获 SnsCommentStorage 引用
                                if (sW1Instance == null
                                        && f.getType().getName().equals(SNS_COMMENT_STORAGE)) {
                                    try {
                                        f.setAccessible(true);
                                        Object ref = f.get(evt);
                                        if (ref != null) {
                                            sW1Instance = ref;
                                            zeroW1FieldY(sW1Instance);
                                            Log.i(TAG, "[MRD:tab] w1 ref captured from "
                                                    + simpleName + "." + f.getName());
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                // 清零 int/long 计数字段（角标数字 → 0）
                                if (f.getType() != int.class && f.getType() != long.class) continue;
                                try {
                                    f.setAccessible(true);
                                    long v = f.getLong(evt);
                                    if (v > 0) {
                                        f.setLong(evt, 0L);
                                        if (!zeroed) {
                                            Log.i(TAG, "[MRD:tab] " + simpleName
                                                    + "." + f.getName() + " " + v + "→0");
                                        }
                                        zeroed = true;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            ec = ec.getSuperclass();
                        }
                        if (zeroed) {
                            InterceptCounter.getInstance().incF05("MRD-tab-badge");
                            com.ghost.assist.debug.DebugTelemetry.getInstance().addBlocked("badge");
                            com.ghost.assist.debug.DebugTelemetry.getInstance().emit(
                                    "badge", "tab-zero",
                                    com.ghost.assist.debug.DebugTelemetry.fields(
                                            "evt", simpleName));
                        }
                    }
                });
                Log.i(TAG, "[MRD:tab] " + simpleName + " badge zeroing installed");
                installed++;
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:tab] " + evtCls + " hook failed: " + t);
            }
        }
        Log.i(TAG, "[MRD:tab] tab badge counter suppressor: " + installed + " events hooked");
    }

    // -------------------------------------------------------------------------
    // v19 B 精准：显示层拦截 —— TextView.setText 限定 osw(发现tab)/o58(朋友圈行)
    //   HIDDEN+enabled 态把红点数字改成「非密友未读数」
    //   依据(本会话 L1)：v18 event 0 命中、scs getter 清零对 badge 无效 → 显示层才可靠；
    //   w1.rawQuery(SnsComment, talker not in 密友) 已 Frida 实证可行。
    // -------------------------------------------------------------------------
    private static volatile int sNonMiyouCache = -1;
    private static volatile long sNonMiyouCacheTime = 0L;
    private static volatile ClassLoader sClassLoader = null;
    private static volatile long sLastMarkTime = 0L;
    private static volatile Object sDbHandle = null;          // v23: 缓存 WCDB 写句柄, 避免每次反射扫
    // v23: 专用后台线程, 把"找句柄 + 写库 UPDATE"移出主线程, 防 WCDB 锁阻塞主线程 ANR
    private static final java.util.concurrent.ExecutorService sBgExec =
            java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "mrd-bg");
                    t.setDaemon(true);
                    return t;
                }
            });

    private static void installBadgePreciseFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.widget.TextView", lpparam.classLoader,
                    "setText", CharSequence.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // [tl-bubble] "X条新消息"互动气泡 (TextView id=n_t): HIDDEN 态重算为非密友数, 0/不可算则隐藏整条
                        try {
                            Object a0 = param.args[0];
                            if (a0 != null && a0.toString().contains("条新消息")
                                    && "n_t".equals(getResEntryName(param.thisObject))) {
                                String bt = a0.toString();
                                if (sDiagSeen.add("tlbubble_seen")) {
                                    Log.i(TAG, "[MRD:tl] bubble n_t raw=\"" + bt + "\" parents="
                                            + parentChain((android.view.View) param.thisObject, 6));
                                }
                                if (AppConfig.getInstance().isMomentsRedDotEnabled()
                                        && StateMachine.getInstance().isActive()
                                        && !Bridge.getInstance().getWxids().isEmpty()) {
                                    int nonMiyou = nonMiyouCachedOrRefresh(param.thisObject);
                                    if (nonMiyou > 0) {
                                        param.args[0] = nonMiyou + "条新消息";
                                        if (sDiagSeen.add("tlbubble_recompute"))
                                            Log.i(TAG, "[MRD:tl] bubble recompute " + bt + " -> " + nonMiyou + "条新消息");
                                    } else {
                                        param.args[0] = "";
                                        hideBubbleContainer((android.view.View) param.thisObject);
                                        if (sDiagSeen.add("tlbubble_hidden"))
                                            Log.i(TAG, "[MRD:tl] bubble hidden (nonMiyou=" + nonMiyou + " raw=" + bt + ")");
                                    }
                                }
                            }
                        } catch (Throwable e2) {
                            if (sDiagSeen.add("tlbubble_err")) Log.w(TAG, "[MRD:tl] bubble err: " + e2);
                        }

                        if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                        if (!StateMachine.getInstance().isActive()) return;

                        Object arg = param.args[0];
                        if (arg == null) return;
                        String s = arg.toString().trim();
                        int len = s.length();
                        if (len == 0 || len > 3) return;
                        for (int i = 0; i < len; i++) {
                            char c = s.charAt(i);
                            if (c < '0' || c > '9') return;
                        }

                        String rid = getResEntryName(param.thisObject);
                        if (!("osw".equals(rid) || "o58".equals(rid))) return;

                        if (sDiagSeen.add("badge_seen_" + rid)) {
                            Log.i(TAG, "[MRD:badge] hit " + rid + " text=" + s);
                        }

                        // v20: SQL 方案 —— 标记密友互动已读，微信自己重算未读数
                        // （整链一致：发现 tab/朋友圈红点/气泡/互动列表，且不误伤微信 tab）
                        markMiyouRead(param.thisObject);
                    } catch (Throwable t) {
                        Log.w(TAG, "[MRD:badge] filter err: " + t);
                    }
                }
            });
            Log.i(TAG, "[MRD:badge] precise badge filter installed (osw/o58 -> 非密友)");
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:badge] install failed: " + t);
        }
    }

    /**
     * 非密友未读互动数：select count(*) from SnsComment where isRead=0 and isSilence!=1
     *   and talker not in (密友 wxids)。走 w1.rawQuery（本会话 Frida 实证可行）。1.5s 缓存。
     * @return &gt;=0 非密友数；-1 = 不可算（保持原红点）
     */
    private static int computeNonMiyouSnsUnread(Object view) {
        long now = System.currentTimeMillis();
        int cached = sNonMiyouCache;
        if (cached >= 0 && now - sNonMiyouCacheTime < 1500L) return cached;

        Object w1 = getSnsCommentStorage(view);
        if (w1 == null) return -1;
        try {
            Set<String> miyou = Bridge.getInstance().getWxids();
            if (miyou == null || miyou.isEmpty()) return -1;
            StringBuilder in = new StringBuilder();
            for (String w : miyou) {
                if (w == null || w.length() == 0) continue;
                if (in.length() > 0) in.append(",");
                in.append("'").append(w.replace("'", "")).append("'");
            }
            if (in.length() == 0) return -1;
            String sql = "select count(*) from SnsComment where isRead=0 and isSilence!=1 and talker not in ("
                    + in + ")";
            Object curObj = XposedHelpers.callMethod(w1, "rawQuery", sql, new String[0]);
            if (curObj == null) return 0;
            android.database.Cursor cursor = (android.database.Cursor) curObj;
            int cnt = 0;
            try {
                if (cursor.moveToFirst()) cnt = cursor.getInt(0);
            } finally {
                try { cursor.close(); } catch (Throwable ignored) {}
            }
            sNonMiyouCache = cnt;
            sNonMiyouCacheTime = now;
            return cnt;
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:badge] computeNonMiyou err: " + t);
            return -1;
        }
    }

    private static String getResEntryName(Object view) {
        try {
            Object idObj = XposedHelpers.callMethod(view, "getId");
            if (!(idObj instanceof Integer)) return null;
            int id = (Integer) idObj;
            if (id == -1 || id == 0) return null;
            Object res = XposedHelpers.callMethod(view, "getResources");
            Object name = XposedHelpers.callMethod(res, "getResourceEntryName", id);
            return (name instanceof String) ? (String) name : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 主动获取 SnsCommentStorage(w1)：优先已捕获实例，否则 l4.Qi() 静态取。
     * jadx 实证（hm.java）：com.tencent.mm.plugin.sns.model.l4.Qi() → w1 单例。
     * 这样不依赖"打开互动列表"才创建 w1。
     */
    /**
     * v20 SQL 方案：把密友未读互动在 DB 标记为已读，微信自己重算未读数 →
     * 发现 tab/朋友圈红点/气泡/互动列表整条链自然变成非密友数。3s 去抖。
     * 用微信自己的更新通道 f149254d.i（同 C2 updateToRead），触发 WCDB 观察者刷新。
     */
    private static void markMiyouRead(final Object view) {
        long now = System.currentTimeMillis();
        if (now - sLastMarkTime < 3000L) return;
        sLastMarkTime = now;            // v23: 提前置位 → 去抖 + 防止重复派发后台任务
        // v23 降 ANR：把"找句柄 + 写库 UPDATE"整段移出主线程。
        //   旧版同步写 WCDB → 主线程 futex_wait 等 DB 锁 → ImproveSnsTimelineUI ANR（已 L1 实证）。
        sBgExec.execute(new Runnable() {
            @Override public void run() {
                try {
                    Object w1 = getSnsCommentStorage(view);
                    if (w1 == null) return;
                    // v21 一次性结构 dump：找正确 DB 写通道（后台线程, 不卡 UI）
                    if (sDiagSeen.add("w1_struct_dump")) dumpW1Structure(w1);
                    Set<String> miyou = Bridge.getInstance().getWxids();
                    if (miyou == null || miyou.isEmpty()) return;
                    StringBuilder in = new StringBuilder();
                    for (String w : miyou) {
                        if (w == null || w.length() == 0) continue;
                        if (in.length() > 0) in.append(",");
                        in.append("'").append(w.replace("'", "")).append("'");
                    }
                    if (in.length() == 0) return;
                    String sql = "update SnsComment set isRead=1, isReminding=0 where isRead=0 and talker in ("
                            + in + ")";
                    // v23: 句柄缓存，只在首次反射扫一次，之后复用
                    Object db = sDbHandle;
                    if (db == null) {
                        db = findW1DbHandle(w1);
                        if (db != null) sDbHandle = db;
                    }
                    if (db == null) {
                        if (sDiagSeen.add("db_not_found")) Log.w(TAG, "[MRD:badge] db handle not found on w1");
                        return;
                    }
                    Object ret = XposedHelpers.callMethod(db, "i", "SnsComment", sql);
                    if (sDiagSeen.add("sql_mark_done")) {
                        Log.i(TAG, "[MRD:badge] SQL mark密友 read ret=" + ret
                                + " via " + db.getClass().getName() + " (bg)");
                    }
                    InterceptCounter.getInstance().incF05("MRD-badge-sql");
                } catch (Throwable t) {
                    if (sDiagSeen.add("sql_mark_err")) Log.w(TAG, "[MRD:badge] markMiyouRead err: " + t);
                }
            }
        });
    }

    /** v21 一次性 dump w1(SnsCommentStorage) 字段+方法 → logcat，找 DB 写通道真名。 */
    private static void dumpW1Structure(Object w1) {
        try {
            Class<?> wc = w1.getClass();
            StringBuilder fb = new StringBuilder("[MRD:w1:FIELDS] " + wc.getName() + " :: ");
            for (java.lang.reflect.Field f : wc.getDeclaredFields()) {
                fb.append(f.getName()).append("=").append(f.getType().getName()).append("  ");
            }
            Log.i(TAG, fb.toString());
            StringBuilder mb = new StringBuilder("[MRD:w1:METHODS] ");
            for (Method m : wc.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 0 || ps.length > 3) continue;
                mb.append(m.getName()).append("(");
                for (Class<?> p : ps) mb.append(p.getSimpleName()).append(",");
                mb.append(")").append(m.getReturnType().getSimpleName()).append("  ");
            }
            Log.i(TAG, mb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "[MRD:w1:dump] " + t);
        }
    }

    /** v21 扫 w1 字段，返回第一个带 i(String,String,...) 方法的对象（WCDB 写句柄候选）。 */
    private static Object findW1DbHandle(Object w1) {
        Class<?> wc = w1.getClass();
        for (int d = 0; wc != null && wc != Object.class && d < 3; d++) {
            for (java.lang.reflect.Field f : wc.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(w1);
                    if (v == null || v instanceof String) continue;
                    Class<?> vc = v.getClass();
                    for (int dd = 0; vc != null && vc != Object.class && dd < 4; dd++) {
                        for (Method m : vc.getDeclaredMethods()) {
                            if (!"i".equals(m.getName())) continue;
                            Class<?>[] ps = m.getParameterTypes();
                            if (ps.length >= 2 && ps[0] == String.class && ps[1] == String.class) {
                                Log.i(TAG, "[MRD:w1] db handle = field " + f.getName()
                                        + " (" + v.getClass().getName() + ")");
                                return v;
                            }
                        }
                        vc = vc.getSuperclass();
                    }
                } catch (Throwable ignored) {}
            }
            wc = wc.getSuperclass();
        }
        return null;
    }

    private static Object getSnsCommentStorage(Object view) {
        Object w1 = sW1Instance;
        if (w1 != null) return w1;
        // 用 view 的 context classloader（Tinker 合并后的"活"loader）；
        // lpparam.classLoader 可能是 Tinker 前副本 → l4.Qi() 报 Kernel not initialized。
        ClassLoader cl = null;
        try {
            Object ctx = XposedHelpers.callMethod(view, "getContext");
            if (ctx != null) cl = (ClassLoader) XposedHelpers.callMethod(ctx, "getClassLoader");
        } catch (Throwable ignored) {}
        if (cl == null) cl = sClassLoader;
        if (cl == null) return null;
        try {
            Class<?> l4 = cl.loadClass("com.tencent.mm.plugin.sns.model.l4");
            Object got = XposedHelpers.callStaticMethod(l4, "Qi");
            if (got != null) {
                sW1Instance = got;
                if (sDiagSeen.add("w1_via_Qi")) Log.i(TAG, "[MRD:badge] w1 via l4.Qi()");
                return got;
            }
        } catch (Throwable t) {
            if (sDiagSeen.add("w1_Qi_fail")) Log.w(TAG, "[MRD:badge] l4.Qi() fail: " + t);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    private static boolean isFilteringActive() {
        if (!StateMachine.getInstance().isActive()) return false;
        if (Bridge.getInstance().allHiddenIds().isEmpty()) return false;
        return true;
    }
}
