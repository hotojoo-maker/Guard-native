# -*- coding: utf-8 -*-
"""
M6a 阶段A 探针驱动（非交互）。
为什么用 Python 而不是 frida CLI：
  - 本机插着 iPhone + Android 两台 USB 设备，frida -U 会抓到 iPhone；
  - frida CLI 的 REPL 用 prompt_toolkit，在无 console 的后台会 NoConsoleScreenBufferError。
本脚本：指定 Android 设备 → attach com.tencent.mm → load JS → 保活 WINDOW 秒 → detach。
JS 的 console.log 通过 on_message 落到 log 文件（UTF-8）。
"""
import sys, time, frida

DEVICE_ID = "609b4b18"          # Android adb 序列号（= frida 设备 id）
TARGET_PID = 17344              # com.tencent.mm 主进程（ps 实测）
TARGET_NAME = "com.tencent.mm"
JS_PATH = r"C:\Users\Me\Desktop\guard_native\03_execute_执行任务\M6a_MomentsGroupIcon\probe_moments_groupicon_8071.js"
LOG_PATH = r"C:\Users\Me\Desktop\guard_native\03_execute_执行任务\M6a_MomentsGroupIcon\logs\groupicon_probe_20260610.log"
WINDOW_SEC = 125

logf = open(LOG_PATH, "w", encoding="utf-8")
def out(s):
    try:
        print(s)
    except Exception:
        pass
    logf.write(str(s) + "\n")
    logf.flush()

def on_message(message, data):
    t = message.get("type")
    if t == "log":
        out(message.get("payload", ""))
    elif t == "send":
        out("[send] " + str(message.get("payload", "")))
    elif t == "error":
        out("[error] " + str(message.get("stack") or message.get("description")))
    else:
        out("[msg] " + str(message))

# --- 选 Android 设备（避开 iPhone） ---
dev = None
try:
    dev = frida.get_device(DEVICE_ID, timeout=8)
except Exception as e:
    out("[drv] get_device(%s) 失败: %s，改用枚举" % (DEVICE_ID, e))
    for d in frida.enumerate_devices():
        nm = (d.name or "").lower()
        out("[dev] id=%s type=%s name=%s" % (d.id, d.type, d.name))
        if d.type == "usb" and "iphone" not in nm and "apple" not in nm:
            dev = d
if dev is None:
    out("[FATAL] 没找到 Android USB 设备"); logf.close(); sys.exit(1)
out("[drv] device = %s (%s)" % (dev.id, dev.name))

# --- 解析主进程 PID：优先命令行参数（外部 adb pidof 传入，最可靠），否则枚举/兜底 ---
pid = None
if len(sys.argv) > 1:
    try:
        pid = int(sys.argv[1])
    except Exception:
        pid = None
if pid is None:
    try:
        for p in dev.enumerate_processes():
            nm = p.name or ""
            if nm == TARGET_NAME or "tencent.mm" in nm.lower():
                pid = p.pid; break
    except Exception as e:
        out("[drv] enumerate 失败: %s" % e)
if pid is None:
    pid = TARGET_PID
out("[drv] target pid = %s" % str(pid))
try:
    session = dev.attach(pid)
    out("[drv] attached to %s" % str(pid))
except Exception as e:
    out("[FATAL] attach(%s) 失败: %s" % (str(pid), e)); logf.close(); sys.exit(2)

BRIDGE_PATH = r"C:\Users\Me\miniconda3\Lib\site-packages\frida_tools\bridges\java.js"
with open(BRIDGE_PATH, "r", encoding="utf-8") as f:
    bridge_src = f.read()
with open(JS_PATH, "r", encoding="utf-8") as f:
    probe_src = f.read()
# frida 17 不再内置 Java 桥，CLI 是惰性注入；这里按 frida-tools 同样的包法
# 把 java.js 包成 IIFE，定义出 global Java，再接探针代码。
combined = ("(function () {\n" + bridge_src
            + "\nObject.defineProperty(globalThis, 'Java', { value: bridge });\n})();\n"
            + probe_src)
script = session.create_script(combined)
script.on("message", on_message)
script.set_log_handler(lambda level, text: out(text))   # frida 把 console.log 走 log handler
script.load()
out("[drv] script loaded, window=%ds —— 现在请进朋友圈滑到目标那条" % WINDOW_SEC)

time.sleep(WINDOW_SEC)
out("[drv] window done, detaching")
try:
    session.detach()
except Exception:
    pass
logf.close()
