'use strict';
function log(t) { console.log('[SCU] ' + t); }
Java.perform(function () {
  var CL = 'com.tencent.mm.ui.contact.SelectContactUI', n = 0;
  var JObject = Java.use('java.lang.Object');
  var JList = Java.use('java.util.List');
  var Adapter = Java.use('android.widget.Adapter');
  var ListView = Java.use('android.widget.ListView');
  var HVLA = Java.use('android.widget.HeaderViewListAdapter');

  function rClass(o) { try { return Java.cast(o, JObject).getClass(); } catch (e) { return null; } }
  function rName(o) { if (o == null) return 'null'; var c = rClass(o); return c ? c.getName() : '?'; }

  function dumpFields(o) {
    var ac = rClass(o);
    while (ac && ac.getName() !== 'java.lang.Object') {
      var afs = ac.getDeclaredFields();
      for (var j = 0; j < afs.length; j++) {
        try {
          afs[j].setAccessible(true);
          var av = afs[j].get(o); var acn = rName(av);
          if (acn.indexOf('List') >= 0 || acn.indexOf('Cursor') >= 0) {
            var extra = '';
            try { var lst = Java.cast(av, JList); extra = ' size=' + lst.size(); } catch (e2) {}
            log('    [F] ' + ac.getName().split('.').pop() + '.' + afs[j].getName() + ' : ' + acn + extra);
          }
        } catch (e) {}
      }
      ac = ac.getSuperclass();
    }
  }

  function dumpMethods(o) {
    var ac = rClass(o);
    while (ac && ac.getName() !== 'java.lang.Object' && ac.getName().indexOf('android.') !== 0) {
      var ms = ac.getDeclaredMethods();
      for (var i = 0; i < ms.length; i++) {
        try {
          var m = ms[i];
          var ps = m.getParameterTypes(); var psn = [];
          for (var k = 0; k < ps.length; k++) psn.push(ps[k].getName().split('.').pop());
          var rt = m.getReturnType().getName();
          var mark = (rt.indexOf('Cursor') >= 0) ? ' <== CURSOR' : '';
          if (rt.indexOf('Cursor') >= 0 || ps.length === 0 || psn.join(',').indexOf('List') >= 0 || psn.join(',').indexOf('String') >= 0) {
            log('    M ' + ac.getName().split('.').pop() + '.' + m.getName() + '(' + psn.join(',') + '):' + rt.split('.').pop() + mark);
          }
        } catch (e) {}
      }
      ac = ac.getSuperclass();
    }
  }

  Java.choose(CL, {
    onMatch: function (inst) {
      n++; log('=== SCU #' + n + ' ===');
      var c = rClass(inst);
      while (c && c.getName() !== 'java.lang.Object') {
        var fs = c.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) {
          try {
            fs[i].setAccessible(true);
            var v = fs[i].get(inst); var cn = rName(v);
            if (cn === 'android.widget.ListView') {
              var lv = Java.cast(v, ListView);
              var adRaw = lv.getAdapter();
              if (adRaw == null) { log('adapter=null'); continue; }
              var real = adRaw;
              if (rName(adRaw).indexOf('HeaderViewListAdapter') >= 0) real = Java.cast(adRaw, HVLA).getWrappedAdapter();
              log('realAdapter=' + rName(real));
              log('  --- fields (List/Cursor) ---'); dumpFields(real);
              log('  --- methods (cursor/0-arg/List/String) ---'); dumpMethods(real);
            }
          } catch (e) { log('ERR ' + e); }
        }
        c = c.getSuperclass();
      }
      log('=== end ===');
    },
    onComplete: function () { if (n === 0) log('NO instance'); else log('done'); }
  });
});
