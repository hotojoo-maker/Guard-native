# rotate_sign_official.ps1
# 官替线：打包 official release → 建 旧debug→新official 密钥轮换证明(lineage) → 用 official key + lineage 重签 → 验证。
# 不装机（装机是单独一步）。密码从 signing/keystore.properties 读，无需手敲。
# 用法：在仓库根目录 PowerShell 运行：  .\tools\win\rotate_sign_official.ps1
$ErrorActionPreference = "Stop"

# --- 定位仓库根（脚本在 tools\win\ 下）---
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $repo
Write-Host "[*] repo: $repo"

# --- 工具路径 ---
$sdk = "C:\Users\Me\AppData\Local\Android\Sdk"
$apksigner = Join-Path $sdk "build-tools\34.0.0\apksigner.bat"
if (-not (Test-Path $apksigner)) { throw "apksigner 不存在: $apksigner" }

# --- 读 keystore.properties（取 official 密码/别名/路径）---
$props = @{}
Get-Content "signing\keystore.properties" | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.+?)\s*$') { $props[$matches[1]] = $matches[2] }
}
$offKs    = $props["guardOfficialStoreFile"]
$offAlias = $props["guardOfficialKeyAlias"]
$offPass  = $props["guardOfficialStorePassword"]
$offKey   = $props["guardOfficialKeyPassword"]
if (-not $offPass) { throw "keystore.properties 未读到 guardOfficialStorePassword" }
Write-Host "[*] official keystore: $offKs (alias=$offAlias)"

$debugKs = "signing\guard-native-debug.keystore"
$lineage = "signing\guard-debug-to-official.lineage"

# --- 1) 打包 official release ---
Write-Host "[1/5] gradlew clean assembleOfficialRelease ..."
& ".\gradlew.bat" clean assembleOfficialRelease
if ($LASTEXITCODE -ne 0) { throw "gradle 构建失败" }

$apk = Get-ChildItem "build\outputs\apk\official\release\*.apk" | Select-Object -First 1
if (-not $apk) { throw "没找到 official release APK" }
Write-Host "[*] APK: $($apk.FullName)"

# --- 2) 生成 旧debug→新official 轮换证明(lineage)，只需建一次 ---
if (Test-Path $lineage) {
    Write-Host "[2/5] lineage 已存在，复用: $lineage"
} else {
    Write-Host "[2/5] apksigner rotate (debug -> official) ..."
    & $apksigner rotate --out $lineage `
        --old-signer --ks $debugKs --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android `
        --new-signer --ks $offKs   --ks-key-alias $offAlias       --ks-pass "pass:$offPass" --key-pass "pass:$offKey"
    if ($LASTEXITCODE -ne 0) { throw "apksigner rotate 失败" }
    Write-Host "[*] lineage 已生成: $lineage"
}

# --- 3) 用 lineage 重签：必须按 lineage 顺序提供两把签名者 ---
#   旧 debug = 最旧签名者，official = --next-signer(当前签名者，v3 轮换)。
#   ⚠ --rotation-min-sdk-version 28：apksigner 默认把轮换只放到 API33+(v3.1)，会导致 API28-32
#     设备(含 Mi9)读到的当前签名者仍是 debug → registry/CompatProbe 绑 debug → 官替线散沙。
#     置 28 让轮换从 API28 生效，Mi9(API28+) 当前签名者 = official。API<28(仅 27) 无 v3 走 v2=debug。
Write-Host "[3/5] apksigner sign (debug=oldest, official=current, + lineage) ..."
& $apksigner sign `
    --ks $debugKs --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android `
    --next-signer --ks $offKs --ks-key-alias $offAlias --ks-pass "pass:$offPass" --key-pass "pass:$offKey" `
    --lineage $lineage `
    --rotation-min-sdk-version 28 `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    $apk.FullName
if ($LASTEXITCODE -ne 0) { throw "apksigner sign 失败" }

# --- 4) 验证签名 + 打印证书（应显示轮换 lineage，当前证书=official）---
Write-Host "[4/5] apksigner verify -v --print-certs ..."
& $apksigner verify -v --print-certs $apk.FullName

# --- 5) 提取"当前签名者(v3)"证书 SHA-256，应 = e3e13a49...adecf36 ---
#   ⚠ 不能用 keytool -printcert -jarfile：它只读 v1/JAR 层，轮换包那里是兼容用的旧 debug 证书，会误导。
#   以 apksigner verify --print-certs 的 "Signer #1 ... SHA-256" 为准（= 平台 API28+ 实际采用的当前签名者）。
Write-Host "[5/5] 当前签名者(v3, 平台 API28+ 采用)证书 SHA-256（应 = e3e13a49...adecf36）:"
& $apksigner verify --print-certs $apk.FullName | Select-String "Signer #1 certificate SHA-256"

Write-Host ""
Write-Host "[DONE] APK 路径（装机用这个）: $($apk.FullName)"
