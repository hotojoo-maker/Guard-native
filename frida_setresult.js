Java.perform(function(){
    var A = Java.use('android.app.Activity');
    A.setResult.overload('int', 'android.content.Intent').implementation = function(rc, data) {
        console.log('[RESULT] rc=' + rc + ' act=' + this.getClass().getName());
        if (data) {
            var b = data.getExtras();
            if (b) {
                var ks = b.keySet();
                var it = ks.iterator();
                while (it.hasNext()) {
                    var k = it.next();
                    var v = b.get(k);
                    console.log('[RESULT_EXT] ' + k + ' = ' + v + ' (type=' + v.getClass().getName() + ')');
                }
            }
        }
        return this.setResult(rc, data);
    };
    console.log('[HOOK] setResult hooked');
});
