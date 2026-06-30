# -*- coding: utf-8 -*-
"""frida CLI(-f spawn, 带 Java 桥) 跑 probe_libcso_remote.js，console.log 落 OUT，常驻 SECS。"""
import subprocess, sys, time

DEV = "609b4b18"
PKG = "com.tencent.mm"
SCRIPT = r"C:\Users\Me\Desktop\guard_native\03_execute_执行任务\P_HotUpdateFreeze_官方热更新冻结\recon\probe_libcso_remote.js"
OUT = r"C:\Users\Me\Desktop\guard_native\03_execute_执行任务\P_HotUpdateFreeze_官方热更新冻结\logs\cso_probe_out.txt"
SECS = int(sys.argv[1]) if len(sys.argv) > 1 else 90

cmd = ["frida", "-D", DEV, "-f", PKG, "-l", SCRIPT, "-o", OUT]
print("[RUN] " + " ".join(cmd) + "  secs=%d" % SECS, flush=True)
p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                     stderr=subprocess.STDOUT)
try:
    time.sleep(SECS)
finally:
    try: p.stdin.close()
    except Exception: pass
    try:
        p.terminate(); p.wait(timeout=5)
    except Exception:
        try: p.kill()
        except Exception: pass
print("[RUN] done -> " + OUT, flush=True)
