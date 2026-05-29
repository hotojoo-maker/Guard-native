package com.ghost.assist.debug;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 通讯录「群聊」页（ChatroomContactUI / adapter s0）的**只读诊断探针**。
 *
 * 【隔离说明】P_CV1-G（2026-05-29）：这是定位群聊 adapter 数据结构时用的探针，
 *   功能已收口（隐藏 + 计数器装机通过）。探针从 moduleD/ContactDiscoveryHook 隔离到 debug 包，
 *   默认 {@link #ENABLED}=false（不输出、零开销）。后续若群聊页结构变动需重新定位，
 *   把 ENABLED 设 true、重新装机即可看到 [CRP*] 日志。
 *
 * 纯反射 + Log，不写任何业务状态、不 hook。调用方传入已解包的真 adapter 实例。
 */
public final class ContactGroupProbe {

    private static final String TAG = "NCL";

    /** 默认关闭。需要重新定位群聊 adapter 结构时手动改 true 再装机。 */
    public static volatile boolean ENABLED = false;

    private ContactGroupProbe() {}

    /** 一次性 dump 已解包的群聊 adapter：getCount/getItem 样本 + List 字段 + 全字段 + 持有器内容。 */
    public static void dumpAdapter(Object adapter) {
        if (!ENABLED || adapter == null) return;
        dumpListAdapterItems(adapter);
        dumpAdapterListFields(adapter);
        dumpAdapterAllFields(adapter);
        dumpHolderContents(adapter);
    }

    private static void dumpListAdapterItems(Object adapter) {
        try {
            int count;
            try {
                Object c = adapter.getClass().getMethod("getCount").invoke(adapter);
                count = (c instanceof Integer) ? (Integer) c : -1;
            } catch (Throwable t) { count = -1; }
            Log.i(TAG, "[CRP] realAdapterCls=" + adapter.getClass().getName() + " getCount=" + count);
            Method getItem;
            try { getItem = adapter.getClass().getMethod("getItem", int.class); }
            catch (Throwable t) { return; }
            int n = (count > 0) ? Math.min(count, 5) : 0;
            for (int i = 0; i < n; i++) {
                try {
                    Object it = getItem.invoke(adapter, i);
                    Log.i(TAG, "[CRP] getItem[" + i + "] cls="
                            + (it != null ? it.getClass().getName() : "null") + " " + dumpItemStrings(it));
                    if (it != null) {
                        Log.i(TAG, "[CRP] getItem[" + i + "] methods=" + dumpItemStringMethods(it));
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w(TAG, "[CRP] dumpListAdapterItems err: " + t);
        }
    }

    private static String tryC1(Object obj) {
        if (obj == null) return "null";
        try {
            Object r = obj.getClass().getMethod("c1").invoke(obj);
            return r != null ? r.toString() : "null";
        } catch (Throwable t) { return "-"; }
    }

    private static void dumpHolderContents(Object adapter) {
        try {
            Class<?> cls = adapter.getClass();
            int holders = 0;
            while (cls != null && cls != Object.class && holders < 8) {
                Field[] fields;
                try { fields = cls.getDeclaredFields(); }
                catch (Throwable t) { break; }
                for (Field f : fields) {
                    if (holders >= 8) break;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(adapter);
                        if (v == null) continue;
                        Collection<?> coll = null;
                        if (v instanceof Map) {
                            coll = ((Map<?, ?>) v).values();
                        } else if (v instanceof Collection) {
                            coll = (Collection<?>) v;
                        } else {
                            try {
                                Object szO = v.getClass().getMethod("size").invoke(v);
                                Method get = v.getClass().getMethod("get", int.class);
                                int sz = (szO instanceof Integer) ? (Integer) szO : 0;
                                StringBuilder sb = new StringBuilder();
                                int n = Math.min(sz, 5);
                                for (int i = 0; i < n; i++) {
                                    Object e = get.invoke(v, i);
                                    sb.append(" [").append(i).append("]")
                                      .append(e != null ? e.getClass().getSimpleName() : "null")
                                      .append("/c1=").append(tryC1(e));
                                }
                                Log.i(TAG, "[CRP:holder] " + cls.getSimpleName() + "." + f.getName()
                                        + " (listLike " + v.getClass().getName() + ") sz=" + sz + sb);
                                holders++;
                                continue;
                            } catch (Throwable ignored) { continue; }
                        }
                        if (coll != null) {
                            StringBuilder sb = new StringBuilder();
                            int i = 0;
                            for (Object e : coll) {
                                if (i >= 5) break;
                                sb.append(" [").append(i).append("]")
                                  .append(e != null ? e.getClass().getSimpleName() : "null")
                                  .append("/c1=").append(tryC1(e));
                                i++;
                            }
                            Log.i(TAG, "[CRP:holder] " + cls.getSimpleName() + "." + f.getName()
                                    + " (" + v.getClass().getName() + ") sz=" + coll.size() + sb);
                            holders++;
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Log.w(TAG, "[CRP:holder] err: " + t);
        }
    }

    private static void dumpAdapterAllFields(Object adapter) {
        try {
            Class<?> cls = adapter.getClass();
            int logged = 0;
            while (cls != null && cls != Object.class && logged < 40) {
                Field[] fields;
                try { fields = cls.getDeclaredFields(); }
                catch (Throwable t) { break; }
                for (Field f : fields) {
                    if (logged >= 40) break;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(adapter);
                        if (v == null) continue;
                        String desc = v.getClass().getName();
                        if (v.getClass().isArray()) {
                            int len = java.lang.reflect.Array.getLength(v);
                            String elem = "?";
                            if (len > 0) {
                                Object e0 = java.lang.reflect.Array.get(v, 0);
                                elem = (e0 != null) ? e0.getClass().getName() : "null";
                            }
                            desc += " ARRAY len=" + len + " elem0=" + elem;
                        } else {
                            try {
                                Object sz = v.getClass().getMethod("size").invoke(v);
                                desc += " size=" + sz;
                            } catch (Throwable ignored) {}
                        }
                        Log.i(TAG, "[CRP:allF] " + cls.getSimpleName() + "." + f.getName() + " = " + desc);
                        logged++;
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Log.w(TAG, "[CRP:allF] err: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void dumpAdapterListFields(Object adapter) {
        Class<?> cls = adapter.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields;
            try { fields = cls.getDeclaredFields(); }
            catch (Throwable t) { break; }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(adapter);
                    if (!(v instanceof List)) continue;
                    List<Object> list = (List<Object>) v;
                    int sz = list.size();
                    String firstCls = "empty";
                    StringBuilder samples = new StringBuilder();
                    if (sz > 0) {
                        Object first = null;
                        try { first = list.get(0); } catch (Throwable ignored) {}
                        firstCls = (first != null) ? first.getClass().getName() : "null";
                        int n = Math.min(sz, 5);
                        for (int i = 0; i < n; i++) {
                            Object it;
                            try { it = list.get(i); } catch (Throwable ignored) { break; }
                            samples.append(" [").append(i).append("]").append(dumpItemStrings(it));
                        }
                    }
                    Log.i(TAG, "[CRP] field=" + f.getName() + " decl=" + cls.getSimpleName()
                            + " sz=" + sz + " first=" + firstCls + " samples=" + samples);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static String dumpItemStringMethods(Object item) {
        StringBuilder sb = new StringBuilder("{");
        try {
            int cnt = 0;
            Class<?> cls = item.getClass();
            while (cls != null && cls != Object.class && cnt < 12) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (cnt >= 12) break;
                    if (m.getParameterTypes().length != 0) continue;
                    if (!"java.lang.String".equals(m.getReturnType().getName())) continue;
                    try {
                        m.setAccessible(true);
                        Object r = m.invoke(item);
                        if (r == null) continue;
                        String s = r.toString();
                        if (s.isEmpty()) continue;
                        if (s.length() > 30) s = s.substring(0, 30);
                        sb.append(m.getName()).append("()=").append(s).append(',');
                        cnt++;
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return sb.append('}').toString();
    }

    private static String dumpItemStrings(Object item) {
        if (item == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        try {
            int cnt = 0;
            Class<?> cls = item.getClass();
            while (cls != null && cls != Object.class && cnt < 6) {
                for (Field f : cls.getDeclaredFields()) {
                    if (cnt >= 6) break;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(item);
                        if (v instanceof String) {
                            String s = (String) v;
                            if (s.length() > 30) s = s.substring(0, 30);
                            sb.append(f.getName()).append('=').append(s).append(',');
                            cnt++;
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return sb.append('}').toString();
    }
}
