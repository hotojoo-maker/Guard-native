import frida, sys, time

LOG = r"C:\Users\Me\Desktop\guard_native\tools\like_source_trace.log"
JS  = r"C:\Users\Me\Desktop\guard_native\tools\like_source_trace.js"

def on_message(message, data):
    line = ""
    if message["type"] == "send":
        line = str(message.get("payload", ""))
    elif message["type"] == "log":
        line = str(message.get("payload", ""))
    elif message["type"] == "error":
        line = "[ERR] " + str(message.get("description", ""))
    if line:
        print(line, flush=True)
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")

device  = frida.get_device("609b4b18")
session = device.attach(32276)
with open(JS, encoding="utf-8") as f:
    code = f.read()
bridge = "const Java = require('@frida/java-bridge');\n"
script = session.create_script(bridge + code)
script.on("message", on_message)
script.load()
print("[run_trace] script loaded, 25s window...", flush=True)
time.sleep(25)
print("[run_trace] done", flush=True)
session.detach()
