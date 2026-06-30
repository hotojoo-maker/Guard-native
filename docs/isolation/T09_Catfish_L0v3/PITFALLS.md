# T09 抓包踩坑记录
> 日期：2026-05-21
> 场景：Frida attach Catfish mn1（com.tencent.mn1）探针调试

---

## 坑 1 — frida -U 连错设备（多设备冲突）

**现象**：`frida -U -p <PID>` 报 `unable to find process with pid`，但 adb 命令正常。

**根因**：`-U` = "第一个 USB 设备"。同时插了 iPhone + Android 时，frida 优先选 iPhone，adb 选 Android，两者 PID 空间不同。

**查看所有设备**：
```python
import frida
for d in frida.enumerate_devices():
    print(d.id, d.name, d.type)
```

**正确姿势**：用 `-D <device_id>` 明确指定：
```powershell
frida -D 609b4b18 -p 5113 -l probe.js
```

---

## 坑 2 — frida CLI 遇到 stdin EOF 立刻退出（setInterval 无效）

**现象**：`frida ... | Tee-Object log.txt` 挂完 hook 后立刻打印 `Thank you for using Frida!` 退出，setInterval 不起作用。

**根因**：frida CLI 读 stdin，管道导致 stdin 立刻 EOF，frida 把它当"用户 quit"处理，JS runtime 随之销毁，setInterval 也一起没了。

**正确姿势**：用 Python subprocess，stdin 保持 PIPE 不关闭：
```python
proc = subprocess.Popen(
    ['frida', '-D', '609b4b18', '-p', PID, '-l', 'probe.js'],
    stdin=subprocess.PIPE,   # 关键：不是 DEVNULL
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT
)
for raw in proc.stdout:
    print(raw.decode('utf-8', errors='replace').rstrip(), flush=True)
```

---

## 坑 3 — Python API `console.log` 无输出

**现象**：用 `session.create_script()` 加载脚本，脚本里 `console.log("xxx")` 完全没有输出，`on_message` 回调也不触发。

**根因**：Frida Python API 中 `console.log` 走的是 `log` 类型消息，而不是 `send` 类型。如果 `on_message` 只处理 `send`，`console.log` 会被静默丢弃。

**正确姿势 A**：在 JS 里全部改用 `send()` 代替 `console.log()`：
```js
send('[T09] hookNewCon OK');
```

**正确姿势 B**：`on_message` 同时处理 log 类型：
```python
def on_message(message, data):
    if message['type'] == 'send':
        print(message['payload'], flush=True)
    elif message['type'] == 'log':
        print('[LOG]', message.get('payload',''), flush=True)
    elif message['type'] == 'error':
        print('[ERR]', message['stack'], flush=True)
```

---

## 坑 4 — Python API `Java is not defined`

**现象**：`session.create_script(src)` 加载 Android hook 脚本，报 `ReferenceError: Java is not defined`，但 frida CLI 模式正常。

**根因**：Frida Python API 在某些版本下，attach 到 Android 进程后 Java bridge 不自动启用。CLI 模式内部有额外初始化逻辑。

**解法**：放弃 Python API 直接创建 script，改用 subprocess 包装 CLI（见坑 2 方案）。

---

## 坑 5 — PowerShell `Add-Content` 写中文注释破坏 UTF-8

**现象**：用 PowerShell `Add-Content` 追加中文注释到 `.js` 文件后，Python `open(f, encoding='utf-8')` 报 `UnicodeDecodeError`。

**根因**：Windows PowerShell `Add-Content` 默认用系统编码（GBK/GB2312），写入的中文字节不是合法 UTF-8。

**正确姿势**：
- 用 Cursor 的 Write/StrReplace 工具写文件（始终 UTF-8）
- 或 PowerShell 加参数：`Add-Content -Encoding UTF8`
- 或直接避免在 JS 脚本里写中文注释，用英文

---

## 坑 6 — `--no-pause` 只用于 spawn，attach 报错

**现象**：`frida -D xxx -p PID -l script.js --no-pause` 报 `unrecognized arguments: --no-pause`。

**根因**：`--no-pause` 是 spawn 模式专用参数（`-f com.xxx.yyy --no-pause`），attach 到已运行进程不需要也不支持此参数。

---

## 坑 7 — Tee-Object 日志文件被锁

**现象**：重新运行探针时报 `文件正被另一个进程使用，无法访问`。

**根因**：上一个 Python/frida 进程没有完全退出，仍持有日志文件句柄。

**正确姿势**：每次换一个新文件名（`t09_out2.log`、`t09_out3.log`），或先确认旧进程已退出再跑。

---

## 坑 8 — `smali_classes17` 工作目录丢失，只剩备份

**现象**：`smali_classes17/` 目录不存在，smali 文件找不到。

**背景**：项目经历多次改动后，工作目录 `smali_classes17/` 被删除，只剩 `smali_classes17_未加密备份/`（含所有功能改动，无 XOR 加密和类名混淆）。

**正确姿势**：直接在 `smali_classes17_未加密备份/` 上操作，用它编译 DEX，不需要重新从原版反编译。

---

## 坑 9 — `repack.py` 硬编码原版 APK 路径失效

**现象**：`python repack.py` 报 `FileNotFoundError: 微密友8070官替稳定版.apk`。

**根因**：`repack.py` 里写死了原版 APK 路径，文件早已移走或重命名。

**正确姿势**：每次打包前先检查/更新 `repack.py` 顶部的 `apk = r'...'` 路径。原版 APK 实际在：
```
C:\Users\Me\Desktop\apk2\_1__B_rewrite\00_original_apk\量子密友版_会弹盗版v8.0.70.2.apk
```

---

## 坑 10 — `adb install -r` 报 `INSTALL_FAILED_VERSION_DOWNGRADE`

**现象**：设备上装的是 8.0.71，推送的是 8.0.70 改包，`adb install -r` 拒绝安装。

**根因**：Android 默认拒绝版本号降级安装。

**解法 A**：加 `-d` 参数强制降级：`adb install -r -d --no-incremental xxx.apk`（仍受签名约束）

**解法 B**（本次实际用法）：推送 APK 到手机存储，用户自行点击安装覆盖：
```powershell
adb push "xxx.apk" /sdcard/Download/catfish_vip.apk
```
手机文件管理 → Download → 点击安装 → 系统会提示"是否覆盖安装"。

---

## 坑 11 — 路径含中文，PowerShell 命令行传参乱码

**现象**：frida 命令行传中文路径（如 `03_execute_执行任务`）时，进程收到的是乱码，报 `No such file or directory`。

**根因**：PowerShell 终端编码（CP936）与 frida 进程期望的 UTF-8 不一致，中文字符被截断。

**正确姿势**：把脚本文件复制到纯 ASCII 路径再引用：
```powershell
Copy-Item "含中文路径\t09_probe.js" "C:\Users\Me\Desktop\apk\t09_probe.js"
frida -D xxx -p PID -l "C:\Users\Me\Desktop\apk\t09_probe.js"
```

---

## 快速检查清单（每次抓包前过一遍）

- [ ] `python -c "import frida; [print(d.id,d.name) for d in frida.enumerate_devices()]"` → 确认目标设备 ID
- [ ] `adb -s <device_id> shell "ps -A | grep mn1"` → 拿主进程 PID（排除 :push :appbrand）
- [ ] 脚本路径纯 ASCII，无中文目录
- [ ] JS 脚本全用 `send()` 不用 `console.log()`
- [ ] `repack.py` 顶部 `apk = r'...'` 路径指向真实存在的文件
- [ ] subprocess `stdin=subprocess.PIPE` 保持连接存活
