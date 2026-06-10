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

    // ── 0. hook startActivity — 拦截 Intent before it reaches SelectContactUI ──
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.startActivity.overload('android.content.Intent').implementation = function (intent) {
            var cmp = intent.getComponent();
            if (cmp && String(cmp.getClassName()).indexOf('SelectContactUI') >= 0) {
                console.log(TAG + ' ====== startActivity -> SelectContactUI ======');
                dumpIntent(intent, '[IN]');
            }
            return this.startActivity(intent);
        };
        console.log(TAG + ' startActivity hook ok');
    } catch (e) {
        console.log(TAG + ' startActivity fail: ' + e);
    }

    // ── 0b. hook startActivityForResult (如果调用者需要返回结果) ──
    try {
        Activity.startActivityForResult.overload('android.content.Intent', 'int').implementation = function (intent, requestCode) {
            var cmp = intent.getComponent();
            if (cmp && String(cmp.getClassName()).indexOf('SelectContactUI') >= 0) {
                console.log(TAG + ' ====== startActivityForResult -> SelectContactUI reqCode=' + requestCode + ' ======');
                dumpIntent(intent, '[IN]');
            }
            return this.startActivityForResult(intent, requestCode);
        };
        console.log(TAG + ' startActivityForResult hook ok');
    } catch (e) {
        console.log(TAG + ' startActivityForResult fail: ' + e);
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

    // ── 1b. hook onResume (onCreate 可能被预创建跳过，onResume 每次进页面必跑) ──
    try {
        var SCPUi_OnResume = Java.use('com.tencent.mm.ui.contact.SelectContactUI');
        var onResumeFirst = true;
        SCPUi_OnResume.onResume.implementation = function () {
            console.log(TAG + ' ====== onResume first=' + onResumeFirst + ' ======');
            dumpIntent(this.getIntent(), '[IN]');
            onResumeFirst = false;
            return this.onResume();
        };
        console.log(TAG + ' onResume hook ok');
    } catch (e) {
        console.log(TAG + ' onResume fail: ' + e);
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
