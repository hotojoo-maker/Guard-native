/**
 * T09 — Catfish mn1 互动红点 hook 点探针
 * 目标: com.tencent.mn1  PID 5113
 * 抓: hookNewCon / hookNotification / kc / NmsHook / hookFriendStatusList
 */

'use strict';

var TAG = '[T09]';

function log(msg) { console.log(TAG + ' ' + msg); }

Java.perform(function () {
    log('Java.perform ready, pkg=' + Java.androidVersion);

    // ── 1. MainEntry.hookNewCon ──────────────────────────────────────
    try {
        var MainEntry = Java.use('com.catfish.newvip.MainEntry');
        MainEntry.hookNewCon.overload('java.util.List').implementation = function (list) {
            var before = list.size();
            var ret = this.hookNewCon(list);
            var after = list.size();
            log('[hookNewCon] before=' + before + ' after=' + after + ' removed=' + (before - after));
            if (before > after) {
                // 打印被移除的方向
                log('[hookNewCon] ★ 过滤生效，差值=' + (before - after));
            }
            return ret;
        };
        log('hookNewCon hooked ✅');
    } catch (e) { log('hookNewCon FAIL: ' + e); }

    // ── 2. MainEntry.kc (角标未读数修正) ────────────────────────────
    try {
        var MainEntry2 = Java.use('com.catfish.newvip.MainEntry');
        MainEntry2.kc.overload('int').implementation = function (count) {
            var ret = this.kc(count);
            log('[kc] input=' + count + ' output=' + ret + ' hidden=' + (count - ret));
            return ret;
        };
        log('kc hooked ✅');
    } catch (e) { log('kc FAIL: ' + e); }

    // ── 3. MainEntry.hookNotification ───────────────────────────────
    try {
        var MainEntry3 = Java.use('com.catfish.newvip.MainEntry');
        MainEntry3.hookNotification.overload('android.os.Message').implementation = function (msg) {
            log('[hookNotification] msg=' + msg);
            return this.hookNotification(msg);
        };
        log('hookNotification hooked ✅');
    } catch (e) { log('hookNotification FAIL: ' + e); }

    // ── 4. UserControll.hookFriendStatusList ─────────────────────────
    try {
        var UC = Java.use('com.catfish.newvip.core.UserControll');
        UC.hookFriendStatusList.overload('java.util.List').implementation = function (list) {
            var before = list.size();
            var ret = this.hookFriendStatusList(list);
            log('[hookFriendStatusList] before=' + before + ' after=' + list.size());
            return ret;
        };
        log('hookFriendStatusList hooked ✅');
    } catch (e) { log('hookFriendStatusList FAIL: ' + e); }

    // ── 5. UserControll.hookStatusTopics (朋友圈状态) ───────────────
    try {
        var UC2 = Java.use('com.catfish.newvip.core.UserControll');
        UC2.hookStatusTopics.overload('java.util.List').implementation = function (list) {
            var before = list.size();
            var ret = this.hookStatusTopics(list);
            log('[hookStatusTopics] before=' + before + ' after=' + list.size());
            return ret;
        };
        log('hookStatusTopics hooked ✅');
    } catch (e) { log('hookStatusTopics FAIL: ' + e); }

    // ── 6. NmsHookInvocationHandler (Binder 层通知压制) ──────────────
    try {
        var NMS = Java.use('com.catfish.newvip.core.NmsHookInvocationHandler');
        NMS.invoke.implementation = function (proxy, method, args) {
            var mname = method ? method.getName() : 'null';
            if (mname.indexOf('enqueue') >= 0 || mname.indexOf('Notification') >= 0) {
                log('[NMS.invoke] method=' + mname);
                // 打印 talker 字段（密友 wxid）
                if (args && args.length > 0) {
                    try {
                        var bundle = args[args.length - 1];
                        if (bundle && bundle.getString) {
                            var talker = bundle.getString('notification.show.talker');
                            if (talker) log('[NMS.invoke] talker=' + talker);
                        }
                    } catch (e2) {}
                }
            }
            return this.invoke(proxy, method, args);
        };
        log('NmsHookInvocationHandler hooked ✅');
    } catch (e) { log('NmsHookInvocationHandler FAIL: ' + e); }

    // ── 7. VipPreference.isVipEnable 确认补丁生效 ───────────────────
    try {
        var VP = Java.use('com.catfish.newvip.preference.VipPreference');
        VP.isVipEnable.implementation = function () {
            var ret = this.isVipEnable();
            log('[isVipEnable] = ' + ret);
            return ret;
        };
        log('isVipEnable hooked ✅');
    } catch (e) { log('isVipEnable FAIL: ' + e); }

    log('=== 所有探针就位，请在手机上操作微信 ===');
    log('>>> 刷朋友圈 / 进会话列表 / 触发通知 <<<');
});
