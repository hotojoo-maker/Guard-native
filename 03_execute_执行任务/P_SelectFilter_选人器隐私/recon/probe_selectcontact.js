'use strict';
// SelectContactUI probe v5 — locate the adapter's backing data List (where item.u live).
// ListView.getAdapter() -> unwrap HeaderViewListAdapter -> real adapter -> scan its
// fields (incl. superclasses) for List types, dump size + elem0 class.
function log(t) { console.log('[SCU] ' + t); }
function cls(o) { try { return o ? o.getClass().getName() : 'null'; } catch (e) { return '?'; } }

Java.perform(function () {
  var CL = 'com.tencent.mm.ui.contact.SelectContactUI', n = 0;
  var JList = Java.use('java.util.List');

  Java.choose(CL, {
    onMatch: function (inst) {
      n++; log('=== SCU #' + n + ' ===');
      var c = inst.getClass();
      while (c && c.getName() !== 'java.lang.Object' && c.getName().indexOf('android.app.') !== 0) {
        var fs = c.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) {
          try {
            fs[i].setAccessible(true);
            var v = fs[i].get(inst); var cn = cls(v);
            if (cn.indexOf('ListView') >= 0) {
              var lv = Java.cast(v, Java.use('android.widget.ListView'));
              var adRaw = lv.getAdapter();
              var ad = Java.cast(adRaw, Java.use('android.widget.Adapter'));
              var rn = ad.getClass().getName();
              log('adapterClass=' + rn);
              if (rn.indexOf('HeaderViewListAdapter') >= 0) {
                adRaw = Java.cast(adRaw, Java.use('android.widget.HeaderViewListAdapter')).getWrappedAdapter();
                ad = Java.cast(adRaw, Java.use('android.widget.Adapter'));
                log('wrappedClass=' + ad.getClass().getName());
              }
              var ac = ad.getClass();
              while (ac && ac.getName() !== 'java.lang.Object') {
                var afs = ac.getDeclaredFields();
                for (var j = 0; j < afs.length; j++) {
                  try {
                    afs[j].setAccessible(true);
                    var av = afs[j].get(ad); var acn = cls(av);
                    if (acn.indexOf('List') >= 0) {
                      var lst = Java.cast(av, JList);
                      var sz = lst.size();
                      log('  [DATA] ' + ac.getName().split('.').pop() + '.' + afs[j].getName() + ' : ' + acn + ' size=' + sz);
                      if (sz > 0) {
                        log('     elem0=' + cls(lst.get(0)));
                        if (sz > 1) log('     elem1=' + cls(lst.get(1)));
                        if (sz > 2) log('     elem2=' + cls(lst.get(2)));
                      }
                    }
                  } catch (e) {}
                }
                ac = ac.getSuperclass();
              }
            }
          } catch (e) {}
        }
        c = c.getSuperclass();
      }
      log('=== end ===');
    },
    onComplete: function () { if (n === 0) log('NO instance -- reopen the friend-select page'); else log('done'); }
  });
});
