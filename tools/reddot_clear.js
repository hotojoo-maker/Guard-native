// reddot_clear.js — 朋友圈发现tab红点清除 (纯Frida, 无需LSPosed)
//
// 用法 (微信主界面有红点时 attach):
//   frida -U -n com.tencent.mm -l tools/reddot_clear.js
//
// 两种模式（二选一）:
//   A) 一次性清除: attach后等3秒, 调g1(false), 红点消失即完成, 可detach
//   B) 持久守护: 传 --no-auto-exit, 持续hook onResume/L1, 每次回主界面自动清除
//
// 原理 (jadx 8.0.71 + Frida 实证):
//   FindMoreFriendsUI.g1("album_dyna_photo_ui_title", false)
//   → 触发 LauncherUI tab bar 视觉刷新 → 朋友圈tab红点消失
//   ns.c.b / ww2.c.b 不控制视觉红点 (find_reddot_field.js 已实证)

var CONFIG = {
    TAG: "[RD]",
    DELAY_MS: 2000,        // attach后等待时间 (等Java VM就绪)
    VERIFY_DELAY_MS: 1500, // 清除后验证等待
    PERSIST: false,        // true=持久守护模式, false=一次性清除
};

// 解析命令行参数
if (typeof args !== 'undefined') {
    args.forEach(function(a) {
        if (a === '--persist' || a === '--no-auto-exit') CONFIG.PERSIST = true;
    });
}

function log(msg) { console.log(CONFIG.TAG + " " + msg); }

Java.perform(function() {
    var FMF_CLASS = "com.tencent.mm.ui.FindMoreFriendsUI";
    var LAUNCHER_CLASS = "com.tencent.mm.ui.LauncherUI";
    var G1_KEY = "album_dyna_photo_ui_title";

    // =====================================================================
    // 核心: 反射查找并调用 g1(String, boolean)
    // 修复 B1: 遍历整个继承链 (getDeclaredMethods → 逐级父类)
    // =====================================================================
    function callG1(instance, show) {
        if (!instance) return false;
        for (var c = instance.getClass();
             c && c.getName() !== "java.lang.Object";
             c = c.getSuperclass()) {
            var methods = c.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                if (m.getName() !== "g1") continue;
                var pt = m.getParameterTypes();
                if (pt.length === 2 &&
                    pt[0].getName() === "java.lang.String" &&
                    pt[1].getName() === "boolean") {
                    try {
                        m.setAccessible(true);
                        m.invoke(instance, G1_KEY, show ? true : false);
                        return true;
                    } catch (e) {
                        log("g1 invoke failed on " + c.getSimpleName() + ": " + e);
                    }
                }
            }
        }
        return false;
    }

    // =====================================================================
    // 读取 FMF 实例上的字段 (继承链遍历)
    // =====================================================================
    function getField(obj, name) {
        for (var c = obj.getClass();
             c && c.getName() !== "java.lang.Object";
             c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (e) {}
        }
        return null;
    }

    function getBooleanField(obj, name) {
        var v = getField(obj, name);
        if (v === null || v === undefined) return false;
        return v.booleanValue ? v.booleanValue() : Boolean(v);
    }

    function getIntField(obj, name) {
        var v = getField(obj, name);
        if (v === null || v === undefined) return 0;
        return v.intValue ? v.intValue() : parseInt(v);
    }

    // =====================================================================
    // 判断是否应该清除红点 (基于 FMF.E / x / y 字段)
    // =====================================================================
    function shouldClear(fmfInst) {
        var e = getBooleanField(fmfInst, "E");
        if (!e) {
            log("FMF.E=false, 红点未亮, 无需清除");
            return { clear: false, reason: "E=false" };
        }
        var y = getIntField(fmfInst, "y");
        if (y > 0) {
            log("FMF.E=true y=" + y + " 互动红点保留");
            return { clear: false, reason: "y>0" };
        }
        var x = getField(fmfInst, "x");
        var xStr = x ? String(x) : "";
        return {
            clear: true,
            reason: "y=0 E=true",
            x: xStr
        };
    }

    // =====================================================================
    // 主清除逻辑
    // =====================================================================
    function clearBadge(fmfInst, source) {
        var result = shouldClear(fmfInst);
        log("[" + source + "] " + result.reason + (result.x ? " x=" + result.x : ""));
        if (!result.clear) return false;

        var ok = callG1(fmfInst, false);
        if (ok) {
            log("[" + source + "] g1(false) 已调用, 查看红点是否消失");
        } else {
            log("[" + source + "] g1 调用失败!");
        }
        return ok;
    }

    // =====================================================================
    // 模式 A: 一次性清除 (默认)
    // =====================================================================
    function oneShot() {
        setTimeout(function() {
            log("扫描 FindMoreFriendsUI 实例...");
            Java.choose(FMF_CLASS, {
                onMatch: function(inst) {
                    log("FMF 实例找到: " + inst.getClass().getName());
                    var ok = clearBadge(inst, "oneshot");

                    // 验证
                    setTimeout(function() {
                        var e = getBooleanField(inst, "E");
                        log("验证: FMF.E=" + e + " (g1调用后)");
                        if (!e || !ok) {
                            log("=== 请查看手机发现tab红点是否消失 ===");
                            if (!CONFIG.PERSIST) {
                                log("一次性清除完成, 可 Ctrl+C detach");
                            }
                        }
                    }, CONFIG.VERIFY_DELAY_MS);
                },
                onComplete: function() {
                    log("FMF 扫描完成");
                }
            });
        }, CONFIG.DELAY_MS);
    }

    // =====================================================================
    // 模式 B: 持久守护 (hook onResume/L1 自动清除)
    // =====================================================================
    function persist() {
        // B1. hook LauncherUI.onResume → 回主界面时自动清除
        try {
            var LauncherUI = Java.use(LAUNCHER_CLASS);
            LauncherUI.onResume.overload().implementation = function() {
                this.onResume();
                // 延迟300ms等FMF tab lazy init
                var self = this;
                Java.scheduleOnMainThread(function() {
                    Java.choose(FMF_CLASS, {
                        onMatch: function(fmf) {
                            clearBadge(fmf, "launcher-onResume");
                        },
                        onComplete: function() {}
                    });
                });
            };
            log("持久守护: LauncherUI.onResume hooked");
        } catch (e) {
            log("LauncherUI hook 失败: " + e);
        }

        // B2. hook FMF.L1 → 计算红点时拦截
        try {
            var FMF = Java.use(FMF_CLASS);
            if (FMF.L1 && FMF.L1.overloads) {
                FMF.L1.overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        var ret = ov.apply(this, arguments);
                        Java.scheduleOnMainThread(function() {
                            clearBadge(this, "fmf-L1");
                        }.bind(this));
                        return ret;
                    };
                });
                log("持久守护: FMF.L1 hooked (" + FMF.L1.overloads.length + " overloads)");
            }
        } catch (e) {
            log("FMF.L1 hook 失败: " + e);
        }

        // B3. hook FMF.onResume → 进发现页时主动清除
        try {
            var FMF2 = Java.use(FMF_CLASS);
            FMF2.onResume.overload().implementation = function() {
                this.onResume();
                Java.scheduleOnMainThread(function() {
                    clearBadge(this, "fmf-onResume");
                }.bind(this));
            };
            log("持久守护: FMF.onResume hooked");
        } catch (e) {
            log("FMF.onResume hook 失败: " + e);
        }

        log("持久守护模式已启动 (Ctrl+C 退出)");
    }

    // =====================================================================
    // 入口
    // =====================================================================
    log("reddot_clear.js v1 加载");
    log("模式: " + (CONFIG.PERSIST ? "持久守护" : "一次性清除"));
    log("请将微信切到前台主界面...");

    oneShot();
    if (CONFIG.PERSIST) {
        persist();
    }
});
