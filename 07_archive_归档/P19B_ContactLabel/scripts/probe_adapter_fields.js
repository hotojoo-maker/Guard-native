/**
 * probe_adapter_fields.js — dump y63.j adapter 的字段 + onBindViewHolder 时 ViewHolder 的字段
 * 目的：找到 adapter 存数据的地方，替代不存在的 getItem()
 */
'use strict';

Java.perform(function () {
    var TAG = '[LBL]';
    var hooked = false;

    try {
        var RecyclerView = Java.use('androidx.recyclerview.widget.RecyclerView');
        RecyclerView.setAdapter.overload('androidx.recyclerview.widget.f2').implementation = function (a) {
            if (!hooked && a && a.getClass().getName() === 'y63.j') {
                hooked = true;
                console.log(TAG + ' y63.j setAdapter!');

                // dump adapter fields
                var cls = a.getClass();
                while (cls && cls.getName() !== 'java.lang.Object') {
                    var fields = cls.getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        try {
                            fields[i].setAccessible(true);
                            var v = fields[i].get(a);
                            var type = fields[i].getType().getName();
                            var desc = '';
                            if (v === null) desc = 'null';
                            else if (type.indexOf('List') >= 0 || type.indexOf('ArrayList') >= 0) {
                                desc = 'List(' + Java.cast(v, Java.use('java.util.List')).size() + ')';
                            } else {
                                desc = String(v).substring(0, Math.min(String(v).length, 60));
                            }
                            console.log(TAG + ' adapter.' + cls.getName() + '.' + fields[i].getName() + ' [' + type + '] = ' + desc);
                        } catch (e2) {}
                    }
                    cls = cls.getSuperclass();
                }

                // hook onBindViewHolder
                try {
                    var onBind = null;
                    var c = a.getClass();
                    while (c && c.getName() !== 'java.lang.Object') {
                        var methods = c.getDeclaredMethods();
                        for (var j = 0; j < methods.length; j++) {
                            if (methods[j].getName() === 'onBindViewHolder' && methods[j].getParameterTypes().length >= 2) {
                                onBind = methods[j];
                                break;
                            }
                        }
                        if (onBind) break;
                        c = c.getSuperclass();
                    }

                    if (onBind) {
                        onBind.setAccessible(true);
                        var orig = onBind;
                        // We need to hook it differently - use the adapter instance directly
                        console.log(TAG + ' onBind found: ' + onBind);
                    }
                } catch (e2) {
                    console.log(TAG + ' onBind err: ' + e2);
                }
            }
            return this.setAdapter(a);
        };
        console.log(TAG + ' ready — 请进标签页');
    } catch (e) {
        console.log(TAG + ' fail: ' + e);
    }
});
