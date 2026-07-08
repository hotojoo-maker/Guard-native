package com.ghost.assist.moduleE;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.ghost.assist.BuildConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.RiskState;
import com.ghost.assist.core.StateMachine;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * E2 伪装订位 —— 全局伪造定位（设置页改好，发位置/共享/朋友圈/附近的人全局生效）。
 *
 * 全程 L1 动态实证（2026-06-07，探针 tools/probe_loc_*.js），详见
 * docs/HOOK_MAP_8071_AUTHORITATIVE.md §一.1。
 *
 * 两部分：
 *  ① 注入：hook 定位分发总源头 pz0.h.c(pz0.h, boolean ok, double 纬度, double 经度, ...)
 *     beforeHook 把 arg1=true、arg2=伪纬度、arg3=伪经度 → 下游 n83.g.onGetLocation 自动继承 → 全局生效。
 *     （pz0.h 是 tinker 运行时类，必须用 app classloader，同 PushFilter/AntiRecall。）
 *  ② 设置：复用微信原生选点页 RedirectUI。launchPicker() 拉起，用户选点+右上角"发送"后
 *     RedirectUI.setResult(-1, KLocationIntent)；hook Activity.setResult 捕获
 *     LocationIntent.d(纬度)/.e(经度)/.h(POI名) → 存 Bridge。无 caller awaiting → 不真发消息。
 *
 * 【门控】只读/只写 Bridge 伪坐标 + 调微信原生 Activity；不碰状态机/授权写/口令/Filter。
 * 授权门：复用 StateMachine.isVipAuthorized()（已接 EnvelopeStore.isAuthorizedNow 真授权门）。
 */
public final class FakeLocation {

    private static final String TAG = "NCL";

    // 注入点（8.0.71 混淆名，升版经 classmap 重查）
    private static final String DISPATCH_CLASS = "pz0.h";
    private static final String DISPATCH_METHOD = "c";

    // 原生选点页 + 结果载体
    // 启动目标包名 = 宿主包（官替=com.tencent.mm / 共存=com.tencent.mn），随 flavor 自动注入。
    // 写死会让共存版跨包拉官方包选点页被系统拦截 → 伪装定位选点拉不起来。
    private static final String WECHAT_PKG   = BuildConfig.GUARD_WX_PKG;
    private static final String REDIRECT_UI  = "com.tencent.mm.plugin.location.ui.RedirectUI";
    private static final String EX_KLOCATION = "KLocationIntent";
    private static final String LOC_PLUGIN_PREFIX = "com.tencent.mm.plugin.location";

    // 选点页启动 extras（L1 实证 mg.t0 发位置链）
    private static final String EX_VIEW_TYPE = "map_view_type";
    private static final String EX_INDOOR    = "map_indoor_support";
    private static final String EX_SENDER    = "map_sender_name";
    private static final String EX_TALKER    = "map_talker_name";
    private static final String FILEHELPER   = "filehelper"; // 文件传输助手（误发兜底，私密）

    /** 只消费「我们自己发起选点」的那次 setResult，避免误吞微信正常发位置。 */
    private static volatile boolean sExpectPick = false;
    /** 本次选点是否已捕获坐标（链路里 SoSoProxyUI→RedirectUI 两次 setResult，只捕获一次）。 */
    private static volatile boolean sCaptured = false;

    private FakeLocation() {}

    // ------------------------------------------------------------------
    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader cl) {
        installInjector(cl);
        installPickCapture();
        installButtonRename();
    }

    // ③ 选点窗口内把右上角「发送」按钮文字改成「保存」（仅 sExpectPick 期；扫视图树，不管文字怎么设的）
    private static final String TXT_SEND = "\u53d1\u9001"; // 发送
    private static final String TXT_SAVE = "\u4fdd\u5b58"; // 保存

    private static void installButtonRename() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!sExpectPick) return;
                        Activity a = (Activity) param.thisObject;
                        if (a == null || !a.getClass().getName().startsWith(LOC_PLUGIN_PREFIX)) return;
                        scheduleRename(a);
                    } catch (Throwable ignored) {}
                }
            });
            Log.i(TAG, "[FLOC] button rename (onResume scan) installed");
        } catch (Throwable t) {
            Log.w(TAG, "[FLOC] button rename install fail: " + t);
        }
    }

    private static void scheduleRename(Activity a) {
        final View decor;
        try { decor = a.getWindow().getDecorView(); } catch (Throwable t) { return; }
        Handler h = new Handler(Looper.getMainLooper());
        int[] delays = {0, 250, 700};
        for (int d : delays) {
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    try { renameSendButton(decor); } catch (Throwable ignored) {}
                }
            }, d);
        }
    }

    private static boolean renameSendButton(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            CharSequence t = tv.getText();
            if (t != null && TXT_SEND.contentEquals(t)) {
                tv.setText(TXT_SAVE);
                Log.i(TAG, "[FLOC] send button -> 保存");
                return true;
            }
            return false;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (renameSendButton(vg.getChildAt(i))) return true;
            }
        }
        return false;
    }

    // ① 注入 pz0.h.c —— 全局伪造定位
    private static void installInjector(ClassLoader cl) {
        try {
            Class<?> dispatch = XposedHelpers.findClass(DISPATCH_CLASS, cl);
            XposedHelpers.findAndHookMethod(dispatch, DISPATCH_METHOD,
                    dispatch, boolean.class, double.class, double.class, int.class,
                    double.class, double.class, double.class, android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Bridge br = Bridge.getInstance();
                                if (!br.isFakeLocationEnabled() || !br.hasFakeLocation()) return;
                                // 授权门（块B / SPEC §3）：定位是付费杂项功能 → 未授权/到期/封停删卡一律白嫖不了。
                                //   isVipAuthorized() = EnvelopeStore.isAuthorizedNow()，已覆盖三者
                                //   （封停/删卡 isCardRevoked → isAuthorizedNow=false），故折掉单独 isCardRevoked() 判断。
                                if (!StateMachine.getInstance().isVipAuthorized()) return;
                                // 散沙扩面（杂项·块B）：确认篡改超影子期 → 伪装定位失效（盗版功能散沙）。
                                //   对正版未篡改恒为 false → 注入行为不变（不误伤，铁律29）。
                                if (RiskState.isTamperDegraded()) return;
                                param.args[1] = Boolean.TRUE;          // ok=true（即便真 GPS 失败也报有效）
                                param.args[2] = Double.valueOf(br.getFakeLat()); // 纬度
                                param.args[3] = Double.valueOf(br.getFakeLng()); // 经度
                            } catch (Throwable t) {
                                Log.w(TAG, "[FLOC] inject err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[FLOC] injector installed on " + DISPATCH_CLASS + "." + DISPATCH_METHOD);
        } catch (Throwable t) {
            Log.w(TAG, "[FLOC] injector install fail: " + t);
        }
    }

    // ② 捕获原生选点页结果 —— Activity.setResult(KLocationIntent)
    //    链路：SoSoProxyUI.setResult(内层) → RedirectUI.setResult(外层，回传给聊天 caller → 触发发送)。
    //    在 sExpectPick 期内：捕获坐标(一次) + 把外层 RedirectUI 的结果改成 CANCELED + data=null，
    //    这样聊天 caller 的 onActivityResult 收到取消 → 不真发位置消息（这是「保存而非发送」的关键）。
    private static void installPickCapture() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "setResult",
                    int.class, Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!sExpectPick) return;
                                Object self = param.thisObject;
                                if (self == null) return;
                                String cls = self.getClass().getName();
                                if (!cls.startsWith(LOC_PLUGIN_PREFIX)) return;
                                Intent data = (Intent) param.args[1];
                                Object li = data != null ? data.getParcelableExtra(EX_KLOCATION) : null;
                                if (li != null && !sCaptured) {
                                    captureLocationIntent(li);
                                    sCaptured = true;
                                }
                                // 外层 RedirectUI 回传 → 改成取消 + 清 data，阻止 caller 真发送。
                                if (REDIRECT_UI.equals(cls)) {
                                    param.args[0] = Activity.RESULT_CANCELED;
                                    param.args[1] = null;
                                    sExpectPick = false;
                                    sCaptured = false;
                                    Log.i(TAG, "[FLOC] send suppressed (result canceled)");
                                }
                            } catch (Throwable t) {
                                sExpectPick = false;
                                sCaptured = false;
                                Log.w(TAG, "[FLOC] capture err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[FLOC] pick capture installed");
        } catch (Throwable t) {
            Log.w(TAG, "[FLOC] pick capture install fail: " + t);
        }
    }

    private static void captureLocationIntent(Object li) {
        // LocationIntent 字段（L1 实证）：d=纬度(double) e=经度(double) h=POI名(String)
        double lat = readDouble(li, "d");
        double lng = readDouble(li, "e");
        String label = readString(li, "h");
        if (lat == 0d && lng == 0d) {
            Log.w(TAG, "[FLOC] capture skipped: lat/lng both 0");
            return;
        }
        Bridge.getInstance().setFakeLocation(lat, lng, label);
        Log.i(TAG, "[FLOC] captured lat=" + lat + " lng=" + lng + " label=" + label);
        try { com.ghost.assist.moduleB.SettingsEntry.refreshFakeLocation(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // 拉起原生选点页（由 SettingsEntry「选择伪装位置」onClick 调）
    // ------------------------------------------------------------------
    public static void launchPicker(Activity act) {
        if (!StateMachine.getInstance().isVipAuthorized()) {
            Log.i(TAG, "[FLOC] picker blocked: not authorized");
            return;
        }
        if (act == null) {
            Log.w(TAG, "[FLOC] picker abort: no activity");
            return;
        }
        try {
            String me = Bridge.getInstance().getMyWxid();
            // talker 用文件传输助手：RedirectUI 内部直接发送、无视/回退 talker，
            // 设成 filehelper 时即便误发也只进自己的文件传输助手（私密、可删、别人看不到）。
            Intent it = new Intent();
            it.setClassName(WECHAT_PKG, REDIRECT_UI);
            it.putExtra(EX_VIEW_TYPE, 0);        // 0 = 发送位置选点模式（L1 实证 mg.t0）
            it.putExtra(EX_INDOOR, 1);
            it.putExtra(EX_SENDER, me != null ? me : "");
            it.putExtra(EX_TALKER, FILEHELPER);
            sCaptured = false;
            sExpectPick = true;
            act.startActivity(it);
            Log.i(TAG, "[FLOC] launch RedirectUI picker (talker=filehelper)");
        } catch (Throwable t) {
            sExpectPick = false;
            Log.w(TAG, "[FLOC] launchPicker fail: " + t);
            try { Toast.makeText(act, "\u65e0\u6cd5\u6253\u5f00\u9009\u70b9\u9875", Toast.LENGTH_SHORT).show(); }
            catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------
    private static double readDouble(Object obj, String field) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v instanceof Number ? ((Number) v).doubleValue() : 0d;
        } catch (Throwable t) {
            return 0d;
        }
    }

    private static String readString(Object obj, String field) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v != null ? String.valueOf(v) : "";
        } catch (Throwable t) {
            return "";
        }
    }
}
