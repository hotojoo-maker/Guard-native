import frida, sys, time

sys.stdout.reconfigure(encoding="utf-8")

SCRIPT = r"c:\Users\Me\Desktop\guard_native\03_execute_执行任务\P21_MomentsRedDot\scripts\_probe_build\probe_bundle.js"
DEVICE = "609b4b18"
PROC_NAME = "com.tencent.mm"
HOLD_SECONDS = 600


def on_message(message, data):
    if message.get("type") == "log":
        print(message.get("payload"), flush=True)
    elif message.get("type") == "error":
        print("[FRIDA-ERROR]", message.get("stack") or message.get("description"), flush=True)
    else:
        print("[MSG]", message, flush=True)


dev = frida.get_device(DEVICE)
pid = int(sys.argv[1]) if len(sys.argv) > 1 else None
if pid is None:
    print("[runner] usage: run_probe_badge.py <pid>", flush=True)
    sys.exit(1)
session = dev.attach(pid)
with open(SCRIPT, encoding="utf-8") as f:
    src = f.read()
script = session.create_script(src)
script.on("message", on_message)
script.load()
print(f"[runner] loaded + attached to pid {pid}, holding {HOLD_SECONDS}s", flush=True)

end = time.time() + HOLD_SECONDS
while time.time() < end:
    time.sleep(2)
print("[runner] done", flush=True)
