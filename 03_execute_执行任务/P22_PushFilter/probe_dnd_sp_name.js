/**
 * probe_dnd_sp_name.js  v2 (轻量版)
 * 目标:
 *   ① SingleChatInfoUI.z7() 参数签名 + 实际传参
 *   ② room_notify_new_msg 写入时所在的 SP 文件名
 *   ③ 同一次 toggle 写了哪些 key（3次之谜）
 *
 * 修复: 去掉 getSharedPreferences 宽网 hook（热路径，导致卡死）
 *       只在 key === 目标 时才读 SP 文件名 + 打印栈
 *
 * 跑法:
 *   frida -U -n com.tencent.mm -l probe_dnd_sp_name.js --no-pause
 *   手动切换一次「消息免打扰」
 */

"use strict";

Java.perform(function () {

    // ── ① z7() 参数签名 ────────────────────────────────────────────────────
    try {
        var SCInfo  = Java.use("com.tencent.mm.ui.SingleChatInfoUI");
        var methods = SCInfo.class.getDeclaredMethods();
        var found   = false;

        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            if (m.getName() !== "z7") continue;
            found = true;

            var pts = m.getParameterTypes();
            var sig = [];
            for (var p = 0; p < pts.length; p++) sig.push(pts[p].getName());
            console.log("[Z7-SIG] z7(" + sig.join(", ") + ") → " + m.getReturnType().getName());

            // hook 这个重载
            (function (paramTypeNames) {
                var ov = paramTypeNames.length
                    ? SCInfo.z7.overload.apply(SCInfo.z7, paramTypeNames)
                    : SCInfo.z7.overload();

                ov.implementation = function () {
                    var args    = Array.prototype.slice.call(arguments);
                    var argsStr = args.map(function (a) {
                        try { return String(a); } catch(e) { return "?"; }
                    }).join(", ");
                    var ret = ov.apply(this, arguments);
                    console.log("[Z7-CALL] SingleChatInfoUI.z7(" + argsStr + ") → " + ret);
                    return ret;
                };
            })(sig);
        }
        if (!found) console.log("[Z7-SIG] z7 未找到（混淆名可能已变）");
        console.log("[Z7] hook ready");
    } catch (e) {
        console.log("[Z7] ERR: " + e);
    }

    // ── ② EditorImpl.putBoolean — 只在 key 命中时才取 SP 文件名 ──────────
    // 关键: 先判断 key，再做任何反射/栈操作 → 热路径代价 ≈ 一次 String 比较
    try {
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");

        EditorImpl.putBoolean.implementation = function (k, v) {
            var ret = this.putBoolean(k, v);
            // 只处理跟 DND 相关的 key，忽略其他所有写入
            if (k !== null && /notify|room_notify|mute|dnd|disturb/i.test(k)) {
                var spName = getSpName(this);
                console.log("[SP-ALL] file=\"" + spName + "\"  key=\"" + k + "\"  val=" + v);
            }
            return ret;
        };

        EditorImpl.putInt.implementation = function (k, v) {
            var ret = this.putInt(k, v);
            if (k !== null && /notify|room_notify|mute|dnd|disturb/i.test(k)) {
                var spName = getSpName(this);
                console.log("[SP-ALL/int] file=\"" + spName + "\"  key=\"" + k + "\"  val=" + v);
            }
            return ret;
        };

        EditorImpl.putString.overload("java.lang.String", "java.lang.String")
            .implementation = function (k, v) {
                var ret = this.putString(k, v);
                if (k !== null && /notify|room_notify|mute|dnd|disturb/i.test(k)) {
                    var spName = getSpName(this);
                    console.log("[SP-ALL/str] file=\"" + spName + "\"  key=\"" + k + "\"  val=" + v);
                }
                return ret;
            };

        console.log("[SP] EditorImpl hooks ready");
    } catch (e) {
        console.log("[SP] EditorImpl hook ERR: " + e);
    }

    // 只在命中时才调用一次反射，取 SP 文件名
    function getSpName(editor) {
        try {
            var f = editor.getClass().getDeclaredField("this$0");
            f.setAccessible(true);
            var spi   = f.get(editor);
            var mFile = spi.getClass().getDeclaredField("mFile");
            mFile.setAccessible(true);
            return String(mFile.get(spi))
                .replace(/.*\//, "")
                .replace(/\.xml$/, "");
        } catch (e) {
            return "err:" + e;
        }
    }

    console.log("[DND-v2] probe ready — 请手动切换「消息免打扰」开关");
});
