# -*- coding: utf-8 -*-
# Frida 常驻驱动：spawn 微信包 + 挂 hook_convtime.js，常驻收 send()/console.log。
# 用法: python convtime_driver.py <pkg> <script.js> [hold_seconds]
import sys, time, io, frida

PKG = sys.argv[1] if len(sys.argv) > 1 else "com.tencent.mn"
SCRIPT_PATH = sys.argv[2] if len(sys.argv) > 2 else "hook_convtime.js"
DURATION = int(sys.argv[3]) if len(sys.argv) > 3 else 600


def log_line(s):
    print(s, flush=True)


def on_message(message, data):
    t = message.get("type")
    if t == "send":
        log_line(str(message.get("payload")))
    elif t == "error":
        log_line("[JS-ERROR] " + str(message.get("stack", message)))


def on_log(level, text):
    log_line(text)


device = frida.get_usb_device(timeout=10)
pid = device.spawn([PKG])
session = device.attach(pid)
with io.open(SCRIPT_PATH, "r", encoding="utf-8") as f:
    src = f.read()
script = session.create_script(src)
script.on("message", on_message)
try:
    script.set_log_handler(on_log)
except Exception as e:
    log_line("[DRIVER] set_log_handler unavailable: %s" % e)
script.load()
device.resume(pid)
log_line("[DRIVER] spawned+resumed pid=%d pkg=%s holding=%ds" % (pid, PKG, DURATION))
time.sleep(DURATION)
log_line("[DRIVER] done")
