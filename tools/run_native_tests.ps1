#!/usr/bin/env pwsh
# Guard Native — native crypto + registry self-test runner.
# =========================================================
# Builds tools/test_config_crypto.cpp against the SO crypto/registry sources and
# runs it, so decrypt_config_self_test() + registry_self_test() are caught at
# build/CI time instead of only at install-time logcat (PHASE*_VERIFY).
#
# Exit code: 0 only when ALL=PASS. Non-zero on compile failure OR any test fail.
#
# Auto mode selection:
#   1. Host C++ compiler (clang++ / g++ on PATH) -> compile + run locally
#      (best for headless CI; no device needed).
#   2. Fallback: Android NDK clang -> compile for the connected device's ABI,
#      adb push + run a tiny standalone binary (NOT an app install, NOT logcat).
#
# Scope guard: this only builds the TEST. It never touches the SO build, hooks,
# or registry content. The test mirrors gen_registry_cipher.py's cert binding
# (set_binding_material) so the cert-only embedded registry decrypts here too.

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

$srcs = @(
    (Join-Path $repo "native_core\src\config_crypto.cpp"),
    (Join-Path $repo "native_core\src\registry_loader.cpp"),
    (Join-Path $repo "tools\test_config_crypto.cpp")
)
$inc = Join-Path $repo "native_core\include"
$cipher = Join-Path $repo "native_core\src\registry_cipher.inc"

foreach ($f in ($srcs + $cipher + $inc)) {
    if (-not (Test-Path $f)) { Write-Error "missing required path: $f"; exit 2 }
}

$cflags = @("-std=c++17", "-fno-rtti", "-fno-exceptions", "-I", $inc)

function Find-HostCxx {
    foreach ($c in @("clang++", "g++")) {
        $cmd = Get-Command $c -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    return $null
}

function Get-SdkDir {
    $lp = Join-Path $repo "local.properties"
    if (Test-Path $lp) {
        $line = (Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1)
        if ($line) {
            # Java .properties escaping: "\:" -> ":", "\\" -> "\".
            return (($line -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\(.)', '$1').Trim()
        }
    }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME)     { return $env:ANDROID_HOME }
    return (Join-Path $env:LOCALAPPDATA "Android\Sdk")
}

function Get-NdkClang($sdk) {
    $ndkRoot = Join-Path $sdk "ndk"
    if (-not (Test-Path $ndkRoot)) { return $null }
    $ver = Get-ChildItem $ndkRoot -Directory -ErrorAction SilentlyContinue |
           Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ver) { return $null }
    $clang = Join-Path $ver.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe"
    if (Test-Path $clang) { return $clang }
    return $null
}

function AbiToTarget($abi) {
    switch ($abi) {
        "arm64-v8a"   { return "aarch64-linux-android21" }
        "armeabi-v7a" { return "armv7a-linux-androideabi21" }
        "x86_64"      { return "x86_64-linux-android21" }
        "x86"         { return "i686-linux-android21" }
        default       { return "aarch64-linux-android21" }
    }
}

$ok = $false

# ── Mode 1: host compiler (CI-friendly) ───────────────────────
$hostCxx = Find-HostCxx
if ($hostCxx) {
    Write-Host "[run_native_tests] host compiler: $hostCxx"
    $out = Join-Path $repo ".tmp_native_test.exe"
    & $hostCxx @cflags @srcs "-o" $out
    if ($LASTEXITCODE -ne 0) { Write-Error "host compile failed"; exit 3 }
    Write-Host "[run_native_tests] running host binary ..."
    & $out
    $rc = $LASTEXITCODE
    Remove-Item $out -ErrorAction SilentlyContinue
    if ($rc -eq 0) { $ok = $true }
}
else {
    # ── Mode 2: NDK build + adb run on the connected device ────
    $sdk = Get-SdkDir
    $clang = Get-NdkClang $sdk
    $adb = Join-Path $sdk "platform-tools\adb.exe"
    if (-not $clang) { Write-Error "no host compiler and no NDK clang found (sdk=$sdk)"; exit 4 }
    if (-not (Test-Path $adb)) { Write-Error "adb not found at $adb"; exit 4 }

    $devLine = (& $adb devices | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1)
    if (-not $devLine) { Write-Error "no adb device connected (and no host compiler for CI mode)"; exit 5 }

    $abi = (& $adb shell getprop ro.product.cpu.abi).Trim()
    $target = AbiToTarget $abi
    Write-Host "[run_native_tests] NDK clang: $clang"
    Write-Host "[run_native_tests] device abi=$abi target=$target"

    $bin = Join-Path $repo ".tmp_native_test.elf"
    & $clang "--target=$target" @cflags @srcs "-static-libstdc++" "-o" $bin
    if ($LASTEXITCODE -ne 0) { Write-Error "NDK compile failed"; exit 3 }

    $remote = "/data/local/tmp/guard_native_test"
    & $adb push $bin $remote | Out-Null
    & $adb shell chmod 755 $remote | Out-Null
    Write-Host "[run_native_tests] running on device ..."
    & $adb shell $remote
    $rc = $LASTEXITCODE
    & $adb shell rm -f $remote | Out-Null
    Remove-Item $bin -ErrorAction SilentlyContinue
    if ($rc -eq 0) { $ok = $true }
}

if ($ok) {
    Write-Host "[run_native_tests] RESULT: PASS"
    exit 0
} else {
    Write-Host "[run_native_tests] RESULT: FAIL"
    exit 1
}
