# 临时 frida runner：attach 目标进程，load 一个用 send() 输出的脚本，把 payload 落 UTF-8 文件。
# 用法: python tools/frida_dump_runner.py <pkg> <script.js> <out.txt> [wait_seconds]
import frida, sys, io, time

pkg = sys.argv[1]
spath = sys.argv[2]
outpath = sys.argv[3] if len(sys.argv) > 3 else None
wait_s = float(sys.argv[4]) if len(sys.argv) > 4 else 8.0

buf = []
done = {"v": False}

def on_message(message, data):
    if message.get("type") == "send":
        buf.append(str(message.get("payload")))
    else:
        buf.append("ERRMSG: " + str(message))
    done["v"] = True

dev = frida.get_usb_device()
target = int(pkg) if pkg.isdigit() else pkg
session = dev.attach(target)
code = io.open(spath, encoding="utf-8").read()
script = session.create_script(code)
script.on("message", on_message)
script.load()

t = 0.0
while not done["v"] and t < wait_s:
    time.sleep(0.2)
    t += 0.2

try:
    session.detach()
except Exception:
    pass

text = "\n".join(buf) if buf else "NO_MESSAGE_RECEIVED"
if outpath:
    io.open(outpath, "w", encoding="utf-8").write(text)
    print("WROTE " + outpath + " (" + str(len(text)) + " chars)")
else:
    print(text)
