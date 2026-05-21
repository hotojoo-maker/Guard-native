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

        // v2 兜底：Event ctor + View（已改为精准 wxid 过滤，非盲拦截）
        installEventBlocker(lpparam, CLS_WECHAT_TAB_EVT);
        installEventBlocker(lpparam, CLS_TAB_CHANGE_EVT);
        installFinderRedDotViewBlocker(lpparam);

        // v8/v9 主路径：ns.c 聚合桶 + FindMoreFriendsUI.onResume 主动刷新
        installNsCAggregationBlocker(lpparam);

        // v10 精准路径：w1 写入拦截
        installW1InteractionFilter(lpparam);

        // v11 出口封堵：SnsCommentStorage int/long getter → 0
        installSnsCommentStorageHook(lpparam);

        // v12 新增：SnsMsgUIWithAll 进入时过滤密友列表 + 同步清零
        installSnsMsgUIFilter(lpparam);

        // boot-time 追溯清零
        retroactiveZeroOnBoot(lpparam);

        Log.i(TAG, "[MRD] install done (v12: +SnsMsgUIFilter +onResume refresh)");
    }

    /**
     * v5 主路径：hook SnsMsgUI 父类（不是子类），onCreate/onResume after 过滤 List。
     * 同时对 SnsMsgUIWithRelevance 也挂（covers 两个入口）。
     */
    private static void installSnsMsgUIFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
                // 用户实证 2026-05-21：进入红点消费界面的真实类
                "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithAll",
                "com.tencent.mm.plugin.sns.ui.SnsMsgUI",
                "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance",
        };
        for (String cn : candidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(cn);
                int hooked = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    String mn = m.getName();
                    if (!("onCreate".equals(mn) || "onResume".equals(mn))) continue;
                    if (m.getParameterTypes().length > 1) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleSnsMsgUIEnter(param.thisObject, mn, cn);
                        }
                    });
                    hooked++;
                }
                Log.i(TAG, "[MRD:smsg] " + cn + " hooked " + hooked);
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "[MRD:smsg] class not found: " + cn);
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:smsg] " + cn + " failed: " + t);
            }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int filterListFields(Object instance, java.util.Set<String> hidden) {
        int total = 0;
        Class<?> cls = instance.getClass();
        for (int d = 0; cls != null && d < 5; d++) {
            if (cls.getName().startsWith("android.")) break;
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (!java.util.List.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object listObj = f.get(instance);
                    if (!(listObj instanceof java.util.List)) continue;
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
                // getCount → 0 in HIDDEN
                for (Method m : cls.getMethods()) {
                    if (!"getCount".equals(m.getName())) continue;
                    if (m.getParameterTypes().length != 0) continue;
                    if (m.getReturnType() != int.class) continue;
                    if (m.getDeclaringClass() == Object.class) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (sDiagSeen.add("adapter_getCount")) {
                                Log.i(TAG, "[MRD:adapter:diag] getCount, state="
                                        + StateMachine.getInstance().getStateName());
                            }
                            if (!AppConfig.getInstance().isMomentsRedDotEnabled()) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            param.setResult(0);
                            if (sDiagSeen.add("adapter_block")) {
                                Log.i(TAG, "[MRD:adapter] getCount → 0");
                            }
                            InterceptCounter.getInstance().incF05("MRD-adapter-getCount");
                        }
                    });
                }
                Log.i(TAG, "[MRD:adapter] " + adapterClass + " probed");
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:adapter] " + adapterClass + " failed: " + t);
            }
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
    private static void zeroW1FieldY(Object inst) {
        Class<?> cls = inst.getClass();
        for (int d = 0; cls != null && cls != Object.class && d < 4; d++) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (!"y".equals(f.getName())) continue;
                if (f.getType() != int.class && f.getType() != long.class) continue;
                try {
                    f.setAccessible(true);
                    int before = f.getInt(inst);
                    if (before > 0) {
                        f.setInt(inst, 0);
                        Log.i(TAG, "[MRD:boot:w1] f178674y retroactive zero: "
                                + before + " → 0");
                        InterceptCounter.getInstance().incF05("MRD-boot-w1");
                    } else {
                        Log.i(TAG, "[MRD:boot:w1] f178674y already 0, skip");
                    }
                    return;
                } catch (Throwable t) {
                    Log.w(TAG, "[MRD:boot:w1] field y zero err: " + t);
                    return;
                }
            }
            cls = cls.getSuperclass();
        }
        // 字段 y 未找到：dump 全部 int 字段供定位
        if (sDiagSeen.add("w1_field_y_not_found")) {
            StringBuilder sb = new StringBuilder(
                    "[MRD:boot:w1] ⚠ field y not found on " + inst.getClass().getName()
                    + ", all int fields:\n");
            Class<?> dc = inst.getClass();
            for (int d = 0; dc != null && dc != Object.class && d < 4; d++) {
                for (java.lang.reflect.Field f : dc.getDeclaredFields()) {
                    if (f.getType() != int.class && f.getType() != long.class) continue;
                    try {
                        f.setAccessible(true);
                        sb.append("  [d").append(d).append("] ")
                          .append(f.getName()).append(" = ").append(f.getInt(inst)).append("\n");
                    } catch (Throwable ignored) {}
                }
                dc = dc.getSuperclass();
            }
            Log.w(TAG, sb.toString());
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
                // v12: hook FindMoreFriendsUI.onResume（进发现页时刷新）
                for (Method m : fmfUi.getMethods()) {
                    if (!"onResume".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 缓存 FMF 实例
                            if (sFMFInstance == null) sFMFInstance = param.thisObject;
                            boolean en2 = AppConfig.getInstance().isMomentsRedDotEnabled();
                            boolean act2 = StateMachine.getInstance().isActive();
                            Log.i(TAG, "[MRD:fmf:onResume] en=" + en2 + " act=" + act2
                                    + " wxids=" + Bridge.getInstance().getWxids().size());
                            if (!en2) return;
                            if (!act2) return;
                            if (Bridge.getInstance().getWxids().isEmpty()) return;
                            suppressRedDotIfHiddenFriend(param.thisObject, nsC, finalWw2C);
                            Log.i(TAG, "[MRD:discover] FindMoreFriends.onResume badge refresh");
                        }
                    });
                    Log.i(TAG, "[MRD:ns.c] FindMoreFriendsUI.onResume() hooked (v12)");
                    fmfHooked++;
                    break;
                }
                if (fmfHooked == 0) Log.w(TAG, "[MRD:ns.c] FindMoreFriendsUI: no methods hooked");
            } catch (Throwable t) {
                Log.w(TAG, "[MRD:ns.c] L1/onResume hook failed: " + t);
            }

            // v13: hook LauncherUI.onResume → 冷启动时通过 sFMFInstance 调 g1() 清 tab 红点
            // 修复：ns.c.b 在 8.0.71 不控制视觉红点（find_reddot_field.js 实证），
            //       必须在 FMF 实例上调 g1("album_dyna_photo_ui_title", false) 才有效。
            // 冷启动时序：LauncherUI.onResume → (之后) FMF.L1 / FMF.onResume
            // 若 sFMFInstance 尚未就绪则延迟 300ms 重试一次（tab lazy init）
            try {
                Class<?> launcher = lpparam.classLoader.loadClass(
                        "com.tencent.mm.ui.LauncherUI");
                for (Method m : launcher.getMethods()) {
                    if (!"onResume".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
                    final Class<?> fFmfUi = finalFmfClass;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 诊断入口日志（每次 onResume 都打，帮助确认 hook 在执行）
                            boolean en = AppConfig.getInstance().isMomentsRedDotEnabled();
                            boolean act = StateMachine.getInstance().isActive();
                            int wxN = Bridge.getInstance().getWxids().size();
                            Object fmfSnap = sFMFInstance;
                            Log.i(TAG, "[MRD:launcher:entry] en=" + en + " act=" + act
                                    + " wxids=" + wxN + " fmf=" + (fmfSnap != null ? "ready" : "null"));
                            if (!en) return;
                            if (!act) return;
                            if (wxN == 0) return;
                            // 尝试立即清除（sFMFInstance 有值时）
                            Object fmf = sFMFInstance;
                            if (fmf != null) {
                                clearFMFBadgeIfNeeded(fmf, "launcher-immediate");
                                return;
                            }
                            // sFMFInstance 为 null：冷启动 FMF tab 尚未初始化
                            // 延迟 300ms 等 tab lazy init 完成后再试
                            final Object launcherInst = param.thisObject;
                            android.os.Handler h = new android.os.Handler(
                                    android.os.Looper.getMainLooper());
                            h.postDelayed(new Runnable() {
                                @Override public void run() {
                                    Object fmf2 = sFMFInstance;
                                    if (fmf2 != null) {
                                        clearFMFBadgeIfNeeded(fmf2, "launcher-delayed");
                                        return;
                                    }
                                    // 最后手段：从 LauncherUI 字段中扫描 FMF 实例
                                    Object found = findFMFFromLauncher(launcherInst, fFmfUi);
                                    if (found != null) {
                                        sFMFInstance = found;
                                        clearFMFBadgeIfNeeded(found, "launcher-scan");
                                    } else if (sDiagSeen.add("fmf_not_found_launcher")) {
                                        Log.w(TAG, "[MRD:launcher] FMF instance not found after 300ms");
                                    }
                                }
                            }, 300);
                        }
                    });
                    Log.i(TAG, "[MRD:ns.c] LauncherUI.onResume() hooked (v13 g1-based)");
                    break;
                }
            } catch (Throwable t2) {
                Log.w(TAG, "[MRD:ns.c] LauncherUI.onResume hook failed: " + t2);
            }

            Log.i(TAG, "[MRD:ns.c] aggregation blocker v12b installed");
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
            if (!fmfE) return; // 红点没亮，不需要处理
            // 直接清除（激进 v1）
            callG1OnUi(fmf, false);
            if (sDiagSeen.add("fmf_badge_cleared_" + tag)) {
                Log.i(TAG, "[MRD:" + tag + "] FMF.E=true → g1(false) called (y=" + y + " x=" + x + ")");
            }
            InterceptCounter.getInstance().incF05("MRD-" + tag);
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
                        m.invoke(ui, "album_dyna_photo_ui_title", show);
                        if (sDiagSeen.add("g1_called_" + show)) {
                            Log.i(TAG, "[MRD:g1] g1(album_dyna_photo_ui_title, " + show
                                    + ") on " + c.getSimpleName());
                        }
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

                        if (sDiagSeen.add("w1_v2_first")) {
                            Log.i(TAG, "[MRD:w1] v2 arg[1]=" + wxid);
                        }
                        if (wxid == null || !Bridge.getInstance().shouldHideId(wxid)) return;

                        // 密友互动 → 跳过 → w1.y 不递增 → ns.c.b 不被置 true → 红点不出现
                        param.setResult(Boolean.FALSE);
                        Log.i(TAG, "[MRD:w1] blocked v2 wxid=" + wxid);
                        InterceptCounter.getInstance().incF05("MRD-w1-v2");
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
                if (rt != int.class && rt != long.class
                        && rt != Integer.class && rt != Long.class) continue;

                final String fmn = mn;
                final Class<?> fRt = rt;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
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
    private static boolean isFilteringActive() {
        if (!StateMachine.getInstance().isActive()) return false;
        if (Bridge.getInstance().allHiddenIds().isEmpty()) return false;
        return true;
    }
}
