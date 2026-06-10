# -*- coding: utf-8 -*-
# 用 Python 包一层 frida CLI（frida_tools.repl，自带 Java bridge），
# 通过"持有一个不关闭的 stdin 管道"让 CLI 不收到 EOF，从而保持 attach。
# 用法：python run_frida_cli.py <PID> <脚本.js> [秒数=150]
import subprocess, sys, time, threading, io

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

pid = sys.argv[1]
script = sys.argv[2]
dur = int(sys.argv[3]) if len(sys.argv) > 3 else 150
out_path = sys.argv[4] if len(sys.argv) > 4 else None
out_f = io.open(out_path, 'w', encoding='utf-8') if out_path else None

cmd = [sys.executable, '-m', 'frida_tools.repl',
       '-D', '609b4b18', '-p', pid, '-l', script]
print('LAUNCH ' + ' '.join(cmd), flush=True)

proc = subprocess.Popen(
    cmd,
    stdin=subprocess.PIPE,              # 持有但不关闭 → 无 EOF → CLI 不退出
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    bufsize=1,
    universal_newlines=True,
    encoding='utf-8',
    errors='replace',
)

def reader():
    for line in proc.stdout:
        try:
            sys.stdout.write(line)
            sys.stdout.flush()
        except Exception:
            pass
        if out_f:
            try:
                out_f.write(line)
                out_f.flush()
            except Exception:
                pass

threading.Thread(target=reader, daemon=True).start()

time.sleep(dur)
try:
    proc.stdin.close()
except Exception:
    pass
try:
    proc.terminate()
except Exception:
    pass
time.sleep(1)
print('[driver] done', flush=True)
