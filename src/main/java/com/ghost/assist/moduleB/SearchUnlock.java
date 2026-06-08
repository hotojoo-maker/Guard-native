package com.ghost.assist.moduleB;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import com.ghost.assist.core.StateMachine;
import com.ghost.assist.debug.UiContextTracker;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * A4 / B6 搜索框密码解锁
 *
 * 方案：hook android.view.View.onAttachedToWindow，过滤 EditText 子类后注入 TextWatcher。
 *
 * 为什么不 hook EditText.onAttachedToWindow：
 *   PasterEditText 不声明 onAttachedToWindow，继承自 View，
 *   XposedHelpers.findAndHookMethod(EditText.class, "onAttachedToWindow") 只挂
 *   EditText 自身声明的版本，PasterEditText 实际走的是 View 的实现，不会命中。
 *   必须 hook android.view.View.onAttachedToWindow 才能覆盖所有子类。
 *
 * 为什么不 hook 构造器：
 *   PasterEditText.addTextChangedListener 覆写了父类实现，内部 LinkedList
 *   在构造器阶段还是 null，onAttachedToWindow 之后才初始化，构造器时调会 NPE。
 *
 * 过滤策略：class name 包含 "EditText" 或等于已知候选（PasterEditText）。
 * 去重：per-instance setTag(WATCHER_TAG) 防止 onAttachedToWindow 多次触发时重复注册。
 *
 * 触发条件：
 *  1. 当前状态 == HIDDEN
 *  2. EditText 文本精确等于密码（默认 "111111"）
 *
 * 触发后：
 *  - 清空 EditText
 *  - 关闭当前 Activity（回主界面）
 *  - 状态机切换 HIDDEN → UNLOCKING → VISIBLE
 */
public class SearchUnlock {

    private static final String TAG = "NCL";
    // Stable tag key for per-instance watcher dedup
    private static final int WATCHER_TAG = 0x67757264; // "gurd"

    // Known WeChat EditText class names that host search input
    private static final String PASTER_EDIT_TEXT =
            "com.tencent.mm.ui.widget.edittext.PasterEditText";

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        StateMachine sm = StateMachine.getInstance();
        String currentPwd = sm.getPassword();
        if (currentPwd == null || currentPwd.isEmpty() || "1111".equals(currentPwd)) {
            sm.setPassword(StateMachine.getDefaultPassword());
        }

        // Hook android.view.View.onAttachedToWindow — covers all subclasses including
        // PasterEditText which does NOT declare its own onAttachedToWindow.
        try {
            XposedHelpers.findAndHookMethod(
                    android.view.View.class, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object obj = param.thisObject;
                            // Fast reject: must be an EditText subclass
                            if (!(obj instanceof EditText)) return;
                            EditText et = (EditText) obj;
                            String cls = et.getClass().getName();
                            // Only known EditText candidates — avoids attaching to every
                            // EditText in the app (message box, comment box, etc.)
                            if (!cls.contains("EditText") && !cls.equals(PASTER_EDIT_TEXT)) return;
                            // Per-instance dedup
                            if (et.getTag(WATCHER_TAG) != null) return;
                            et.setTag(WATCHER_TAG, Boolean.TRUE);
                            Log.i(TAG, "[SU] view attached class=" + cls
                                    + " id=0x" + Integer.toHexString(et.getId()));
                            try {
                                et.addTextChangedListener(new UnlockWatcher(et));
                                Log.i(TAG, "[SU] watcher installed id=0x"
                                        + Integer.toHexString(et.getId()));
                            } catch (Throwable e) {
                                Log.w(TAG, "[SU] addTextChangedListener fail cls=" + cls + " " + e);
                            }
                        }
                    });
            Log.i(TAG, "[SU] View.onAttachedToWindow hook ok pwdLen="
                    + sm.getPassword().length());
        } catch (Throwable e) {
            Log.w(TAG, "[SU] View.onAttachedToWindow hook fail: " + e);
        }
    }

    // -------------------------------------------------------------------------

    private static class UnlockWatcher implements TextWatcher {
        private final EditText mEt;
        private boolean mClearing = false;

        UnlockWatcher(EditText et) { mEt = et; }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (mClearing) return;

            String text = s.toString();
            if (text.length() >= 4) {
                Log.i(TAG, "[SU] afterTextChanged text=" + text + " len=" + text.length()
                        + " cls=" + mEt.getClass().getSimpleName());
            }
            String pwd = StateMachine.getInstance().getPassword();
            if (pwd == null || !pwd.equals(text)) return;

            // 只在 HIDDEN 状态响应；显形态/乱输均不改变状态。
            if (StateMachine.getInstance().getState() != StateMachine.State.HIDDEN) {
                Log.i(TAG, "[SU] unlock matched but state="
                        + StateMachine.getInstance().getStateName() + ", ignored");
                return;
            }

            Log.i(TAG, "[SU] unlock matched text=" + text + " len=" + text.length());

            // 1. 清空输入框（主线程，设防递归）
            mClearing = true;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    mEt.setText("");
                } finally {
                    mClearing = false;
                }
            });

            // 2. 状态机 HIDDEN → UNLOCKING → VISIBLE
            StateMachine.getInstance().beginUnlock();
            boolean ok = StateMachine.getInstance().attemptUnlock(text);
            Log.i(TAG, "[SU] unlock=" + ok
                    + " state=" + StateMachine.getInstance().getStateName());
            if (ok) Log.i(TAG, "[UNLOCK:B6] success");

            // 3. 关闭当前 Activity → 回主界面
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Activity act = UiContextTracker.getCurrentActivity();
                if (act != null && !act.isFinishing()) {
                    act.finish();
                    Log.i(TAG, "[SU] activity finished=" + act.getClass().getSimpleName());
                }
            }, 120); // 稍等让清空动画先走
        }
    }
}
