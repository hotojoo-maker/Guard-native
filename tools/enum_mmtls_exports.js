'use strict';
// 防封官 B36：枚举 MMTLS 相关 SO 的导出符号，看 Crypt/EVP/HKDF/AES-GCM 能不能直接 hook（不用 Ghidra 找偏移）。
function log(t) { console.log('[EXP] ' + t); }
var libs = ['libwechatnetwork.so', 'libwechatmm.so', 'libMMProtocalJni.so'];
libs.forEach(function (L) {
  var m = Process.findModuleByName(L);
  if (!m) { log(L + ' NOT loaded'); return; }
  var exps = m.enumerateExports();
  log('=== ' + L + ' base=' + m.base + ' exports=' + exps.length + ' ===');
  var hit = exps.filter(function (e) { return /crypt|EVP_|hkdf|aes|gcm|seal|ecdh|sign|pack|hybrid/i.test(e.name); });
  log(L + ' crypto-related=' + hit.length);
  hit.slice(0, 50).forEach(function (e) { log('  ' + e.type + ' ' + e.name); });
});
log('=== enum done ===');
