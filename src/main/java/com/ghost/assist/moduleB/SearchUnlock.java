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
 * 方案：hook EditText 构造函数，向每个 EditText 注入 TextWatcher。
 * 不依赖 onTextChanged（微信自定义子类可能不调 super）。
 *
 * 触发条件：
 *  1. 当前状态 == HIDDEN（隐藏态下输入密码才解锁显形）
 *  2. EditText 文本精确等于密码（默认 "111111"）
 *
 * 触发后：
 *  - 清空 EditText（密码不留屏幕上）
 *  - 关闭当前 Activity（回主界面）
 *  - 状态机切换 HIDDEN → UNLOCKING → VISIBLE
 */
public class SearchUnlock {

    private static final String TAG = "NCL";
    private static final String DEFAULT_PWD = "111111";

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        // 初始化默认密码（只在首次启动且 MMKV 里没有值时写入）
        StateMachine sm = StateMachine.getInstance();
        String currentPwd = sm.getPassword();
        if (currentPwd == null || currentPwd.isEmpty() || "1111".equals(currentPwd)) {
            sm.setPassword(DEFAULT_PWD);
        }

        // 三个构造器全盖（Context / Context+Attr / Context+Attr+Style）
        XC_MethodHook injector = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final EditText et = (EditText) param.thisObject;
                et.addTextChangedListener(new UnlockWatcher(et));
            }
        };

        int hooked = 0;
        try {
            XposedHelpers.findAndHookConstructor(
                    EditText.class,
                    android.content.Context.class,
                    injector);
            hooked++;
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookConstructor(
                    EditText.class,
                    android.content.Context.class,
                    android.util.AttributeSet.class,
                    injector);
            hooked++;
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookConstructor(
                    EditText.class,
                    android.content.Context.class,
                    android.util.AttributeSet.class,
                    int.class,
                    injector);
            hooked++;
        } catch (Throwable ignored) {}

        Log.i(TAG, "[SU] SearchUnlock installed, ctors hooked=" + hooked
                + " pwdLen=" + StateMachine.getInstance().getPassword().length());
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
            String pwd = StateMachine.getInstance().getPassword();
            if (pwd == null || !pwd.equals(text)) return;

            // 只在 HIDDEN 状态响应；显形态/乱输均不改变状态。
            if (StateMachine.getInstance().getState() != StateMachine.State.HIDDEN) {
                Log.i(TAG, "[SU] password matched but state="
                        + StateMachine.getInstance().getStateName() + ", ignored");
                return;
            }

            Log.i(TAG, "[SU] password matched len=" + text.length() + " -> unlocking");

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
