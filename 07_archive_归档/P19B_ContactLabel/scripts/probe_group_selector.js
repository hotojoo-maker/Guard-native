/**
 * probe_group_selector.js — 抓微信内部启动群多选 Activity 的完整 Intent
 * 操作：你自然导航到群多选页（如转发消息→选群→多选），我们会捕获类名+全部Intent extras
 */
'use strict';

Java.perform(function () {
    var Activity = Java.use('android.app.Activity');

    // Hook startActivity 系列，watch for group-related targets
    var startActivityIntent = Activity.startActivity.overload('android.content.Intent');
    startActivityIntent.implementation = function (intent) {
        try {
            var cmp = intent.getComponent();
            if (cmp) {
                var cls = cmp.getClassName();
                if (/contact|group|chatroom|select/i.test(cls)) {
                    console.log('[SA] → ' + cls);
                    dumpIntent(intent, '[SA:IN]');
                }
            }
        } catch (e) {}
        return this.startActivity(intent);
    };

    // Hook onCreate — print class name + intent extras
    Activity.onCreate.overload('android.os.Bundle').implementation = function (savedInstanceState) {
        var cls = this.getClass().getName();
        if (/contact|group|chatroom|select/i.test(cls)) {
            console.log('[ONCREATE] ★ ' + cls);
            // also check what Intent was received
            try {
                dumpIntent(this.getIntent(), '[ONCREATE:IN]');
            } catch (e) {}
        }
        return this.onCreate(savedInstanceState);
    };

    function dumpIntent(intent, tag) {
        if (!intent) return;
        try {
            var b = intent.getExtras();
            if (!b) { console.log(tag + ' extras=null'); return; }
            var keys = b.keySet();
            var kit = keys.iterator();
            while (kit.hasNext()) {
                var k = kit.next();
                try {
                    var v = b.get(k);
                    console.log(tag + ' ' + k + ' = ' + v + ' (' + (v ? v.getClass().getSimpleName() : 'null') + ')');
                } catch (e2) {}
            }
        } catch (e) {}
    }

    console.log('[SA] ready — 请你在微信里自然操作到群多选页面（如：首页搜索→更多联系人→多选群→勾选）');
    console.log('[SA] 所有含 contact/group/chatroom/select 的类名和Intent extras都会被打印');
});
