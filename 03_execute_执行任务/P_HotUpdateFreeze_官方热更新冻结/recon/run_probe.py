# -*- coding: utf-8 -*-
"""spawn com.tencent.mm with probe_libcso_remote.js, capture console.log ~SECS, dump."""
import sys, time, io
import frida

DEV = "609b4b18"
PKG = "com.tencent.mm"
SCRIPT = r"C:\Users\Me\Desktop\guard_native\03_execute_执行任务\P_HotUpdateFreeze_官方热更新冻结\recon\probe_libcso_remote.js"
SECS = int(sys.argv[1]) if len(sys.argv) > 1 else 90

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

def on_message(message, data):
    t = message.get("type")
    if t == "log":
        print(message.get("payload"), flush=True)
    elif t == "error":
        print("ERR " + str(message.get("description")), flush=True)
    else:
        print("MSG " + str(message), flush=True)

dev = frida.get_device(DEV, timeout=10)
print("device ok: " + dev.name, flush=True)
pid = dev.spawn([PKG])
print("spawned pid=" + str(pid), flush=True)
session = dev.attach(pid)
with io.open(SCRIPT, "r", encoding="utf-8") as f:
    code = f.read()
script = session.create_script(code)
script.on("message", on_message)
script.load()
dev.resume(pid)
print("resumed, capturing %ds ..." % SECS, flush=True)
time.sleep(SECS)
print("=== capture done ===", flush=True)
try:
    session.detach()
except Exception:
    pass
