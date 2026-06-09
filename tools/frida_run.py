#!/usr/bin/env python3
# frida_run.py — 稳定驱动：attach 后固定 sleep，不依赖交互终端（绕开 stdin EOF 自退）
# 用法: python frida_run.py <device_id> <pid> <script.js> <out.log> <seconds>
import sys, time, frida

dev_id = sys.argv[1]
pid = int(sys.argv[2])
script_path = sys.argv[3]
out_path = sys.argv[4]
seconds = int(sys.argv[5])

out = open(out_path, 'w', encoding='utf-8')

def log(line):
    out.write(line + '\n')
    out.flush()

def on_message(message, data):
    if message.get('type') == 'log':
        log(message.get('payload', ''))
    elif message.get('type') == 'error':
        log('[ERROR] ' + str(message.get('description', message)))
    else:
        log('[MSG] ' + str(message))

dev = frida.get_device(dev_id, timeout=10)
session = dev.attach(pid)
with open(script_path, 'r', encoding='utf-8') as f:
    src = f.read()
script = session.create_script(src)
script.on('message', on_message)
script.load()
log('[RUNNER] attached pid=%d, sleeping %ds' % (pid, seconds))
try:
    time.sleep(seconds)
finally:
    log('[RUNNER] done, detaching')
    try:
        session.detach()
    except Exception:
        pass
    out.close()
