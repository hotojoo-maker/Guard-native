"""run_probe.py — 通用 frida 脚本包装器（PowerShell stdin 友好）
用法:
    python tools/run_probe.py <PID> <script_path> [--timeout SEC]
默认 timeout=600 秒。Ctrl+C 提前结束。
"""
import sys, os, time, argparse, signal

import frida


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pid", type=int)
    parser.add_argument("script", type=str)
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()

    if not os.path.exists(args.script):
        print(f"[PY] script not found: {args.script}", flush=True)
        sys.exit(2)

    device = frida.get_usb_device()
    session = device.attach(args.pid)

    with open(args.script, "r", encoding="utf-8") as f:
        src = f.read()

    try:
        script = session.create_script(src, runtime="v8")
    except TypeError:
        script = session.create_script(src)

    def on_message(message, data):
        mtype = message.get("type")
        if mtype == "send":
            print("[SEND]", message.get("payload"), flush=True)
        elif mtype == "log":
            level = message.get("level", "info")
            payload = message.get("payload", "")
            print(payload, flush=True)
        elif mtype == "error":
            print("[ERR]", message.get("description"), flush=True)
            stack = message.get("stack")
            if stack:
                print(stack, flush=True)
        else:
            print("[?]", message, flush=True)

    script.on("message", on_message)
    script.load()
    print(f"[PY] loaded {args.script}, sleep {args.timeout}s (Ctrl+C 提前结束)", flush=True)

    stopped = {"flag": False}
    def _sigint(_sig, _frm):
        stopped["flag"] = True
        print("[PY] SIGINT 收到，准备退出", flush=True)
    try:
        signal.signal(signal.SIGINT, _sigint)
    except Exception:
        pass

    t0 = time.time()
    while time.time() - t0 < args.timeout and not stopped["flag"]:
        time.sleep(0.5)

    try:
        script.unload()
    except Exception:
        pass
    try:
        session.detach()
    except Exception:
        pass
    print("[PY] done", flush=True)


if __name__ == "__main__":
    main()
