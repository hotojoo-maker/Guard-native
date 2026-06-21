#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# B35 防封官: 暴力扫 libwechatnormsg.so —— 对 .data/.rodata 里的候选串跑 S6(对合 XOR),
# 捞解出来可读、且含检测关键词的明文(= 疑 normsg 隐藏检测串原文)。
# 思路: S6 密文按 C 串(0x00 分隔)切; 分别按 Latin-1(字节=码点) 和 UTF-8 解释后跑 S6; 过滤可读+关键词。
import sys

def s6(cps):
    n = len(cps); out = []
    for j in range(n):
        x = (cps[j] & 0xFFFF) ^ 0xFFA7
        b = (~((j + 1) ^ n)) & 0xFF
        if b & 0x80: b -= 0x100
        out.append((x ^ b) & 0xFFFF)
    return out

def printable_ratio(cps):
    if not cps: return 0.0
    return sum(1 for c in cps if 0x20 <= c < 0x7f) / len(cps)

KW = ('ro.boot', 'ro.secure', 'ro.debug', 'ro.product', 'ro.build', '/system/', '/proc/',
      '/data/', '/dev/', '/sbin', '/vendor/', '/odm/', 'magisk', 'frida', 'xposed', 'riru',
      'zygisk', 'busybox', 'clicfg', 'http', 'verity', 'vbmeta', 'selinux', 'superuser',
      '/su', 'daemonsu', '.dex', '.so', 'which ', 'mount', 'TracerPid', 'maps', 'status',
      'ptrace', 'oem_unlock', 'verifiedboot', 'persist.', 'getprop', 'pm ', 'package')

def scan(parts, decode_run, label, hits):
    for p in parts:
        cps = decode_run(p)
        if cps is None: continue
        L = len(cps)
        if L < 5 or L > 200: continue
        dec = s6(cps)
        if printable_ratio(dec) < 0.92: continue
        s = ''.join(chr(c) for c in dec)
        if sum(c.isalnum() for c in s) < 4: continue
        low = s.lower()
        if not any(k in low for k in KW): continue
        if s in hits: continue
        hits[s] = label

def dec_latin1(p):
    return list(p) if p else None

def dec_utf8(p):
    try:
        return [ord(c) for c in p.decode('utf-8')]
    except Exception:
        return None

def main():
    path = r'C:\Users\Me\AppData\Local\Temp\wechat8071_so\libwechatnormsg.so'
    data = open(path, 'rb').read()
    parts = data.split(b'\x00')
    hits = {}
    scan(parts, dec_latin1, 'L1', hits)
    scan(parts, dec_utf8, 'U8', hits)
    print('SO size=%d  parts=%d  关键词命中=%d' % (len(data), len(parts), len(hits)))
    for s, lab in sorted(hits.items()):
        print('[%s] %r' % (lab, s))

if __name__ == '__main__':
    main()
