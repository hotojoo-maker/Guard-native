#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# B35 防封官: 列 ELF(.so) 的导入符号(UNDEF dynsym), 筛 文件/crypto/zip/属性 相关。
# 用途: 判 normsg 是否具备 native 直读+hash APK 签名的能力(无 crypto/zip 导入 = 基本排除)。
import struct, sys, re

def main():
    path = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Me\AppData\Local\Temp\wechat8071_so\libwechatnormsg.so'
    d = open(path, 'rb').read()
    if d[:4] != b'\x7fELF':
        print('not ELF'); return
    e_shoff = struct.unpack_from('<Q', d, 0x28)[0]
    e_shentsize = struct.unpack_from('<H', d, 0x3a)[0]
    e_shnum = struct.unpack_from('<H', d, 0x3c)[0]
    secs = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        vals = struct.unpack_from('<IIQQQQIIQQ', d, off)  # name,type,flags,addr,offset,size,link,info,align,entsize
        secs.append(vals)
    dynsym = next((s for s in secs if s[1] == 11), None)  # SHT_DYNSYM=11
    if not dynsym:
        print('no .dynsym'); return
    stroff = secs[dynsym[6]][4]  # link -> .dynstr offset

    def gs(o):
        e = d.index(b'\x00', stroff + o)
        return d[stroff + o:e].decode('latin1')

    off, size, ent = dynsym[4], dynsym[5], (dynsym[9] or 24)
    imports = []
    for o in range(off, off + size, ent):
        st_name, st_info, st_other, st_shndx, st_value, st_size = struct.unpack_from('<IBBHQQ', d, o)
        if st_shndx == 0 and st_name != 0:
            imports.append(gs(st_name))
    imports = sorted(set(imports))
    print('SO =', path)
    print('total imports =', len(imports))
    pat = re.compile(r'open|read|mmap|fopen|stat|EVP|SHA|MD5|X509|d2i|BIO|inflate|unz|zip|crypt|digest|cert|pem|asn1|rsa|getauxval|property|popen|dlopen|access|pread|fread|getentropy|ioctl|syscall|binder|[Pp]arcel|transact|IPCThread|BpBinder|libbinder', re.I)
    print('--- 文件 / crypto / zip / 属性 相关导入 ---')
    hit = [s for s in imports if pat.search(s)]
    for s in hit:
        print('  ', s)
    print('--- 命中 %d / %d ---' % (len(hit), len(imports)))

if __name__ == '__main__':
    main()
