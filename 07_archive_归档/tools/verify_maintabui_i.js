// verify_maintabui_i.js — 验证 MainTabUI.i() 归零能否让底部 tab 数字消失
//
// 参考：Catfish 8.0.70 破解版实证 showUnReadMsgCount(int)→0 = tab badge 归零
// 候选：com.tencent.mm.ui.MainTabUI.i()→int (Frida 实证 out=26，调用者 HomeUI.u())
//
// 操作：attach 微信主进程 → 看底部「微信」tab 数字是否变 0
// 用法：frida -U -p <主进程pid> -l verify_maintabui_i.js

Java.perform(function () {
    var ts = function() {
        var d = new Date(), p = function(n){return n<10?'0'+n:''+n;};
        return p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds());
    };

    var observeCount = 0;
    try {
        var MainTabUI = Java.use('com.tencent.mm.ui.MainTabUI');
        MainTabUI.i.overload().implementation = function () {
            var real = this.i();
            observeCount++;
            if (observeCount <= 5) {
                console.log(ts() + ' [VERIFY] MainTabUI.i() real=' + real +
                    ' (观察模式，第' + observeCount + '次)');
            }
            if (observeCount === 5) {
                console.log(ts() + ' [VERIFY] === 切换到归零模式，观察 tab 数字是否变 0 ===');
            }
            // Phase 2: 第 5 次后开始归零
            if (observeCount > 5) {
                console.log(ts() + ' [VERIFY] MainTabUI.i() real=' + real + ' → return 0');
                return 0;
            }
            return real;
        };

        console.log(ts() + ' [VERIFY] MainTabUI.i() hooked OK');
        console.log(ts() + ' [VERIFY] 请切到微信主界面，等 tab 数字刷新（约 3-5 次触发后归零）');
    } catch (e) {
        console.log('[VERIFY] ERROR: ' + e + ' — 类名可能不对，确认 MainTabUI 全路径');
    }

    // keepalive：每 5s 打一次心跳，防止 Frida 因 stdin EOF 退出
    setInterval(function () {
        console.log(ts() + ' [VERIFY] keepalive observeCount=' + observeCount);
    }, 5000);
});
