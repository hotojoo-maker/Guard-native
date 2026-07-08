import frida, sys, time

# warm-attach 到已运行的官方原版主进程（切账号/重登=运行期事件，不能 spawn 重启）。
# 复用扩容探针 dump_sig_md5.compiled.js（getPackageInfo/AsUser/Archive/Installed + toByteArray 收口栈）。
TARGET = sys.argv[1] if len(sys.argv) > 1 else "com.tencent.mm"
JS = r"c:\Users\Me\Desktop\guard_native\tools\dump_sig_md5.compiled.js"
WAIT = int(sys.argv[2]) if len(sys.argv) > 2 else 120

def on_msg(m, d):
    t = m.get("type")
    if t == "log":
        print(m.get("payload"), flush=True)
    elif t == "send":
        print("SEND:", m.get("payload"), flush=True)
    else:
        print("MSG:", m, flush=True)

dev = frida.get_usb_device(timeout=10)
print("USB DEVICE:", dev, flush=True)
code = open(JS, encoding="utf-8").read()

# 按名找不到时(frida enum 偶发)用 PID：传数字即按 PID attach
tgt = int(TARGET) if TARGET.isdigit() else TARGET
session = dev.attach(tgt)
try:
    session._impl.timeout = 60000
except Exception:
    pass
print("ATTACHED to", tgt, flush=True)
script = session.create_script(code)
script.on("message", on_msg)
script.load()
print("=== PROBE ARMED (warm-attach) — 现在可以操作切换账号 ===", flush=True)
time.sleep(WAIT)
print("=== done (waited %ds) ===" % WAIT, flush=True)
