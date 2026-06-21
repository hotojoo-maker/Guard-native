#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# B35 防封官: normsg S6 (ql3.j.S6, plugin/normsg/u.java:367) XOR 解密器离线复刻。
# Java 原式:
#   int charAt = str.charAt(j) ^ 65447;   // 65447 = 0xFFA7
#   result    = (char)( charAt ^ (byte)(~((j+1) ^ len)) );
# 注: S6 是对合(involution)——同长度下 S6(S6(x))==x, 所以"解密"="加密", 本脚本既能解也能验。
#
# 用法:
#   python tools/s6_decode.py --str "<密文串>"                 # 直接给字符串(每字符当 1 个 UTF-16 code unit)
#   python tools/s6_decode.py --hex  "aabbcc..."              # 每 1 字节当 1 个 char code (0..255)
#   python tools/s6_decode.py --hex16 "00aa00bb..."          # 每 2 字节(大端)当 1 个 UTF-16 char
#   python tools/s6_decode.py --selftest                     # 自检(对合验证)
import sys, argparse

def s6(chars):
    """chars: list[int] (每个 0..65535 的 code unit) -> 解码后的 str"""
    n = len(chars)
    out = []
    for j in range(n):
        x = (chars[j] & 0xFFFF) ^ 0xFFA7
        b = (~((j + 1) ^ n)) & 0xFF       # low 8 bits
        if b & 0x80:
            b -= 0x100                     # (byte) 符号扩展 -> -128..127
        out.append((x ^ b) & 0xFFFF)       # (char) 取低 16 位
    return ''.join(chr(c) for c in out)

def selftest():
    ok = True
    for plain in ["magisk", "su", "/system/xbin/su", "frida-server", "clicfg_x", "ro.boot.verifiedbootstate"]:
        ct = s6([ord(c) for c in plain])             # 加密(对合)
        rt = s6([ord(c) for c in ct])                # 再跑一次 = 解密
        flag = "OK" if rt == plain else "FAIL"
        if rt != plain:
            ok = False
        print("[selftest] %-28s -> cipher(%d B) -> %s" % (repr(plain), len(ct), flag))
    print("[selftest] involution:", "PASS" if ok else "FAIL")

def main():
    ap = argparse.ArgumentParser(description="normsg S6 XOR 解密器(离线)")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument('--str')
    g.add_argument('--hex')
    g.add_argument('--hex16')
    g.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        selftest(); return
    if a.str is not None:
        chars = [ord(c) for c in a.str]
    elif a.hex is not None:
        bs = bytes.fromhex(a.hex.replace(' ', '').replace('0x', ''))
        chars = list(bs)
    else:
        bs = bytes.fromhex(a.hex16.replace(' ', '').replace('0x', ''))
        chars = [(bs[i] << 8) | bs[i + 1] for i in range(0, len(bs) - 1, 2)]
    dec = s6(chars)
    print('len   =', len(chars))
    print('plain = %r' % dec)
    print('plain =', dec)

if __name__ == '__main__':
    main()
