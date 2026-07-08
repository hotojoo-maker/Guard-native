// 验大血管: 微信启动期"读自身签名"到底走哪个 API / 哪个重载, 拿到的字节 MD5 是官方(18c867f0) 还是非官方?
//   官方 DER md5 = 18c867f0...   我方 release cert 的 DER 会是别的值
// Frida 17: Java bridge 已从核心移除, 需 ESM default 引入(frida-java-bridge 是 type:module 包), 经 frida-compile 打包
import Java from 'frida-java-bridge';
function log(t) { console.log('[SIGMD5] ' + t); }
var Exc, LogC, MD;
function stack(){ try { return LogC.getStackTraceString(Exc.$new()); } catch(e){ return '<stk '+e+'>'; } }
function md5(bytes){ try{ var d=MD.getInstance('MD5').digest(bytes); var s=''; for(var i=0;i<d.length;i++){var v=(d[i]&0xff).toString(16); if(v.length<2)v='0'+v; s+=v;} return s; }catch(e){return '<md5 '+e+'>';} }
function isSelf(p){ return p==='com.tencent.mm' || p==='com.tencent.mn'; }
function dumpPI(where, p, flags, r){
  try{
    var arr = r && r.signatures ? r.signatures.value : null;
    if (arr && arr.length){
      for (var i=0;i<arr.length;i++){ var m=md5(arr[i].toByteArray());
        log(where+' pkg='+p+' flags=0x'+(flags>>>0).toString(16)+' sig['+i+'] md5='+m+(m.indexOf('18c867f0')===0?'  <== 官方(喂进了!)':'  <== 非官方')); }
    } else {
      var si = r && r.signingInfo ? r.signingInfo.value : null;
      if (si){
        try{ var sc = si.getApkContentsSigners(); if(sc&&sc.length){ for(var j=0;j<sc.length;j++){ var mm=md5(sc[j].toByteArray());
          log(where+' pkg='+p+' flags=0x'+(flags>>>0).toString(16)+' signingInfo['+j+'] md5='+mm+(mm.indexOf('18c867f0')===0?'  <== 官方(喂进了!)':'  <== 非官方')); } } }catch(e){ log(where+' signingInfo read err '+e); }
      } else {
        log(where+' pkg='+p+' flags=0x'+(flags>>>0).toString(16)+' (no signatures / no signingInfo)');
      }
    }
  }catch(e){ log(where+' dump err '+e); }
}
Java.perform(function(){
  Exc = Java.use('java.lang.Exception'); LogC = Java.use('android.util.Log'); MD = Java.use('java.security.MessageDigest');
  var APM = Java.use('android.app.ApplicationPackageManager');
  var SIGF = 0x40, CERTF = 0x8000000;
  var seenSig = {};
  var n=0;
  APM.getPackageInfo.overloads.forEach(function(ov){
    try{
      var argt = ov.argumentTypes.map(function(t){return t.className;}).join(',');
      ov.implementation = function(){
        var r = ov.apply(this, arguments);
        try{
          var p = arguments[0]; var pkg = (p && p.toString) ? p.toString() : String(p);
          var flags = 0; for (var k=1;k<arguments.length;k++){ if(typeof arguments[k]==='number'){flags=arguments[k];break;} }
          var wantSig = ((flags & SIGF)!==0) || ((flags & CERTF)!==0);
          if (wantSig || isSelf(pkg)){
            if (wantSig && isSelf(pkg)) dumpPI('getPackageInfo['+argt+']', pkg, flags, r);
            else if (wantSig) log('getPackageInfo['+argt+'] pkg='+pkg+' flags=0x'+(flags>>>0).toString(16)+' (他包, 只记不dump)');
          }
        }catch(e){ log('gpi cb err '+e); }
        return r;
      };
      n++;
    }catch(e){ log('hook overload fail '+e); }
  });
  log('hooked getPackageInfo overloads = '+n);
  // getPackageInfoAsUser (若存在)
  try{ APM.getPackageInfoAsUser.overloads.forEach(function(ov){ var argt=ov.argumentTypes.map(function(t){return t.className;}).join(','); ov.implementation=function(){ var r=ov.apply(this,arguments); try{ var pkg=String(arguments[0]); var flags=0; for(var k=1;k<arguments.length;k++){if(typeof arguments[k]==='number'){flags=arguments[k];break;}} if((flags&SIGF||flags&CERTF)&&isSelf(pkg)){ dumpPI('getPackageInfoAsUser['+argt+']',pkg,flags,r); var st=stack(); log('  AsUser-caller='+(st.indexOf('.getPackageInfo(')>=0?'VIA-getPackageInfo(framework内部转调)':'DIRECT(独立调用!)')); log('  AsUser-stackhead: '+st.split(/\n/).slice(1,6).join(' <- ')); }}catch(e){ log('AsUser cb err '+e);} return r; }; }); log('hooked getPackageInfoAsUser'); }catch(e){ log('no getPackageInfoAsUser'); }
  // Signature.toByteArray = 所有"读签名字节"的最终收口点。对自身官方签名(18c867f0)打 caller 栈(去重),
  // 看有没有【不经 getPackageInfo】的读取路径(getPackageArchiveInfo / 枚举 / 直取 Signature / 其它)。
  try{ var Sig=Java.use('android.content.pm.Signature'); Sig.toByteArray.implementation=function(){
    var r=this.toByteArray(); var m=md5(r);
    if(m.indexOf('18c867f0')===0){
      var st=stack(); var key=st.split(/\n/).slice(1,4).join('|');
      if(!seenSig[key]){ seenSig[key]=1;
        var via = st.indexOf('.getPackageInfo(')>=0 ? 'getPackageInfo'
                : (st.indexOf('getPackageArchiveInfo')>=0 ? 'getPackageArchiveInfo!!'
                : (st.indexOf('getInstalledPackages')>=0 ? 'getInstalledPackages!!' : 'OTHER-PATH!!'));
        log('SIG.toByteArray SELF(18c867f0) via='+via);
        log('  head: '+st.split(/\n/).slice(1,8).join(' <- '));
      }
    }
    return r;
  }; log('hooked Signature.toByteArray (self-caller trace)'); }catch(e){ log('Sig ERR '+e); }
  // 候选绕行路径A: getPackageArchiveInfo(读APK文件签名,不经已装包) —— 微信若读自身 base.apk 会绕过 getPackageInfo
  try{ APM.getPackageArchiveInfo.overloads.forEach(function(ov){ var argt=ov.argumentTypes.map(function(t){return t.className;}).join(','); ov.implementation=function(){ var r=ov.apply(this,arguments); try{ log('getPackageArchiveInfo['+argt+'] path='+String(arguments[0])); }catch(e){} return r; }; }); log('hooked getPackageArchiveInfo'); }catch(e){ log('no getPackageArchiveInfo'); }
  // 候选绕行路径B: getInstalledPackages(枚举所有包+签名) —— 微信若枚举再挑自己也绕过点名 getPackageInfo
  try{ APM.getInstalledPackages.overloads.forEach(function(ov){ var argt=ov.argumentTypes.map(function(t){return t.className;}).join(','); ov.implementation=function(){ var r=ov.apply(this,arguments); try{ var flags=0; for(var k=0;k<arguments.length;k++){if(typeof arguments[k]==='number'){flags=arguments[k];break;}} log('getInstalledPackages['+argt+'] flags=0x'+(flags>>>0).toString(16)+' '+((flags&SIGF||flags&CERTF)?'含签名!!':'无签名')); }catch(e){} return r; }; }); log('hooked getInstalledPackages'); }catch(e){ log('no getInstalledPackages'); }
  log('=== probe armed (官方=18c867f0) ===');
});
