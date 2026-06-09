package com.ghost.assist.moduleD;

import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.DebugTelemetry;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Moments feed filter — v20 (8.0.71)
 *
 * D1: na4.b / la4.p — extractPosterWxid → remove post
 * D2/D3: LikeUserList / CommentUserList on la4.p or b1() SnsInfo
 * L0v3: SnsMsgUIWithRelevance / jw1.d — hide 密友点赞评论/顶部提醒
 */
public class MomentsFilter {

    private static final String TAG = "NCL";

    // P1E Step3: these 8 anchors are now sourced from the encrypted SO registry
    // (moments.feed) via GuardRuntime.getRecipe(), literal kept as fallback.
    // resolveRecipes() (called first in install()) overrides each on registry hit;
    // scatter / SO-unavailable → keeps literal → behaviour unchanged. NON-FINAL so
    // the resolved value can replace the fallback. (z15.e56 actor_class /
    // f435583d FIELD_E56_WXID NOT migrated — no live anchor / dead constant.)
    private static String ITEM_PROMO      = "la4.p";
    private static String ITEM_FRIEND     = "na4.b";
    private static final String ITEM_BUBBLE     = "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance";
    private static final String ITEM_NOTIFY     = "jw1.d";
    // 控制台高频类，待确认语义（疑似 like/comment 元素）
    private static final String ITEM_WQ_C1      = "wq.c1";
    private static final String ITEM_WQ_Y0      = "wq.y0";
    private static final String ITEM_II5_B      = "ii5.b";
    private static String METHOD_SNS_OBJ  = "h1";   // la4.p.h1() → TimeLineObject（p1 字段的解包 getter）
    private static final String METHOD_NICKNAME = "O0";
    private static String FIELD_WXID      = "field_userName";
    private static String FIELD_INNER     = "d";
    private static String ADAPTER_CLASS   = "e2";

    // SnsObject 字段（agent 实证 2026-05-20）
    private static String FIELD_LIKE_LIST    = "LikeUserList";     // LinkedList<e56>
    private static String FIELD_COMMENT_LIST = "CommentUserList";  // LinkedList<e56>
    private static final String FIELD_LIKE_COUNT   = "LikeCount";
    private static final String FIELD_LIKE_UC      = "LikeUserListCount";
    private static final String FIELD_CMT_COUNT    = "CommentCount";
    private static final String FIELD_CMT_UC       = "CommentUserListCount";
    // e56 元素字段（proto field 1 = wxid，proto field 2 = 昵称）
    private static final String FIELD_E56_WXID     = "f435583d";
    private static final String FIELD_E56_NICK     = "f435584e";
    private static final String FIELD_ITEM_USER    = "d";

    // 8.0.71 确认：z15.e56 / cs5.di0 / i84.y 的 wxid 字段均为 "d"
    private static final String[] ACTOR_FIELD_NAMES = {
            "d", "f435583d", "username", "field_userName"
    };

    private static volatile boolean sRecipesResolved = false;

    /** Resolve one recipe field from registry moments.feed; "" → keep fallback. */
    private static String recipe(String key, String fallback) {
        String v = com.ghost.assist.core.GuardRuntime.getRecipe("moments.feed", key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    /**
     * P1E Step3: pull the 8 live moments.feed anchors from the encrypted registry,
     * falling back to the embedded literals when the registry is unavailable /
     * scattered. Idempotent; called once at install() before any hook fires.
     * Only swaps the class-name SOURCE — does not touch addAll/remove callback
     * logic (iron rules 28/29).
     */
    private static void resolveRecipes() {
        if (sRecipesResolved) return;
        ITEM_FRIEND        = recipe("item_friend", ITEM_FRIEND);
        ITEM_PROMO         = recipe("item_promo", ITEM_PROMO);
        ADAPTER_CLASS      = recipe("adapter_class", ADAPTER_CLASS);
        FIELD_WXID         = recipe("wxid_field", FIELD_WXID);
        FIELD_INNER        = recipe("inner_field", FIELD_INNER);
        METHOD_SNS_OBJ     = recipe("sns_getter", METHOD_SNS_OBJ);
        FIELD_LIKE_LIST    = recipe("like_list", FIELD_LIKE_LIST);
        FIELD_COMMENT_LIST = recipe("comment_list", FIELD_COMMENT_LIST);
        sRecipesResolved = true;
        boolean fbOk = "na4.b".equals(recipe("__no_such_key__", "na4.b"));
        Log.i(TAG, "[MF] recipes friend=" + ITEM_FRIEND + " promo=" + ITEM_PROMO
                + " adapter=" + ADAPTER_CLASS + " wxid=" + FIELD_WXID
                + " inner=" + FIELD_INNER + " sns=" + METHOD_SNS_OBJ
                + " like=" + FIELD_LIKE_LIST + " cmt=" + FIELD_COMMENT_LIST
                + " fallbackSelfTest=" + (fbOk ? "ok" : "FAIL"));
    }

    private static volatile Method sSnsInfoMethod  = null;
    private static volatile Method sNicknameMethod = null;
    private static boolean sInstalled = false;

    private static final AtomicInteger sPendingRemoved = new AtomicInteger(0);
    private static final AtomicInteger sPendingMsgRemoved = new AtomicInteger(0);
    private static final java.util.Set<String> sDiagSeen = new java.util.HashSet<>();

    // -------------------------------------------------------------------------

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        // P1E Step3: resolve moments.feed anchors from registry (fallback=literals)
        // BEFORE any hook installs.
        resolveRecipes();

        try {
            XC_MethodHook addAllHook = new XC_MethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.args[0] instanceof Collection)) return;
                    handleAddAll((Collection<Object>) param.args[0]);
                }
            };

            XposedBridge.hookMethod(
                    ArrayList.class.getMethod("addAll", Collection.class), addAllHook);
            XposedBridge.hookMethod(
                    java.util.LinkedList.class.getMethod("addAll", Collection.class), addAllHook);

            // LinkedList.add(Object) — 拦截懒加载单条评论/点赞写入
            XposedBridge.hookMethod(
                    java.util.LinkedList.class.getMethod("add", Object.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object item = param.args[0];
                            if (item == null) return;
                            String cn = item.getClass().getName();
                            // 只关心微信内部类，跳过 java.* / android.*
                            if (cn.startsWith("java.") || cn.startsWith("android.")
                                    || cn.startsWith("androidx.") || cn.startsWith("kotlin.")) return;

                            // 诊断：第一次见到新类型就打字段（找 wxid）
                            if (sDiagSeen.add("LL_ADD_" + cn)) {
                                StringBuilder sb = new StringBuilder("[MF:LLadd cls=" + cn + "]");
                                Class<?> ec = item.getClass();
                                for (int d = 0; ec != null && d < 3; d++) {
                                    for (Field f : ec.getDeclaredFields()) {
                                        try {
                                            f.setAccessible(true);
                                            Object v = f.get(item);
                                            if (v instanceof String)
                                                sb.append(" ").append(f.getName()).append("=").append(v);
                                        } catch (Throwable ignored) {}
                                    }
                                    ec = ec.getSuperclass();
                                }
                                Log.i(TAG, sb.toString());
                                Bridge.getInstance().addRawFeedLine(sb.toString());

                                // diag: NotificationItem ClassLoader check
                                if ("com.tencent.mm.booter.notification.NotificationItem".equals(cn)) {
                                    Class<?> nic = item.getClass();
                                    Log.i(TAG, "[MF:LLadd:DIAG] NotificationItem.classLoader=" + nic.getClassLoader());
                                    for (Method mm : nic.getDeclaredMethods()) {
                                        if ("a".equals(mm.getName()) && mm.getParameterTypes().length == 1)
                                            Log.i(TAG, "[MF:LLadd:DIAG] a(Context) found: " + mm.toGenericString());
                                    }
                                    // h field
                                    try {
                                        Field hf = nic.getDeclaredField("h");
                                        hf.setAccessible(true);
                                        Log.i(TAG, "[MF:LLadd:DIAG] this.h=" + hf.get(item));
                                    } catch (Throwable ignored) {}
                                }
                            }

                            // 过滤：wxid 在密友名单 → 阻止 add
                            if (!StateMachine.getInstance().isActive()) return;
                            Set<String> hidden = Bridge.getInstance().getWxids();
                            if (hidden.isEmpty()) return;
                            if (isHiddenListEntry(item, hidden)) {
                                param.setResult(true); // 假装 add 成功但实际不加
                                Log.i(TAG, "[MF] LLadd blocked: " + cn);
                                InterceptCounter.getInstance().incF05("LLadd_block");
                                String bWxid = extractWxidFromEntry(item);
                                DebugTelemetry dt = DebugTelemetry.getInstance();
                                dt.emit("like_comment", "lladd_blocked",
                                        DebugTelemetry.fields("wxid", bWxid != null ? bWxid : "",
                                                "class", cn, "page", "Moments"));
                                dt.addBlocked("like_comment");
                            }
                        }
                    });
            Log.i(TAG, "[MF] addAll+add hook ok (ArrayList + LinkedList)");

            installFeedAdapterHook(lpparam);
            installD3Hook(lpparam);
            installUnreadHooks(lpparam);

            // Hot-reload: state listener (registration log) + RefreshBus callback.
        StateMachine.getInstance().addListener("MomentsFilter",
                (oldState, newState) -> { /* log only — RefreshBus driven by StateMachine */ });
        RefreshBus.getInstance().register("MomentsFilter", hidden -> {
            // Moments feed is driven by WeChat's own scroll/refresh — we can't force reload here.
            // Best-effort: clear any stale pending counters on state change so the next
            // WeChat-initiated addAll call starts with a clean slate.
            sPendingRemoved.set(0);
            sPendingMsgRemoved.set(0);
            Log.i(TAG, "[BUS] refresh MomentsFilter pending-counters reset hidden=" + hidden);
        });

        Log.i(TAG, "[MF] v20 ready (8.0.71)");

        } catch (Throwable t) {
            Log.e(TAG, "[MF] install FAILED: " + t);
            sInstalled = false;
        }
    }

    /**
     * D3: hook la4.p.getCommentList() afterHookedMethod → 过滤密友评论
     * D2: hook la4.p.getLikeUserList() afterHookedMethod → 过滤密友点赞（如找到）
     */
    private static void installD3Hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> la4pCls = lpparam.classLoader.loadClass(ITEM_PROMO);
            int hooked = 0;
            for (Method m : la4pCls.getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> ret = m.getReturnType();
                if (!java.util.Collection.class.isAssignableFrom(ret)) continue;

                String mn = m.getName();
                final boolean isComment = mn.contains("Comment") || mn.contains("comment");
                final boolean isLike    = mn.contains("Like") || mn.contains("like");
                if (!isComment && !isLike) continue;

                final String label = isComment ? "D3" : "D2";
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (!(result instanceof java.util.List)) return;
                        java.util.List list = (java.util.List) result;
                        if (list.isEmpty()) return;

                        // 一次性诊断：打第一个元素的所有字段，确认 wxid 路径
                        if (sDiagSeen.add("D3_ELEM_" + label)) {
                            Object elem = list.get(0);
                            StringBuilder sb = new StringBuilder(
                                    "[D3:elem=" + elem.getClass().getName() + " sz=" + list.size() + "]");
                            Class<?> ec = elem.getClass();
                            for (int ed = 0; ec != null && ed < 4; ed++) {
                                for (Field f : ec.getDeclaredFields()) {
                                    f.setAccessible(true);
                                    try {
                                        Object v = f.get(elem);
                                        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                                            sb.append(" ").append(f.getName()).append("=").append(v);
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                ec = ec.getSuperclass();
                            }
                            Log.i(TAG, sb.toString());
                            Bridge.getInstance().addRawFeedLine(sb.toString());
                        }

                        Bridge bridge = Bridge.getInstance();
                        Set<String> hidden = bridge.getWxids();
                        if (!StateMachine.getInstance().isActive() || hidden.isEmpty()) return;
                        int removed = 0;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object entry = list.get(i);
                            if (entry != null && isHiddenListEntry(entry, hidden)) {
                                try { list.remove(i); removed++; }
                                catch (UnsupportedOperationException ignored) {}
                            }
                        }
                        if (removed > 0) {
                            InterceptCounter.getInstance().incF05(label + "(x" + removed + ")");
                            Log.i(TAG, "[MF] " + label + " " + param.method.getName()
                                    + " removed=" + removed);
                        }
                    }
                });
                Log.i(TAG, "[MF] " + label + " hook: " + mn + "() ok");
                hooked++;
            }
            if (hooked == 0) Log.w(TAG, "[MF] D3 hook: no List getter found on " + ITEM_PROMO);
        } catch (Throwable t) {
            Log.w(TAG, "[MF] D3 hook err: " + t);
        }
    }

    private static void installFeedAdapterHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> adapterClass = lpparam.classLoader.loadClass(ADAPTER_CLASS);
            for (Method m : adapterClass.getMethods()) {
                if ("getItemCount".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            int pending = sPendingRemoved.getAndSet(0);
                            if (pending <= 0) return;
                            int adjusted = Math.max(0, (Integer) param.getResult() - pending);
                            param.setResult(adjusted);
                            Log.i(TAG, "[MF] getItemCount -" + pending);
                        }
                    });
                    Log.i(TAG, "[MF] e2.getItemCount hook ok");
                    break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MF] e2 not found: " + t);
        }
    }

    /** 尝试扣减朋友圈未读/新消息计数（小红点） */
    private static void installUnreadHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] classes = {
                "com.tencent.mm.plugin.sns.model.SnsLogic",
                "com.tencent.mm.plugin.sns.ui.SnsUnreadTipManager",
                "com.tencent.mm.plugin.sns.ui.SnsUIAction",
                "com.tencent.mm.plugin.sns.storage.SnsCommentStorage",
        };
        int hooked = 0;
        for (String cn : classes) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(cn);
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getReturnType() != int.class) continue;
                    String mn = m.getName().toLowerCase();
                    if (!mn.contains("unread") && !mn.contains("newmsg") && !mn.contains("newcount")) continue;
                    if (m.getParameterTypes().length > 1) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            int delta = sPendingMsgRemoved.getAndSet(0);
                            if (delta <= 0) return;
                            int cur = (Integer) param.getResult();
                            param.setResult(Math.max(0, cur - delta));
                            Log.i(TAG, "[MF] unread " + cn + "." + m.getName() + " -" + delta);
                        }
                    });
                    hooked++;
                }
            } catch (Throwable ignored) {}
        }
        if (hooked > 0) Log.i(TAG, "[MF] unread hooks=" + hooked);
    }

    // -------------------------------------------------------------------------

    private static void handleAddAll(Collection<Object> coll) {
        if (coll.isEmpty()) return;

        Object first = null;
        for (Object o : coll) { first = o; break; }
        if (first == null) return;

        String firstCn = first.getClass().getName();

        String preview = extractPreview(first);
        String ts = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(new java.util.Date());
        Bridge.getInstance().addRawFeedLine(ts + " " + firstCn + " sz=" + coll.size() + preview);

        if (sDiagSeen.add(firstCn)) {
            Log.i(TAG, "[MF] addAll firstItem=" + firstCn + " size=" + coll.size());
        }

        boolean isFriend  = ITEM_FRIEND.equals(firstCn);
        boolean isPromo   = ITEM_PROMO.equals(firstCn);
        boolean isBubble  = ITEM_BUBBLE.equals(firstCn);
        boolean isNotify  = ITEM_NOTIFY.equals(firstCn);
        boolean isWqC1    = ITEM_WQ_C1.equals(firstCn);
        boolean isWqY0    = ITEM_WQ_Y0.equals(firstCn);
        boolean isIi5B    = ITEM_II5_B.equals(firstCn);
        if (!isFriend && !isPromo && !isBubble && !isNotify
                && !isWqC1 && !isWqY0 && !isIi5B) return;

        // 疑似 like/comment 元素类：dump 第一个 item 的字段 + 关键 getter 返回值
        if (isWqC1 || isWqY0 || isIi5B) {
            if (sDiagSeen.add("DUMP_" + firstCn)) {
                dumpItem(first, Bridge.getInstance());
                Log.i(TAG, "[MF] suspect class=" + firstCn + " sz=" + coll.size());
                // wq.c1 有 a()/b() 返回 wq.b1，深入挖到 List 内容
                if (isWqC1) {
                    Object b1a = dumpMethodResult("[D2D3:wq.c1.a]", first, "a");
                    Object b1b = dumpMethodResult("[D2D3:wq.c1.b]", first, "b");
                    // 如果 wq.b1 上有 b() 返回 List，dump List 第一个元素
                    if (b1a != null) dumpMethodResult("[D2D3:wq.b1.a.b]", b1a, "b");
                    if (b1b != null) {
                        Object chainB = dumpMethodResult("[D2D3:wq.b1.b.b]", b1b, "b");
                        // wq.i0 没有 no-arg 方法 → dump 其字段
                        dumpFirstElemFields(chainB, "wq.b1.b()", "wq.i0");
                    }
                    // 挖到 wq.b1 上的字段，找 wxid 藏身处
                    if (sDiagSeen.add("DUMP_wq.b1_fields")) {
                        Log.i(TAG, "[D2D3] dumping wq.b1 full fields (b1b):");
                        dumpAllFields("[D2D3:wq.b1.fields]", b1b);
                        // p() 返回 wq.t，上面有 c()/d()/e()→String
                        Object wt = dumpMethodResultRaw("[D2D3:wq.b1.p]", b1b, "p");
                        if (wt != null) {
                            String cVal = invokeStringGetter(wt, "c");
                            String dVal = invokeStringGetter(wt, "d");
                            String eVal = invokeStringGetter(wt, "e");
                            Log.i(TAG, "[D2D3:wq.t] c=" + cVal + " d=" + dVal + " e=" + eVal);
                        }
                    }
                }
                if (isWqY0) {
                    // 疑似 like 元素类，遍历全部元素，找非空 wq.s
                    if (sDiagSeen.add("DUMP_wq.y0_fields")) {
                        Log.i(TAG, "[D2D3] dumping wq.y0 (sz=" + coll.size() + ")");
                        dumpAllFields("[D2D3:wq.y0[0].fields]", first);
                        dumpAllNoArgMethods("[D2D3:wq.y0[0].allmethods]", first);
                        int idx = 0;
                        for (Object elem : coll) {
                            if (elem == null) continue;
                            Object ws = getField(elem, "a");
                            if (ws == null) continue;
                            String cVal = invokeStringGetter(ws, "c");
                            String dVal = invokeStringGetter(ws, "d");
                            String eVal = invokeStringGetter(ws, "e");
                            int bVal = getIntField(elem, "b");
                            Log.i(TAG, "[D2D3:wq.y0[" + idx + "]] b=" + bVal + " wq.s c=" + cVal + " d=" + dVal + " e=" + eVal);
                            idx++;
                        }
                    }
                }
                // wq.i0 直接作为 addAll 元素出现 (size=180)，dump 其字段 + wq.t 值
                if ("wq.i0".equals(firstCn) && sDiagSeen.add("DUMP_wq.i0_fields")) {
                    Log.i(TAG, "[D2D3] dumping wq.i0 full fields:");
                    dumpAllFields("[D2D3:wq.i0.fields]", first);
                    Object i0b = getField(first, "b");
                    if (i0b != null) {
                        Log.i(TAG, "[D2D3:wq.i0.b] type=" + i0b.getClass().getName());
                        String cVal = invokeStringGetter(i0b, "c");
                        String dVal = invokeStringGetter(i0b, "d");
                        String eVal = invokeStringGetter(i0b, "e");
                        Log.i(TAG, "[D2D3:wq.i0.wt] c=" + cVal + " d=" + dVal + " e=" + eVal);
                    }
                }
            }
            return;
        }

        Bridge bridge = Bridge.getInstance();
        Set<String> hidden = bridge.getWxids();
        boolean filterOn = StateMachine.getInstance().isActive() && !hidden.isEmpty();

        // L0v3: 互动消息 / 顶部更新提醒
        if (isBubble || isNotify) {
            if (AppConfig.getInstance().isDebugEnabled()) {
                dumpItem(first, bridge);
            }
            if (!filterOn) return;

            ArrayList<Object> msgRemove = new ArrayList<>();
            for (Object item : coll) {
                if (shouldHideInteraction(item, hidden)) {
                    msgRemove.add(item);
                    Log.i(TAG, "[MF] L0v3 blocked " + item.getClass().getSimpleName()
                            + " wxids=" + collectInteractionWxids(item, hidden));
                }
            }
            int removed = removeFromCollection(coll, msgRemove);
            if (removed > 0) {
                sPendingMsgRemoved.addAndGet(removed);
                InterceptCounter.getInstance().incF05("L0v3(x" + removed + ")");
                Log.i(TAG, "[MF] L0v3 removed=" + removed);
                DebugTelemetry dt = DebugTelemetry.getInstance();
                dt.emit("badge", "bubble_blocked",
                        DebugTelemetry.fields("count", String.valueOf(removed), "page", "Moments"));
                dt.addBlocked("badge");
                dt.addRemoved("badge", removed);
            }
            return;
        }

        // D1 + D2/D3 on feed items
        // 诊断探针：不受 filterOn 控制，第一个 item 进来就跑一次，拿到 List getter 方法名
        boolean diagDone = !sDiagSeen.add("D2D3_METHODS");
        ArrayList<Object> toRemove = new ArrayList<>();
        for (Object item : coll) {
            String outerWxid = extractWxid(item);
            if (outerWxid != null) {
                String nick = extractNickname(item);
                bridge.addFeedWxid(outerWxid, nick);
                DebugTelemetry.getInstance().noteFeedWxid(outerWxid, nick);
            }

            if (!diagDone) {
                diagDone = true;
                runD2D3Diag(item);
            }

            // cmList 探针：每条 la4.p 都试 getCommentList()，直到找到非空
            if (!sDiagSeen.contains("D2D3_CMLIST_DONE") && ITEM_PROMO.equals(item.getClass().getName())) {
                try {
                    Object la4p = ITEM_FRIEND.equals(item.getClass().getName()) ? getField(item, FIELD_INNER) : item;
                    java.util.List<?> cmList = (java.util.List<?>) la4p.getClass().getMethod("getCommentList").invoke(la4p);
                    int sz = cmList.size();
                    if (sz > 0 && sDiagSeen.add("D2D3_CMLIST_DONE")) {
                        Log.i(TAG, "[D2D3:cmList] FOUND sz=" + sz);
                        int i = 0;
                        for (Object entry : cmList) {
                            if (entry == null) continue;
                            // 自适应：打所有 String/Number 字段，找 wxid 实际路径（-999 是 tinker 字段改名，此处改为全量 dump）
                            StringBuilder esb = new StringBuilder(
                                    "[D2D3:cmList[" + i + "]] class=" + entry.getClass().getName());
                            Class<?> ec = entry.getClass();
                            for (int ed = 0; ec != null && ed < 4; ed++) {
                                for (java.lang.reflect.Field ef : ec.getDeclaredFields()) {
                                    try {
                                        ef.setAccessible(true);
                                        Object ev = ef.get(entry);
                                        if (ev instanceof String || ev instanceof Number)
                                            esb.append(" ").append(ef.getName()).append("=").append(ev);
                                    } catch (Throwable ignored2) {}
                                }
                                ec = ec.getSuperclass();
                            }
                            Log.i(TAG, esb.toString());
                            i++;
                            if (i >= 3) break;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (!filterOn) continue;

            String posterWxid = extractPosterWxid(item);
            if (posterWxid != null && posterWxid.startsWith("wxid_") && hidden.contains(posterWxid)) {
                toRemove.add(item);
                Log.i(TAG, "[MF] D1 blocked poster=" + posterWxid);
                DebugTelemetry dt = DebugTelemetry.getInstance();
                dt.emit("moments", "d1_blocked",
                        DebugTelemetry.fields("wxid", posterWxid, "page", "Moments"));
                dt.addBlocked("moments");
            } else {
                cleanD2D3(item, hidden);
            }
        }

        int removed = removeFromCollection(coll, toRemove);
        if (removed > 0) {
            sPendingRemoved.addAndGet(removed);
            InterceptCounter.getInstance().incF05("D1(x" + removed + ")");
            Log.i(TAG, "[MF] D1 removed=" + removed);
            DebugTelemetry.getInstance().addRemoved("moments", removed);
        }
    }

    private static int removeFromCollection(Collection<Object> coll, ArrayList<Object> toRemove) {
        int removed = 0;
        try {
            for (Object item : toRemove) {
                if (coll.remove(item)) removed++;
            }
        } catch (UnsupportedOperationException ignored) {}
        return removed;
    }

    /** 互动项是否含密友 wxid（点赞/评论者，或提醒里的密友） */
    private static boolean shouldHideInteraction(Object item, Set<String> hidden) {
        for (String wxid : collectInteractionWxids(item, hidden)) {
            if (hidden.contains(wxid)) return true;
        }
        return false;
    }

    private static Set<String> collectInteractionWxids(Object item, Set<String> hidden) {
        Set<String> found = new HashSet<>();
        collectWxidsDeep(item, found, 0);
        Set<String> matched = new HashSet<>();
        for (String w : found) {
            if (w.startsWith("wxid_") && hidden.contains(w)) matched.add(w);
        }
        return matched;
    }

    private static void collectWxidsDeep(Object obj, Set<String> out, int depth) {
        if (obj == null || depth > 4) return;

        // Catfish hookSnsComments: getUsername / getReply_username
        try {
            for (Method m : obj.getClass().getMethods()) {
                String mn = m.getName();
                if (!"getUsername".equals(mn) && !"getReply_username".equals(mn)) continue;
                if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
                Object r = m.invoke(obj);
                if (r instanceof String) addWxid(out, (String) r);
            }
        } catch (Throwable ignored) {}

        for (String fn : ACTOR_FIELD_NAMES) {
            Object v = getField(obj, fn);
            if (v instanceof String) addWxid(out, (String) v);
        }

        // jw1.d: .f → fw1.b 内可能有 wxid
        if (ITEM_NOTIFY.equals(obj.getClass().getName())) {
            Object inner = getField(obj, "f");
            if (inner != null) collectWxidsDeep(inner, out, depth + 1);
            Object wr = getField(obj, "e");
            if (wr instanceof WeakReference) {
                Object ref = ((WeakReference<?>) wr).get();
                if (ref != null) collectWxidsDeep(ref, out, depth + 1);
            }
        }

        if (depth >= 2) return;
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 3; d++) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == String.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null && !v.getClass().isPrimitive()
                            && !(v instanceof Number) && !(v instanceof Boolean)
                            && !(v instanceof Collection) && !(v.getClass().getName().startsWith("android."))) {
                        collectWxidsDeep(v, out, depth + 1);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void addWxid(Set<String> out, String s) {
        if (s != null && s.startsWith("wxid_") && s.length() > 5) out.add(s);
    }

    // -------------------------------------------------------------------------
    // D1 wxid
    // -------------------------------------------------------------------------

    static String extractPosterWxid(Object item) {
        if (item == null) return null;
        String cn = item.getClass().getName();
        Object la4p = ITEM_FRIEND.equals(cn) ? getField(item, FIELD_INNER) : item;
        if (la4p == null) return null;

        // 主路径：la4p.h1() → TimeLineObject → field_userName（8.0.71 正式版）
        try {
            Method b1 = getSnsInfoMethod(la4p.getClass());
            if (b1 != null) {
                Object snsInfo = b1.invoke(la4p);
                if (snsInfo != null) {
                    Object u = getField(snsInfo, FIELD_WXID);
                    if (u instanceof String) {
                        String s = (String) u;
                        if (s.length() > 5) return s;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // fallback：直接读 la4p.field_userName（tinker 补丁路径，extractWxid 验证有效）
        Object u2 = getField(la4p, FIELD_WXID);
        if (u2 instanceof String) {
            String s = (String) u2;
            if (s.length() > 5) {
                if (sDiagSeen.add("D1_FALLBACK_HIT")) {
                    Log.i(TAG, "[MF] D1 fallback: field_userName on la4p directly (h1 miss)");
                }
                return s;
            }
        }
        return null;
    }

    static String extractWxid(Object item) {
        if (item == null) return null;
        String cn = item.getClass().getName();
        Object target = ITEM_FRIEND.equals(cn) ? getField(item, FIELD_INNER) : item;
        if (target == null) return null;
        Object u = getField(target, FIELD_WXID);
        if (!(u instanceof String)) return null;
        String s = (String) u;
        return s.length() > 5 ? s : null;
    }

    // -------------------------------------------------------------------------
    // D2/D3
    // -------------------------------------------------------------------------

    /** 无条件诊断：扫 la4p/SnsInfo 上所有 no-arg 方法，以及 w45.a 实例的所有 no-arg 方法 */
    private static void runD2D3Diag(Object item) {
        try {
            String cn = item.getClass().getName();
            Object la4p = ITEM_FRIEND.equals(cn) ? getField(item, FIELD_INNER) : item;
            if (la4p == null) return;

            // 1. la4p: 先扫 List-returning，再扫所有 no-arg（含 void）
            dumpListMethods("[D2D3:la4p.methods]", la4p);
            dumpAllNoArgMethods("[D2D3:la4p.allmethods]", la4p);
            dumpAllFields("[D2D3:la4p.fields]", la4p);
            dumpListFieldsDeep("[D2D3:la4p.deeplist]", la4p, 0);

            // 2. SnsInfo
            Method b1x = getSnsInfoMethod(la4p.getClass());
            if (b1x != null) {
                Object snsX = b1x.invoke(la4p);
                if (snsX != null) {
                    dumpListMethods("[D2D3:sns.methods]", snsX);
                    dumpAllNoArgMethods("[D2D3:sns.allmethods]", snsX);
                }
            }

            // 3. w45.a 实例：从 la4p 字段里拿一个活实例
            Object w45aInstance = findFieldByType(la4p, "w45.a");
            if (w45aInstance != null) {
                dumpListMethods("[D2D3:w45a.methods]", w45aInstance);
                dumpAllNoArgMethods("[D2D3:w45a.allmethods]", w45aInstance);
            }

            // 4. la4p 上已知的 no-arg 方法返回值：c1→SnsObject, getPostInfo→og4, h1→TimeLineObject
            dumpMethodResult("[D2D3:c1]", la4p, "c1");
            dumpMethodResult("[D2D3:getPostInfo]", la4p, "getPostInfo");
            dumpMethodResult("[D2D3:h1]", la4p, "h1");

            // 5. SnsInfo 上的 getPostInfo→og4, getTimeLine→TimeLineObject
            Method b1 = getSnsInfoMethod(la4p.getClass());
            if (b1 != null) {
                Object sns = b1.invoke(la4p);
                if (sns != null) {
                    dumpMethodResult("[D2D3:sns.getPostInfo]", sns, "getPostInfo");
                    dumpMethodResult("[D2D3:sns.getTimeLine]", sns, "getTimeLine");
                }
            }

            // 4. 也试 la4.n / la4.o 等内部类（la4p 某些 g 字段指向的不同类）
            for (String innerType : new String[]{"la4.i", "la4.n", "la4.o", "la4.f", "la4.l"}) {
                Object inner = findFieldByType(la4p, innerType);
                if (inner != null) {
                    dumpListMethods("[D2D3:" + innerType + ".methods]", inner);
                    dumpAllNoArgMethods("[D2D3:" + innerType + ".allmethods]", inner);
                }
            }

            // 5. 控制台高频类：wq.c1 / wq.y0 / ii5.b — 疑似 like/comment 元素类（k0 在 tinker 版已改名，跳过）
            ClassLoader cl = la4p.getClass().getClassLoader();
            for (String suspectClass : new String[]{"wq.c1", "wq.y0", "ii5.b"}) {
                dumpListMethodsOnClass("[D2D3:" + suspectClass + "]", cl, suspectClass);
                dumpAllMethodsOnClass("[D2D3:" + suspectClass + ".all]", cl, suspectClass);
            }

            // 6. 遍历 getCommentList() 全部元素（每 item 都试，直到找到非空）
            if (!sDiagSeen.contains("D2D3_CMLIST_DONE")) {
                try {
                    java.util.List<?> cmList = (java.util.List<?>) la4p.getClass().getMethod("getCommentList").invoke(la4p);
                    int sz = cmList.size();
                    Log.i(TAG, "[D2D3:cmList] size=" + sz);
                    if (sz > 0 && sDiagSeen.add("D2D3_CMLIST_DONE")) {
                        int i = 0;
                        for (Object entry : cmList) {
                            if (entry == null) continue;
                            StringBuilder esb = new StringBuilder(
                                    "[D2D3:cmList[" + i + "]] class=" + entry.getClass().getName());
                            Class<?> ec = entry.getClass();
                            for (int ed = 0; ec != null && ed < 4; ed++) {
                                for (java.lang.reflect.Field ef : ec.getDeclaredFields()) {
                                    try {
                                        ef.setAccessible(true);
                                        Object ev = ef.get(entry);
                                        if (ev instanceof String || ev instanceof Number)
                                            esb.append(" ").append(ef.getName()).append("=").append(ev);
                                    } catch (Throwable ignored2) {}
                                }
                                ec = ec.getSuperclass();
                            }
                            Log.i(TAG, esb.toString());
                            i++;
                            if (i >= 3) break;
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "[D2D3:cmList] err: " + t);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MF] D2D3 diag err: " + t);
        }
    }

    /** 递归遍历对象字段（含父类+嵌套，最深4层），找目标类名的实例 */
    private static Object findFieldByType(Object obj, String targetClassName) {
        return findFieldByTypeRecursive(obj, targetClassName, new java.util.HashSet<>(), 0);
    }

    private static Object findFieldByTypeRecursive(Object obj, String targetClassName,
                                                    Set<Integer> visited, int depth) {
        if (obj == null || depth >= 4) return null;
        int id = System.identityHashCode(obj);
        if (!visited.add(id)) return null;

        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 6; d++) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;
                    String name = v.getClass().getName();
                    if (targetClassName.equals(name)) return v;
                    if (!name.startsWith("java.") && !name.startsWith("android.")
                            && !name.startsWith("miui.") && !name.startsWith("dalvik.")) {
                        Object found = findFieldByTypeRecursive(v, targetClassName, visited, depth + 1);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /** 扫对象所有 no-arg 方法（含继承，最深6层），输出方法名 + 返回类型 */
    private static void dumpAllNoArgMethods(String prefix, Object obj) {
        if (obj == null) return;
        try {
            Class<?> cls = obj.getClass();
            prefix = prefix + " class=" + cls.getName();
            for (int d = 0; cls != null && d < 6; d++) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> ret = m.getReturnType();
                    String line = "  [d=" + d + "] " + m.getName() + " -> " + ret.getSimpleName();
                    Log.i(TAG, prefix + line);
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Log.w(TAG, prefix + " err: " + t);
        }
    }

    /** 调用 obj 上的 no-arg 方法，对返回值做 method dump */
    private static Object dumpMethodResult(String prefix, Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = null;
            Class<?> cls = obj.getClass();
            for (int d = 0; cls != null && d < 4; d++) {
                try { m = cls.getDeclaredMethod(methodName); break; }
                catch (NoSuchMethodException ignored) {}
                cls = cls.getSuperclass();
            }
            if (m == null) {
                Log.i(TAG, prefix + " no method " + methodName);
                return null;
            }
            m.setAccessible(true);
            Object result = m.invoke(obj);
            if (result == null) {
                Log.i(TAG, prefix + " " + methodName + "() -> null");
                return null;
            }
            dumpListMethods(prefix + ".methods", result);
            dumpAllNoArgMethods(prefix + ".allmethods", result);
            // 如果结果是 Collection，dump 第一个元素
            if (result instanceof java.util.Collection) {
                java.util.Collection<?> col = (java.util.Collection<?>) result;
                if (!col.isEmpty()) {
                    Object elem = col.iterator().next();
                    Log.i(TAG, prefix + " elemClass=" + elem.getClass().getName());
                    dumpListMethods(prefix + ".elem.methods", elem);
                    dumpAllNoArgMethods(prefix + ".elem.allmethods", elem);
                }
            }
            return result;
        } catch (Throwable t) {
            Log.w(TAG, prefix + " " + methodName + " err: " + t);
            return null;
        }
    }

    /** 扫对象实例的所有方法（含继承），返回 List/Collection 的 getter 同时写 logcat + web 控制台 */
    private static void dumpListMethods(String prefix, Object obj) {
        if (obj == null) return;
        StringBuilder sb = new StringBuilder(prefix)
                .append(" class=").append(obj.getClass().getName()).append("\n");
        try {
            Class<?> cls = obj.getClass();
            for (int d = 0; cls != null && d < 6; d++) {
                for (Method m : cls.getDeclaredMethods()) {
                    Class<?> ret = m.getReturnType();
                    if (!java.util.Collection.class.isAssignableFrom(ret)
                            && !ret.getName().contains("List")) continue;
                    String params = m.getParameterTypes().length == 0 ? "()" : "(params)";
                    String line = "  [d=" + d + "] " + m.getName() + params + " -> " + ret.getName();
                    sb.append(line).append("\n");
                    Log.i(TAG, prefix + line);
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            sb.append("  err: ").append(t).append("\n");
            Log.w(TAG, prefix + " err: " + t);
        }
        Bridge.getInstance().addRawFeedLine(sb.toString().replace("\n", " | "));
    }

    /** 按类名加载类，扫其方法中返回 List/Collection 的 getter，同时写 logcat + web 控制台 */
    private static void dumpListMethodsOnClass(String prefix, ClassLoader cl, String className) {
        StringBuilder sb = new StringBuilder(prefix).append(" class=").append(className).append("\n");
        try {
            Class<?> cls = cl.loadClass(className);
            for (int d = 0; cls != null && d < 4; d++) {
                for (Method m : cls.getDeclaredMethods()) {
                    Class<?> ret = m.getReturnType();
                    if (!java.util.Collection.class.isAssignableFrom(ret)
                            && !ret.getName().contains("List")) continue;
                    String params = m.getParameterTypes().length == 0 ? "()" : "(params)";
                    String line = "  [d=" + d + "] " + m.getName() + params + " -> " + ret.getName();
                    sb.append(line).append("\n");
                    Log.i(TAG, prefix + line);
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            sb.append("  err: ").append(t).append("\n");
            Log.w(TAG, prefix + " load err: " + t);
        }
        Bridge.getInstance().addRawFeedLine(sb.toString().replace("\n", " | "));
    }

    /** 按类名加载类，扫其所有 no-arg 方法（不过滤返回类型） */
    private static void dumpAllMethodsOnClass(String prefix, ClassLoader cl, String className) {
        try {
            Class<?> cls = cl.loadClass(className);
            for (int d = 0; cls != null && d < 4; d++) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> ret = m.getReturnType();
                    Log.i(TAG, prefix + " [d=" + d + "] " + m.getName() + " -> " + ret.getSimpleName());
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Log.w(TAG, prefix + " load err: " + t);
        }
    }

    /**
     * 递归遍历 obj 的所有字段，专门找 Collection 类型（即 List 藏身处）。
     * 对每个非基础类型的对象字段继续下探（最多 depth=3）。
     * 输出格式：[prefix] path.fieldName (FieldClass) List(sz=N,elemClass)
     */
    private static void dumpListFieldsDeep(String prefix, Object obj, int depth) {
        if (obj == null || depth > 3) return;
        Class<?> cls = obj.getClass();
        String pkgName = cls.getName();
        // 只深挖微信自己的类，跳过 java.* / android.* / androidx.*
        if (pkgName.startsWith("java.") || pkgName.startsWith("android.")
                || pkgName.startsWith("androidx.") || pkgName.startsWith("kotlin.")) return;

        for (int d = 0; cls != null && d < 6; d++) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;

                    if (v instanceof java.util.Collection) {
                        java.util.Collection<?> col = (java.util.Collection<?>) v;
                        String elemClass = col.isEmpty() ? "?" : col.iterator().next().getClass().getName();
                        Log.i(TAG, prefix + " [d=" + depth + "] LIST " + f.getName()
                                + " (" + f.getType().getSimpleName() + ")"
                                + " sz=" + col.size() + " elem=" + elemClass);
                    } else if (!(v instanceof String) && !(v instanceof Number)
                            && !(v instanceof Boolean) && !(v instanceof byte[])
                            && !v.getClass().isPrimitive()) {
                        String vPkg = v.getClass().getName();
                        // 只对微信内部类递归
                        if (!vPkg.startsWith("java.") && !vPkg.startsWith("android.")
                                && !vPkg.startsWith("androidx.") && !vPkg.startsWith("kotlin.")) {
                            Log.i(TAG, prefix + " [d=" + depth + "] OBJ  " + f.getName()
                                    + " (" + f.getType().getSimpleName() + ") → " + vPkg);
                            dumpListFieldsDeep(prefix + "." + f.getName(), v, depth + 1);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void cleanD2D3(Object item, Set<String> hidden) {
        try {
            String cn = item.getClass().getName();
            Object la4p = ITEM_FRIEND.equals(cn) ? getField(item, FIELD_INNER) : item;
            if (la4p == null) return;

            // 进入 SnsObject（agent 实证：la4p.c1() → SnsObject）
            Object snsObj = null;
            try {
                Method c1 = la4p.getClass().getMethod(METHOD_SNS_OBJ);
                snsObj = c1.invoke(la4p);
            } catch (Throwable ignored) {}
            if (snsObj == null) {
                // h1() 失败：打 la4p 所有返回微信内部类的无参方法，找 TimeLineObject getter
                if (sDiagSeen.add("D2D3_H1_MISS")) {
                    StringBuilder sb = new StringBuilder("[D2D3:h1_miss la4p=" + la4p.getClass().getName() + "]");
                    for (Method m : la4p.getClass().getMethods()) {
                        if (m.getParameterTypes().length != 0) continue;
                        String rn = m.getReturnType().getName();
                        if (!rn.startsWith("java.") && !rn.startsWith("android.")
                                && !rn.startsWith("androidx.") && !rn.equals("void")
                                && !rn.equals("boolean") && !rn.equals("int")
                                && !rn.equals("long") && !rn.equals("float")) {
                            sb.append(" ").append(m.getName()).append("→").append(m.getReturnType().getSimpleName());
                        }
                    }
                    Log.i(TAG, sb.toString());
                    Bridge.getInstance().addRawFeedLine(sb.toString());
                }
                return;
            }

            // 诊断：打 LikeUserList / CommentUserList 的 size（只跑一次，每次安装重置）
            if (sDiagSeen.add("D2D3_SIZE_H1")) {
                Object lk = getField(snsObj, FIELD_LIKE_LIST);
                Object cm = getField(snsObj, FIELD_COMMENT_LIST);
                int lkSz = (lk instanceof List) ? ((List<?>) lk).size() : -1;
                int cmSz = (cm instanceof List) ? ((List<?>) cm).size() : -1;
                String msg = "[D2D3:size snsObj=" + snsObj.getClass().getSimpleName()
                        + " like=" + lkSz + " cmt=" + cmSz + "]";
                Log.i(TAG, msg);
                Bridge.getInstance().addRawFeedLine(msg);
            }

            // D2: SnsObject.LikeUserList → LinkedList<e56>，e56.f435583d = wxid
            int d2 = cleanFieldList(snsObj, FIELD_LIKE_LIST,
                    FIELD_LIKE_COUNT, FIELD_LIKE_UC, hidden);
            // D3: SnsObject.CommentUserList → LinkedList<e56>
            int d3 = cleanFieldList(snsObj, FIELD_COMMENT_LIST,
                    FIELD_CMT_COUNT, FIELD_CMT_UC, hidden);

            if (d2 + d3 > 0) {
                InterceptCounter.getInstance().incF05("D2/D3(like=" + d2 + ",cmt=" + d3 + ")");
                Log.i(TAG, "[MF] D2/D3 cleaned like=" + d2 + " cmt=" + d3);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MF] D2/D3 err: " + t);
        }
    }

    /**
     * 直接通过字段名访问 SnsObject 上的 LikeUserList / CommentUserList，过滤密友条目后写回计数。
     * 返回删除数量（0 = 字段不存在或列表为空或无匹配）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int cleanFieldList(Object snsObj, String listField,
                                      String countField, String ucField, Set<String> hidden) {
        Object listObj = getField(snsObj, listField);
        if (!(listObj instanceof List)) return 0;
        List list = (List) listObj;
        if (list.isEmpty()) return 0;

        int removed = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Object entry = list.get(i);
            if (entry == null) continue;
            if (isHiddenListEntry(entry, hidden)) {
                try { list.remove(i); removed++; }
                catch (UnsupportedOperationException e) {
                    // protobuf 不可变 list：换 ArrayList 复制
                    List copy = new ArrayList(list);
                    copy.remove(i);
                    setField(snsObj, listField, copy);
                    removed++;
                    break;
                }
            }
        }
        if (removed > 0) {
            writebackCount(snsObj, countField, removed);
            writebackCount(snsObj, ucField, removed);
        }
        return removed;
    }

    /** 反射写字段 */
    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 8; d++) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (NoSuchFieldException ignored) { cls = cls.getSuperclass(); }
            catch (Throwable t) { break; }
        }
    }

    /**
     * 通过 getter 方法获取 list，过滤密友条目后用反射写回。
     * 返回 -1 = 方法不存在；>= 0 = 删除数量（可能为 0）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int cleanListViaGetter(Object la4p, String getterName, Set<String> hidden) {
        try {
            Method getter = la4p.getClass().getMethod(getterName);
            Object raw = getter.invoke(la4p);
            if (!(raw instanceof List)) return -1;
            List list = (List) raw;
            if (list.isEmpty()) return 0;

            // 收集要删的
            List<Object> toRemove = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                if (entry != null && isHiddenListEntry(entry, hidden)) toRemove.add(entry);
            }
            if (toRemove.isEmpty()) return 0;

            // protobuf list 通常不可直接 remove，尝试；失败则找 backing 字段
            int removed = 0;
            boolean directOk = false;
            try {
                for (Object e : toRemove) { list.remove(e); removed++; }
                directOk = true;
            } catch (UnsupportedOperationException ignored) {}

            if (!directOk) {
                // 找 backing 字段（protobuf 通常是 fieldName_ 或单字母）
                removed = replaceBackingField(la4p, getter, toRemove, list);
            }
            Log.i(TAG, "[MF] D2/D3 " + getterName + " removed=" + removed);
            return removed;
        } catch (NoSuchMethodException e) {
            return -1; // 方法不存在
        } catch (Throwable t) {
            Log.w(TAG, "[MF] cleanListViaGetter " + getterName + " err: " + t);
            return 0;
        }
    }

    /** 通过反射找 la4p 上与 getter 返回值相同的 List 字段，替换为过滤后的副本 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int replaceBackingField(Object la4p, Method getter,
                                            List<Object> toRemove, List original) {
        Class<?> cls = la4p.getClass();
        for (int d = 0; cls != null && d < 8; d++) {
            for (Field f : cls.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(la4p);
                    if (v == original || v == getter.invoke(la4p)) {
                        List copy = new ArrayList(original);
                        copy.removeAll(toRemove);
                        f.set(la4p, copy);
                        return toRemove.size();
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int cleanItemList(Object target, String listField,
                                     String countField, String ucField, Set<String> hidden) {
        Object listObj = getField(target, listField);
        if (!(listObj instanceof List)) return 0;
        List list = (List) listObj;
        if (list.isEmpty()) return 0;

        int removed = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Object entry = list.get(i);
            if (entry == null) continue;
            if (isHiddenListEntry(entry, hidden)) {
                try {
                    list.remove(i);
                    removed++;
                } catch (UnsupportedOperationException ignored) {}
            }
        }
        if (removed == 0) return 0;

        writebackCount(target, countField, removed);
        writebackCount(target, ucField, removed);
        return removed;
    }

    private static boolean isHiddenListEntry(Object entry, Set<String> hidden) {
        // e56 确认路径：f435583d = wxid（proto field 1）
        for (String fn : ACTOR_FIELD_NAMES) {
            Object u = getField(entry, fn);
            if (u instanceof String && !((String) u).isEmpty() && hidden.contains(u)) return true;
        }
        return false;
    }

    /** 从 like/comment entry 提取 wxid（不过滤名单，用于 emit telemetry） */
    static String extractWxidFromEntry(Object entry) {
        if (entry == null) return null;
        for (String fn : ACTOR_FIELD_NAMES) {
            Object u = getField(entry, fn);
            if (u instanceof String) {
                String s = (String) u;
                if (s.startsWith("wxid_") && s.length() > 5) return s;
            }
        }
        return null;
    }

    private static void writebackCount(Object target, String fieldName, int delta) {
        try {
            Class<?> cls = target.getClass();
            for (int d = 0; cls != null && d < 8; d++) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    int cur = f.getInt(target);
                    f.setInt(target, Math.max(0, cur - delta));
                    return;
                } catch (NoSuchFieldException ignored) {
                    cls = cls.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** 调用 no-arg 方法返回原始结果，不做 method dump */
    private static Object dumpMethodResultRaw(String prefix, Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Throwable t) {
            Log.w(TAG, prefix + " " + methodName + " err: " + t);
            return null;
        }
    }

    /** 调用 no-arg 方法，返回 String 值或 null */
    private static String invokeStringGetter(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object r = m.invoke(obj);
            return r instanceof String ? (String) r : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int getIntField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable ignored) {
            return -999;
        }
    }

    private static String extractPreview(Object obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder(" |");
        int found = 0;
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 4 && found < 3; d++) {
            for (Field f : cls.getDeclaredFields()) {
                if (found >= 3) break;
                if (f.getType() != String.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof String && !((String) v).isEmpty()) {
                        String s = (String) v;
                        sb.append(" .").append(f.getName()).append("=")
                          .append(s.length() > 30 ? s.substring(0, 30) + "…" : s);
                        found++;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return found > 0 ? sb.toString() : "";
    }

    private static void dumpItem(Object item, Bridge bridge) {
        StringBuilder sb = new StringBuilder();
        sb.append("class=").append(item.getClass().getName()).append("\n");
        for (Field f : item.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object v = f.get(item);
                String vStr = v == null ? "null" : v.toString();
                if (vStr.length() > 80) vStr = vStr.substring(0, 80) + "…";
                sb.append("  .").append(f.getName())
                  .append(" (").append(f.getType().getSimpleName()).append(") = ")
                  .append(vStr).append("\n");
            } catch (Throwable ignored) {}
        }
        bridge.addItemDump(sb.toString());
    }

    /** dumpMethodResult 的 Collection 结果 → dump 第一个元素的字段 */
    private static void dumpFirstElemFields(Object result, String source, String expectedElemClass) {
        if (!(result instanceof java.util.Collection)) return;
        java.util.Collection<?> col = (java.util.Collection<?>) result;
        if (col.isEmpty()) return;
        Object elem = col.iterator().next();
        Log.i(TAG, "[D2D3:" + source + ".fields] elemClass=" + elem.getClass().getName());
        dumpAllFields("[D2D3:" + source + ".fields]", elem);
    }

    /** 打出对象所有层级的字段名、类型，写进 web raw feed */
    private static void dumpAllFields(String label, Object obj) {
        StringBuilder sb = new StringBuilder(label).append("\n");
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 8; d++) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    String typeName = f.getType().getSimpleName();
                    String valStr = v == null ? "null"
                            : (v instanceof java.util.Collection
                                    ? "List(sz=" + ((java.util.Collection<?>) v).size() + "," + v.getClass().getSimpleName() + ")"
                                    : v.toString().length() > 50 ? v.toString().substring(0, 50) + "…" : v.toString());
                    sb.append("  [").append(d).append("] ").append(f.getName())
                      .append(" (").append(typeName).append(") = ").append(valStr).append("\n");
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        Log.i(TAG, sb.toString());
        Bridge.getInstance().addRawFeedLine(sb.toString().replace("\n", " | "));
    }

    static String extractNickname(Object item) {
        if (item == null) return "";
        if (ITEM_FRIEND.equals(item.getClass().getName())) {
            item = getField(item, FIELD_INNER);
            if (item == null) return "";
        }
        try {
            Method m = getNicknameMethod(item.getClass());
            if (m == null) return "";
            Object r = m.invoke(item);
            return (r instanceof String) ? (String) r : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static Method getNicknameMethod(Class<?> cls) {
        if (sNicknameMethod != null) return sNicknameMethod;
        try {
            for (Method m : cls.getMethods()) {
                if (METHOD_NICKNAME.equals(m.getName()) && m.getParameterTypes().length == 0
                        && m.getReturnType() == String.class) {
                    m.setAccessible(true);
                    sNicknameMethod = m;
                    return m;
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    private static Method getSnsInfoMethod(Class<?> cls) {
        if (sSnsInfoMethod != null) return sSnsInfoMethod;
        try {
            for (Method m : cls.getMethods()) {
                if (METHOD_SNS_OBJ.equals(m.getName()) && m.getParameterTypes().length == 0) {
                    m.setAccessible(true);
                    sSnsInfoMethod = m;
                    return m;
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    private static Object getField(Object obj, String name) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        for (int d = 0; cls != null && d < 8; d++) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
