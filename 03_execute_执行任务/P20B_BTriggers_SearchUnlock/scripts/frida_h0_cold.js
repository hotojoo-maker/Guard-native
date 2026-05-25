/**
 * Route A 冷启动 — h0 全部 27 方法 call trace
 * 目的：看冷启动 0~5s 内哪些方法被调用，找出数据注入入口
 */

var H0 = "com.tencent.mm.ui.base.preference.h0";
var startMs = 0;
var traceCount = 0;

Java.perform(function () {
    console.log("[h0-cold] enter");
    var h0 = Java.use(H0);
    startMs = Date.now();

    var methodDefs = [
        {n:"k", p:["android.database.DataSetObserver"]},
        {n:"l", p:["android.database.DataSetObserver"]},
        {n:"q", p:["android.database.DataSetObserver"]},
        {n:"t", p:["android.database.DataSetObserver","int"]},
        {n:"a", p:["java.util.ArrayList"]},
        {n:"c", p:["java.util.ArrayList"]},
        {n:"d", p:["int","java.util.ArrayList"]},
        {n:"f", p:["int","java.util.ArrayList"]},
        {n:"g", p:["boolean"]},
        {n:"getCount", p:[]},
        {n:"getItem", p:["int"]},
        {n:"getItemId", p:["int"]},
        {n:"getItemViewType", p:["int"]},
        {n:"getView", p:["int","android.view.View","android.view.ViewGroup"]},
        {n:"getViewTypeCount", p:[]},
        {n:"i", p:["int"]},
        {n:"j", p:[]},
        {n:"m", p:["boolean","boolean"]},
        {n:"n", p:["int"]},
        {n:"notifyDataSetChanged", p:[]},
        {n:"p", p:["int"]},
        {n:"r", p:["boolean"]},
        {n:"s", p:[]},
        {n:"u", p:[]},
        {n:"v", p:["int"]},
        {n:"w", p:["int"]},
        {n:"x", p:["java.util.ArrayList"]},
    ];

    methodDefs.forEach(function (def) {
        try {
            var target;
            if (def.p.length === 0) {
                target = h0[def.n];
            } else {
                target = h0[def.n].overload.apply(h0[def.n], def.p);
            }

            target.implementation = function () {
                var ms = Date.now() - startMs;
                traceCount++;

                // 参数摘要
                var summary = "";
                for (var i = 0; i < arguments.length; i++) {
                    var a = arguments[i];
                    if (a === null || a === undefined) {
                        summary += "null,";
                    } else if (a instanceof Java.use("java.util.ArrayList")) {
                        var sz = a.size();
                        summary += "AL[size=" + sz + "]";
                        if (sz > 0 && sz <= 3) {
                            for (var j = 0; j < sz; j++) {
                                var e = a.get(j);
                                summary += "[" + j + "]=" + (e != null ? String(e.getClass().getName()).split('.').pop() : "null") + ",";
                            }
                        }
                        summary += ",";
                    } else if (typeof a === "object" && a.getClass) {
                        summary += String(a.getClass().getName()).split('.').pop() + ",";
                    } else {
                        summary += String(a).substring(0, 30) + ",";
                    }
                }

                var stars = ms < 3000 ? " ★" : "";
                var line = "[h0:" + ms + "ms]" + stars + " #" + traceCount + " " + this.name + "(" + summary + ")";

                // 只看前 8000ms，但高价值调用始终打印
                if (ms < 8000 || this.name === "notifyDataSetChanged"
                    || this.name === "getCount" || this.name === "getView"
                    || summary.indexOf("AL[size=") >= 0) {
                    console.log(line);
                }
                return this._orig.apply(this, arguments);
            }.bind({_orig: target, name: def.n});
        } catch (e) {
            // overload mismatch — not all methods may have the exact parameter types listed
        }
    });

    console.log("[h0-cold] all hooks installed. traceCount=" + traceCount);
});
