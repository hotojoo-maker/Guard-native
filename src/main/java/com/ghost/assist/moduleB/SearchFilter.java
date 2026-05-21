package com.ghost.assist.moduleB;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.UiContextTracker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Global search result filter.
 *
 * Runtime evidence:
 *   z15.ef6 d=<query> e=<query> o=<em class="highlight">...</em>
 *
 * The exact field carrying talker/user wxid is still being mapped, so v1 uses
 * a conservative object scan: when hidden mode is active, remove z15.ef6 result
 * items if any shallow/nested field contains a hidden wxid.
 */
public class SearchFilter {

    private static final String TAG = "NCL";
    private static final String FTS_RESULT_ITEM = "z15.ef6";
    private static volatile long sLastUnlockAt = 0L;

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
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
                            if (!FTS_RESULT_ITEM.equals(first.getClass().getName())) return;

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
