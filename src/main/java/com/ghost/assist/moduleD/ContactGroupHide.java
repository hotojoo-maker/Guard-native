package com.ghost.assist.moduleD;

import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 通讯录「群聊」独立页（ChatroomContactUI）密群隐藏 —— P_CV1-G（2026-05-29 收口）。
 *
 * 与主通讯录（ContactFilter / AddressLiveList / ik3.t0）是**两套独立机制**：
 *   页面 ChatroomContactUI；adapter = com.tencent.mm.ui.contact.s0 extends com.tencent.mm.ui.s9（CursorAdapter）；
 *   item = com.tencent.mm.storage.z3；群 id = z3.c1()=xxx@chatroom；数据源 = s9.f SQLiteCursor。
 *
 * 隐藏方案（机制 + 三坑详见 docs/A3_GROUP_FILTER_IMPL.md §六）：
 *   cursor 层 hook 够不着（g() 被 R8 内联、t() 进页面不重调）→ hook Adapter 框架方法
 *   getCount/getItem/getView（虚表调、不被内联）做位置重映射。getCount 隐藏态返回可见数 +
 *   按 username 列建「可见位置→真位置」映射；getItem/getView 把 position 重映射跳过隐藏行。
 *   ⚠️ classloader 分裂：必须从 live adapter 实例的真 Class 装 hook（不能 lpparam.loadClass）。
 *
 * footer「N个群聊」计数器走独立 SQL（ContactCountView），不受本类影响，由 ContactDiscoveryHook
 *   按 {@link #sGroupHiddenRemoved}/{@link #sGroupVisibleCount} 直接改文本（方案 B）。
 *
 * 【门控锁定】只读 StateMachine.isActive() + Bridge.allHiddenIds()；不写状态机 / 不碰授权。
 */
final class ContactGroupHide {

    private static final String TAG = ContactFilter.TAG;
    private static final String CHATROOM_S0 = "com.tencent.mm.ui.contact.s0";

    private static final WeakHashMap<Object, int[]> sGroupVisMap = new WeakHashMap<>();
    private static volatile boolean sGroupHooked = false; // 群聊 adapter hook 只装一次
    static volatile int sGroupHiddenRemoved = 0;          // 本页隐藏掉的群数（footer 计数器扣减用，V 态归零）
    static volatile int sGroupVisibleCount = -1;          // 本页过滤后可见群数（footer 计数器绝对目标，幂等）

    private ContactGroupHide() {}

    /**
     * 由 ContactDiscoveryHook 从 live 群聊 adapter（s0 实例，Tinker classloader 真类）回调。
     * 用实例的真 Class 装 getCount/getItem/getView hook，绕开 lpparam 影子类 classloader 分裂。
     */
    static void hookGroupAdapterFromLive(Object adapter) {
        if (sGroupHooked || adapter == null) return;
        try {
            final Class<?> s0 = adapter.getClass();
            Method mCount = findMethodUp(s0, "getCount");
            Method mItem  = findMethodUp(s0, "getItem", int.class);
            if (mCount == null || mItem == null) {
                Log.w(TAG, "[CGF] getCount/getItem not found on live " + s0.getName() + "; abort");
                return;
            }
            sGroupHooked = true;

            XposedBridge.hookMethod(mCount, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!CHATROOM_S0.equals(param.thisObject.getClass().getName())) return;
                        boolean active = StateMachine.getInstance().isActive();
                        Set<String> hidden = active ? Bridge.getInstance().allHiddenIds() : null;
                        boolean want = false;
                        if (active && hidden != null && !hidden.isEmpty()) {
                            for (String h : hidden) {
                                if (h != null && h.endsWith("@chatroom")) { want = true; break; }
                            }
                        }
                        if (!want) {
                            synchronized (sGroupVisMap) { sGroupVisMap.remove(param.thisObject); }
                            sGroupHiddenRemoved = 0; // V 态归零，防 footer 误扣
                            return;
                        }
                        int[] map = buildGroupVisibleMap(param.thisObject, hidden);
                        if (map == null) return;
                        synchronized (sGroupVisMap) { sGroupVisMap.put(param.thisObject, map); }
                        Object ret = param.getResult();
                        int total = (ret instanceof Integer) ? (Integer) ret : map.length;
                        sGroupHiddenRemoved = Math.max(0, total - map.length);
                        sGroupVisibleCount = map.length;
                        if (map.length != total) {
                            param.setResult(map.length);
                            Log.i(TAG, "[CGF] getCount " + total + "→" + map.length
                                    + " hiddenRemoved=" + (total - map.length));
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "[CGF] getCount fail: " + t);
                    }
                }
            });

            XC_MethodHook remap = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!CHATROOM_S0.equals(param.thisObject.getClass().getName())) return;
                        int[] map;
                        synchronized (sGroupVisMap) { map = sGroupVisMap.get(param.thisObject); }
                        if (map == null || param.args.length == 0
                                || !(param.args[0] instanceof Integer)) return;
                        int p = (Integer) param.args[0];
                        if (p >= 0 && p < map.length) param.args[0] = map[p];
                    } catch (Throwable ignored) {}
                }
            };
            XposedBridge.hookMethod(mItem, remap);
            Method mView = findMethodUp(s0, "getView", int.class,
                    android.view.View.class, android.view.ViewGroup.class);
            if (mView != null) XposedBridge.hookMethod(mView, remap);

            Log.i(TAG, "[CGF] adapter-level filter installed (count@"
                    + mCount.getDeclaringClass().getSimpleName() + " item@"
                    + mItem.getDeclaringClass().getSimpleName()
                    + " view=" + (mView != null) + ")");
            // hook 在首次渲染后才装上 → 主动 notify 一次让 ListView 重算 getCount（应用过滤）
            try {
                adapter.getClass().getMethod("notifyDataSetChanged").invoke(adapter);
                Log.i(TAG, "[CGF] notifyDataSetChanged after hook");
            } catch (Throwable ignored) {}
        } catch (Throwable e) {
            sGroupHooked = false;
            Log.w(TAG, "[CGF] install fail: " + e);
        }
    }

    /** 从 cls 往上走继承链找方法（处理 R8 把方法定义在父类的情况）。 */
    private static Method findMethodUp(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException e) { c = c.getSuperclass(); }
        }
        return null;
    }

    /** 读 adapter 的 cursor 字段 f，按 username 列建「可见位置→真位置」映射（跳过 hidden）。 */
    private static int[] buildGroupVisibleMap(Object adapter, Set<String> hidden) {
        try {
            Field ff = ContactFilter.findFieldInHierarchy(adapter.getClass(), "f");
            if (ff == null) return null;
            ff.setAccessible(true);
            Object cobj = ff.get(adapter);
            if (!(cobj instanceof android.database.Cursor)) return null;
            android.database.Cursor c = (android.database.Cursor) cobj;
            int n = c.getCount();
            int col;
            try { col = c.getColumnIndex("username"); } catch (Throwable t) { return null; }
            if (col < 0) return null;
            int saved = -1;
            try { saved = c.getPosition(); } catch (Throwable ignored) {}
            int[] tmp = new int[Math.max(n, 0)];
            int vis = 0;
            for (int i = 0; i < n; i++) {
                String u = null;
                try { if (c.moveToPosition(i)) u = c.getString(col); } catch (Throwable ignored) {}
                if (u != null && hidden.contains(u)) continue;
                tmp[vis++] = i;
            }
            try { c.moveToPosition(saved); } catch (Throwable ignored) {}
            int[] m = new int[vis];
            System.arraycopy(tmp, 0, m, 0, vis);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }
}
