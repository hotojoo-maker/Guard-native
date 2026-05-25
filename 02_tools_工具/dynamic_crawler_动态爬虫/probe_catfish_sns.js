// probe_catfish_sns.js — Catfish 朋友圈 SNS 探针
// 目标: 1) feed 滚动过滤(hookSns2/hookSns) 2) 分组可见图标(hookSnsGroup)
//       3) H↔V 热切换刷新机制 4) 小红点(另探)

Java.perform(function() {
    var TAG = "[SNS-PROBE]";
    console.log(TAG + " === Catfish SNS/Moments Probe ===");

    // ── §1 MainEntry SNS 静态方法（Pine hook 入口） ──

    // §1.1 hookSns2 — feed 每条帖子滚动时触发（主力）
    try {
        var ME = Java.use("com.catfish.newvip.MainEntry");
        var orig_hs2 = ME.hookSns2;
        ME.hookSns2.implementation = function(name, view) {
            console.log(TAG + " hookSns2 wxid=" + name + " view=" + (view ? view.getClass().getName() : "null"));
            return orig_hs2.call(this, name, view);
        };
        console.log(TAG + " V MainEntry.hookSns2");
    } catch(e) { console.log(TAG + " X MainEntry.hookSns2: " + e); }

    // §1.2 hookSnsObject — 详情页单条
    try {
        var ME2 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hso = ME2.hookSnsObject;
        ME2.hookSnsObject.implementation = function(snsObj) {
            var cls = snsObj ? snsObj.getClass().getName() : "null";
            console.log(TAG + " hookSnsObject class=" + cls);
            return orig_hso.call(this, snsObj);
        };
        console.log(TAG + " V MainEntry.hookSnsObject");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsObject: " + e); }

    // §1.3 hookSnsObject2 — 详情页第二条路径
    try {
        var ME2b = Java.use("com.catfish.newvip.MainEntry");
        var orig_hso2 = ME2b.hookSnsObject2;
        ME2b.hookSnsObject2.implementation = function(snsObj) {
            var cls = snsObj ? snsObj.getClass().getName() : "null";
            console.log(TAG + " hookSnsObject2 class=" + cls);
            return orig_hso2.call(this, snsObj);
        };
        console.log(TAG + " V MainEntry.hookSnsObject2");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsObject2: " + e); }

    // §1.4 hookSnsGroup — 分组可见图标
    try {
        var ME3 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hsg = ME3.hookSnsGroup;
        ME3.hookSnsGroup.implementation = function() {
            var result = orig_hsg.call(this);
            console.log(TAG + " hookSnsGroup() = " + result);
            return result;
        };
        console.log(TAG + " V MainEntry.hookSnsGroup");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsGroup: " + e); }

    // §1.5 hookSnsCommentDetail — 评论详情
    try {
        var ME4 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hscd = ME4.hookSnsCommentDetail;
        ME4.hookSnsCommentDetail.implementation = function(data) {
            console.log(TAG + " hookSnsCommentDetail()");
            return orig_hscd.call(this, data);
        };
        console.log(TAG + " V MainEntry.hookSnsCommentDetail");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsCommentDetail: " + e); }

    // §1.6 hookSns — adapter 级（老路径）
    try {
        var ME5 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hs = ME5.hookSns;
        ME5.hookSns.implementation = function(index, adapter, view) {
            console.log(TAG + " hookSns index=" + index);
            return orig_hs.call(this, index, adapter, view);
        };
        console.log(TAG + " V MainEntry.hookSns");
    } catch(e) { console.log(TAG + " X MainEntry.hookSns: " + e); }

    // §1.7 hookSnsLikes
    try {
        var ME6 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hsl = ME6.hookSnsLikes;
        ME6.hookSnsLikes.implementation = function(l, d) {
            console.log(TAG + " hookSnsLikes()");
            return orig_hsl.call(this, l, d);
        };
        console.log(TAG + " V MainEntry.hookSnsLikes");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsLikes: " + e); }

    // §1.8 hookSnsComments
    try {
        var ME7 = Java.use("com.catfish.newvip.MainEntry");
        var orig_hsc = ME7.hookSnsComments;
        ME7.hookSnsComments.implementation = function(list) {
            console.log(TAG + " hookSnsComments size=" + (list ? list.size() : -1));
            return orig_hsc.call(this, list);
        };
        console.log(TAG + " V MainEntry.hookSnsComments");
    } catch(e) { console.log(TAG + " X MainEntry.hookSnsComments: " + e); }

    // ── §2 UserControll 实例方法 ──

    // §2.1 UserControll.hookSns2 — 实际过滤逻辑
    try {
        var UC = Java.use("com.catfish.newvip.core.UserControll");
        var uc_hs2 = UC.hookSns2;
        UC.hookSns2.implementation = function(name, view) {
            var vips = "";
            try {
                var VP = Java.use("com.catfish.newvip.preference.VipPreference");
                vips = VP.getInstance().getVipSecret();
            } catch(e2) {}
            var isVip = vips.indexOf(name) >= 0;
            if (isVip) {
                console.log(TAG + " !!! UC.hookSns2 BLOCK wxid=" + name + " view=" + view.getClass().getName());
            }
            return uc_hs2.call(this, name, view);
        };
        console.log(TAG + " V UC.hookSns2");
    } catch(e) { console.log(TAG + " X UC.hookSns2: " + e); }

    // §2.2 UserControll.hookSnsObject
    try {
        var UC2 = Java.use("com.catfish.newvip.core.UserControll");
        var uc_hso = UC2.hookSnsObject;
        UC2.hookSnsObject.implementation = function(snsObj) {
            console.log(TAG + " UC.hookSnsObject class=" + (snsObj ? snsObj.getClass().getName() : "null"));
            return uc_hso.call(this, snsObj);
        };
        console.log(TAG + " V UC.hookSnsObject");
    } catch(e) { console.log(TAG + " X UC.hookSnsObject: " + e); }

    // §2.3 UserControll.hookSnsGroup
    try {
        var UC3 = Java.use("com.catfish.newvip.core.UserControll");
        var uc_hsg = UC3.hookSnsGroup;
        UC3.hookSnsGroup.implementation = function() {
            var r = uc_hsg.call(this);
            console.log(TAG + " UC.hookSnsGroup() = " + r);
            return r;
        };
        console.log(TAG + " V UC.hookSnsGroup");
    } catch(e) { console.log(TAG + " X UC.hookSnsGroup: " + e); }

    // §2.4 UserControll.hookSnsCommentOne — 评论/点赞过滤
    try {
        var UC4 = Java.use("com.catfish.newvip.core.UserControll");
        var uc_hsco = UC4.hookSnsCommentOne;
        UC4.hookSnsCommentOne.implementation = function(fieldName, snsObject) {
            var r = uc_hsco.call(this, fieldName, snsObject);
            console.log(TAG + " UC.hookSnsCommentOne field=" + fieldName + " ret=" + r);
            return r;
        };
        console.log(TAG + " V UC.hookSnsCommentOne");
    } catch(e) { console.log(TAG + " X UC.hookSnsCommentOne: " + e); }

    // ── §3 热切换追踪：H↔V 时 Moments 如何刷新 ──

    // §3.1 ActivityControll — SNS Activity 生命周期
    try {
        var AC = Java.use("com.catfish.newvip.core.ActivityControll");
        var ac_nia = AC.needInterruptActivity;
        AC.needInterruptActivity.implementation = function(activity) {
            var name = activity.getClass().getName();
            if (name.indexOf("Sns") >= 0 || name.indexOf("sns") >= 0) {
                console.log(TAG + " >>> needInterruptActivity SNS: " + name);
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            }
            return ac_nia.call(this, activity);
        };
        console.log(TAG + " V AC.needInterruptActivity (SNS filter)");
    } catch(e) { console.log(TAG + " X AC.needInterruptActivity: " + e); }

    // §3.2 restartLauncher — H↔V 时是否触发
    try {
        var AC2 = Java.use("com.catfish.newvip.core.ActivityControll");
        var ac_rl = AC2.restartLauncher;
        AC2.restartLauncher.implementation = function(isBack) {
            console.log(TAG + " >>> restartLauncher(" + isBack + ")");
            var e = Java.use("java.lang.Exception").$new();
            console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            return ac_rl.call(this, isBack);
        };
        console.log(TAG + " V AC.restartLauncher");
    } catch(e) {}

    // §3.3 enterVipMode / exitVipMode — 状态切换
    try {
        var UC5 = Java.use("com.catfish.newvip.core.UserControll");
        var evm = UC5.enterVipMode;
        UC5.enterVipMode.implementation = function() {
            console.log(TAG + " >>> enterVipMode() [H→V]");
            var e = Java.use("java.lang.Exception").$new();
            console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            return evm.call(this);
        };
        var xvm = UC5.exitVipMode;
        UC5.exitVipMode.implementation = function(isBack) {
            console.log(TAG + " >>> exitVipMode(" + isBack + ") [V→H]");
            var e = Java.use("java.lang.Exception").$new();
            console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            return xvm.call(this, isBack);
        };
        console.log(TAG + " V UC.enterVipMode/exitVipMode");
    } catch(e) {}

    // §3.4 notifyStateChanged
    try {
        var AC3 = Java.use("com.catfish.newvip.core.ActivityControll");
        var ac_nsc = AC3.notifyStateChanged;
        AC3.notifyStateChanged.implementation = function() {
            console.log(TAG + " >>> notifyStateChanged()");
            return ac_nsc.call(this);
        };
        console.log(TAG + " V AC.notifyStateChanged");
    } catch(e) {}

    // §3.5 SnsTimeLineUI 关键生命周期 — onCreate/onResume
    try {
        var SnsTL = Java.use("com.tencent.mm.plugin.sns.ui.SnsTimeLineUI");
        if (SnsTL.onResume) {
            var orig_onResume = SnsTL.onResume;
            SnsTL.onResume.implementation = function() {
                console.log(TAG + " >>> SnsTimeLineUI.onResume()");
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
                return orig_onResume.call(this);
            };
            console.log(TAG + " V SnsTimeLineUI.onResume");
        }
    } catch(e) { console.log(TAG + " X SnsTimeLineUI.onResume: " + e); }

    // §3.6 SnsTimeLineUI onCreate
    try {
        var SnsTL2 = Java.use("com.tencent.mm.plugin.sns.ui.SnsTimeLineUI");
        if (SnsTL2.onCreate) {
            var orig_onCreate = SnsTL2.onCreate;
            SnsTL2.onCreate.implementation = function(bundle) {
                console.log(TAG + " >>> SnsTimeLineUI.onCreate()");
                return orig_onCreate.call(this, bundle);
            };
            console.log(TAG + " V SnsTimeLineUI.onCreate");
        }
    } catch(e) { console.log(TAG + " X SnsTimeLineUI.onCreate: " + e); }

    // §3.7 朋友圈 RecyclerView/ListView adapter notifyDataSetChanged
    // 监控 SnsTimeLineUI 内的 adapter 刷新
    try {
        var AbsAdapter = Java.use("android.widget.BaseAdapter");
        var orig_ndc = AbsAdapter.notifyDataSetChanged;
        AbsAdapter.notifyDataSetChanged.implementation = function() {
            var cls = this.getClass().getName();
            if (cls.indexOf("sns") >= 0 || cls.indexOf("Sns") >= 0 || cls.indexOf("SNS") >= 0) {
                console.log(TAG + " >>> Adapter.notifyDataSetChanged class=" + cls);
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack (trimmed): " + Java.use("android.util.Log").getStackTraceString(e).substring(0, 500));
            }
            return orig_ndc.call(this);
        };
        console.log(TAG + " V BaseAdapter.notifyDataSetChanged (SNS filter)");
    } catch(e) { console.log(TAG + " X BaseAdapter.notifyDataSetChanged: " + e); }

    console.log(TAG + " === Ready. Enter Moments feed now. ===");
    console.log(TAG + " Test sequence:");
    console.log(TAG + "   A) Scroll feed — watch for hookSns2 per-post");
    console.log(TAG + "   B) Open a post detail — watch for hookSnsObject");
    console.log(TAG + "   C) Find a post with group visibility icon — watch for hookSnsGroup");
    console.log(TAG + "   D) Back to feed → switch H↔V → re-enter Moments");
    console.log(TAG + "      → watch for restartLauncher / SnsTimeLineUI lifecycle / notifyDataSetChanged");
});
