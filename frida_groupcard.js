Java.perform(function(){
    var A = Java.use('android.app.Activity');
    A.onCreate.overload('android.os.Bundle').implementation = function(b) {
        this.onCreate(b);
        try {
            var n = this.getClass().getName();
            if (n.indexOf('GroupCard') < 0) return;
            var intent = this.getIntent();
            if (!intent) return;
            var ex = intent.getExtras();
            console.log('[GC] launched extras=' + (ex ? 'yes' : 'null'));
            if (!ex) return;
            var ks = ex.keySet();
            var it = ks.iterator();
            while (it.hasNext()) {
                var k = it.next();
                try {
                    var v = ex.get(k);
                    console.log('[GC] ' + k + ' (type=' + typeof v + ') = ' + v);
                } catch(e) {
                    console.log('[GC] ' + k + ' ERR=' + e);
                }
            }
        } catch(e) {
            console.log('[GC] err=' + e);
        }
    };
    console.log('[HOOK] Activity.onCreate ready for GroupCardSelectUI');
});
