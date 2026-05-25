/**
 * probe_select_contact.js v3 — 抓 SelectContactUI onCreate Intent + setResult 返回数据
 * 竞品包名 com.tencent.mn1
 */
'use strict';

Java.perform(function () {
    var TAG = '[SEL]';

    function dumpIntent(intent, prefix) {
        if (!intent) { console.log(TAG + ' ' + prefix + ' intent=null'); return; }
        var b = intent.getExtras();
        if (!b) { console.log(TAG + ' ' + prefix + ' extras=null'); return; }
        var keys = b.keySet();
        var kit = keys.iterator();
        while (kit.hasNext()) {
            var k = kit.next();
            try {
                var v = b.get(k);
                console.log(TAG + ' ' + prefix + ' EXTRA: ' + k + ' = ' + v + ' (' + (v ? v.getClass().getSimpleName() : 'null') + ')');
            } catch (e2) {}
        }
    }

    // ── 1. hook onCreate ──
    try {
        var SelectContactUI = Java.use('com.tencent.mm.ui.contact.SelectContactUI');
        SelectContactUI.onCreate.implementation = function (savedInstanceState) {
            console.log(TAG + ' ====== onCreate ======');
            dumpIntent(this.getIntent(), '[IN]');
            return this.onCreate(savedInstanceState);
        };
        console.log(TAG + ' onCreate hook ok');
    } catch (e) {
        console.log(TAG + ' onCreate fail: ' + e);
    }

    // ── 2. hook setResult ──
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.setResult.overload('int', 'android.content.Intent').implementation = function (resultCode, data) {
            if (this.getClass().getName().indexOf('SelectContactUI') >= 0) {
                console.log(TAG + ' ====== setResult code=' + resultCode + ' ======');
                dumpIntent(data, '[OUT]');
            }
            return this.setResult(resultCode, data);
        };
        console.log(TAG + ' setResult hook ok');
    } catch (e) {
        console.log(TAG + ' setResult fail: ' + e);
    }

    console.log(TAG + ' ready — 请打开"选择朋友"页面');
});
