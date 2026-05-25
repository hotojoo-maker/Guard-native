'use strict';
/**
 * v4 安全版 — 只 log 类名，不反射扫描
 * 基于 crawler_v2 (已验证存活30s) + RecyclerView setAdapter 探针
 */
Java.perform(function () {
    var TAG = '[VIS]';
    var HIDDEN_SET = { 'wxid_lzd2va16jd1622': true };
    var adaptersSeen = {};
    var addAllSeen = {};

    function isWxidLike(s) {
        return typeof s === 'string' && /^wxid_[A-Za-z0-9_]+$/.test(s);
    }

    // ==== 1. RecyclerView.setAdapter (只记类名，安全) ====
    try {
        Java.use('androidx.recyclerview.widget.RecyclerView')
            .setAdapter.implementation = function(a) {
                if (a) {
                    var cls = a.getClass().getName();
                    if (!adaptersSeen[cls]) {
                        adaptersSeen[cls] = true;
                        try {
                            var cnt = a.getItemCount();
                            console.log(TAG + ' RV adapter=' + cls + ' items=' + cnt);
                        } catch(e) {
                            console.log(TAG + ' RV adapter=' + cls);
                        }
                    }
                }
                return this.setAdapter(a);
            };
        console.log(TAG + ' RV setAdapter ok');
    } catch(e) { console.log(TAG + ' RV fail: ' + e); }

    // ==== 2. SelectContactUI (确认界面) ====
    try {
        var SC = Java.use('com.tencent.mm.ui.contact.SelectContactUI');
        SC.onCreate.implementation = function(b) {
            console.log(TAG + ' SelectContactUI onCreate');
            return this.onCreate(b);
        };
        SC.onResume.implementation = function() {
            console.log(TAG + ' SelectContactUI onResume (现在应该在"选择可见好友")');
            return this.onResume();
        };
        console.log(TAG + ' SelectContactUI ok');
    } catch(e) { console.log(TAG + ' SC fail: ' + e); }

    // ==== 3. addAll 只记类名 (安全，已验证) ====
    Java.use('java.util.ArrayList').addAll.overload('java.util.Collection')
        .implementation = function(c) {
            var ret = this.addAll(c);
            try {
                if (!c || c.size() === 0) return ret;
                var it = c.iterator();
                if (!it.hasNext()) return ret;
                var first = it.next();
                if (!first) return ret;
                var cls = first.getClass().getName();
                if (!addAllSeen[cls]) {
                    addAllSeen[cls] = true;
                    console.log(TAG + ' addAll ' + cls + ' sz=' + c.size());
                }
            } catch(e) {}
            return ret;
        };
    console.log(TAG + ' addAll ok');

    // ==== 4. 30s 汇总报告 ====
    setTimeout(function() {
        console.log('\n' + TAG + ' === 30s 汇总 ===');
        console.log('RV adapters: ' + JSON.stringify(Object.keys(adaptersSeen)));
        console.log('addAll classes: ' + JSON.stringify(Object.keys(addAllSeen)));
        console.log(TAG + ' 完成');
    }, 30000);

    console.log(TAG + ' === 请退出重进"谁可以看" ===');
});
