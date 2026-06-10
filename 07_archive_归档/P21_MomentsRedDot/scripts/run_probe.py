import frida, sys, time

sys.stdout.reconfigure(encoding="utf-8")

DEVICE = "609b4b18"


def on_message(message, data):
    if message.get("type") == "log":
        print(message.get("payload"), flush=True)
    elif message.get("type") == "error":
        print("[FRIDA-ERROR]", message.get("stack") or message.get("description"), flush=True)
    else:
        print("[MSG]", message, flush=True)


def find_mm_pid(dev):
    cands = []
    for p in dev.enumerate_processes():
        n = p.name or ""
        if n == "com.tencent.mm":
            return p.pid
        if n.startswith("com.tencent.mm") and ":" not in n:
            cands.append(p.pid)
    return cands[0] if cands else None


if len(sys.argv) < 3:
    print("[runner] usage: run_probe.py <pid|mm|name> <bundle.js> [hold_seconds]", flush=True)
    sys.exit(1)

target = sys.argv[1]
script_path = sys.argv[2]
hold = int(sys.argv[3]) if len(sys.argv) > 3 else 60

dev = frida.get_device(DEVICE)

session = None
for attempt in range(12):
    try:
        if target == "mm":
            pid = find_mm_pid(dev)
            if pid is None:
                print(f"[runner] mm main not running (try {attempt+1}/12) — open WeChat", flush=True)
                time.sleep(1)
                continue
            session = dev.attach(pid)
            print(f"[runner] attached mm pid {pid}", flush=True)
        else:
            t = int(target) if str(target).isdigit() else target
            session = dev.attach(t)
            print(f"[runner] attached {t}", flush=True)
        break
    except Exception as e:
        print(f"[runner] attach try {attempt+1}/12 failed: {e}", flush=True)
        time.sleep(1)

if session is None:
    print("[runner] ATTACH FAILED after retries", flush=True)
    sys.exit(1)

with open(script_path, encoding="utf-8") as f:
    src = f.read()
script = session.create_script(src)
script.on("message", on_message)
script.load()
print(f"[runner] script loaded, holding {hold}s", flush=True)

end = time.time() + hold
while time.time() < end:
    time.sleep(1)
print("[runner] done", flush=True)
