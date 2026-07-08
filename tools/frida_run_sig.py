import frida, sys, time

# USB 直连（小米9 插本机，走 -U）；抓官方原版冷启动+登录期"读自身签名"走哪个重载 / MD5
TARGET = sys.argv[1] if len(sys.argv) > 1 else "com.tencent.mm"
JS = r"c:\Users\Me\Desktop\guard_native\tools\dump_sig_md5.compiled.js"
WAIT = int(sys.argv[2]) if len(sys.argv) > 2 else 30

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

for attempt in range(1, 6):
    print("=== spawn %s attempt %d ===" % (TARGET, attempt), flush=True)
    pid = None
    try:
        pid = dev.spawn([TARGET])
        session = dev.attach(pid)
        script = session.create_script(code)
        script.on("message", on_msg)
        script.load()
        dev.resume(pid)
        print("resumed pid=%d, waiting %ds for sig reads" % (pid, WAIT), flush=True)
        time.sleep(WAIT)
        print("=== done attempt %d (target survived) ===" % attempt, flush=True)
        break
    except Exception as e:
        print("attempt %d FAIL: %r" % (attempt, e), flush=True)
        if pid:
            try: dev.kill(pid)
            except Exception: pass
        time.sleep(2)
