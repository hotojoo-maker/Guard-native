/**
 * probe_label_bind.js v6 — 修正 overload 参数类型为 androidx.recyclerview.widget.f2
 * 抓到 adapter 类名后，写 Java hook 在 onBindViewHolder 时读 d4.field_labelID
 */
'use strict';

Java.perform(function () {
    var TAG = '[LBL]';
    var seen = {};

    try {
        var RecyclerView = Java.use('androidx.recyclerview.widget.RecyclerView');
        RecyclerView.setAdapter.overload('androidx.recyclerview.widget.f2').implementation = function (a) {
            if (a) {
                var cn = a.getClass().getName();
                if (!seen[cn]) {
                    seen[cn] = true;

                    // 读 getItemCount
                    var count = -1;
                    try { count = a.getItemCount(); } catch (e) {}

                    console.log(TAG + ' Adapter=' + cn + ' items=' + count);

                    // 父类链
                    var sup = a.getClass().getSuperclass();
                    var chain = cn;
                    while (sup && sup.getName() !== 'java.lang.Object') {
                        chain += ' -> ' + sup.getName();
                        sup = sup.getSuperclass();
                    }
                    console.log(TAG + '  chain: ' + chain);

                    // 如果有 item，读第一个
                    if (count > 0) {
                        try {
                            var item = a.getItem(0);
                            if (item) {
                                var icn = item.getClass().getName();
                                console.log(TAG + '  item[0]=' + icn);

                                // 读 field_labelID / field_labelName
                                var fields = item.getClass().getDeclaredFields();
                                for (var i = 0; i < fields.length; i++) {
                                    var fn = fields[i].getName();
                                    if (fn.indexOf('label') >= 0 || fn.indexOf('ID') >= 0 || fn.indexOf('Name') >= 0) {
                                        try {
                                            fields[i].setAccessible(true);
                                            console.log(TAG + '    .' + fn + ' = ' + fields[i].get(item));
                                        } catch (e2) {}
                                    }
                                }
                            }
                        } catch (e) {}
                    }
                }
            }
            return this.setAdapter(a);
        };
        console.log(TAG + ' ready — 请进通讯录 → 标签页');
    } catch (e) {
        console.log(TAG + ' fail: ' + e);
    }
});
