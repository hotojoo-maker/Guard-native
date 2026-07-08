#!/usr/bin/env pwsh
# Guard Native -- LSPatch one-shot pack
# =============================================================================
# Wraps the LSPatch command from docs/RELEASE_RULES.md "coexist build + clean
# install" / guard-release SKILL main flow step C. Saves typing the same 6
# arguments every time and auto-resolves host APK + module APK + signing
# material by flavor / buildType.
#
#   Exit 0 = PASS (lspatched APK produced under -Output)
#   Exit 1 = FAIL (gradle / LSPatch / missing input)
#
# Usage (PS 5.1+):
#   .\tools\lspatch_pack.ps1 -Flavor coexist                  # coexist debug, module pre-built
#   .\tools\lspatch_pack.ps1 -Flavor coexist -Build           # also runs :assembleCoexistDebug
#   .\tools\lspatch_pack.ps1 -Flavor coexist -Clean -Build    # clean + rebuild
#   .\tools\lspatch_pack.ps1 -Flavor official -BuildType release -Clean -Build
#   .\tools\lspatch_pack.ps1 -Flavor coexist -HostApk path\to\custom.apk
#
# Design notes:
#   - LSPatch outer signing key is chosen by -BuildType:
#       release -> reads signing/keystore.properties (guardOfficial* keys).
#                  Both official and coexist release modules are signed by the
#                  official jks (cert-converge v2, see SSOT). LSPatch outer
#                  signature MUST match the module's own embedded signature,
#                  otherwise installing an upgrade later trips
#                  INSTALL_FAILED_UPDATE_INCOMPATIBLE. ModuleMain.bindSigningCert
#                  reads the module's embedded cert SHA-256; if that differs
#                  from BuildConfig.GUARD_EXPECTED_CERT (= e3e13a49) the
#                  registry scatters silently. So the release path REQUIRES the
#                  official jks at both layers.
#       debug   -> signing/guard-native-debug.keystore (ca421ec3). The debug
#                  module's embedded cert never matches GUARD_EXPECTED_CERT,
#                  so registry deliberately scatters: debug builds are only
#                  for module/notice/C2 smoke, not real release candidates.
#     Override with -LsKeystore / -LsAlias / -LsStorePass / -LsKeyPass.
#   - Default coexist host = mn_clean_origin_8071.apk. Default official host =
#     host_official_clean_8.0.71.apk. Both are user-provided clones (MT package
#     name swap for coexist, pulled clean APK for official); this script does
#     not download or repackage them.
#   - Does not touch git, does not install, does not call any server. Pure
#     local artifact chain. Output messages are English on purpose so the
#     script file stays ASCII and survives PowerShell 5.1 codepage quirks
#     (see tools/check_classmap.ps1 header for the same convention).
# =============================================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("official", "coexist")]
    [string]$Flavor,

    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",

    [switch]$Clean,
    [switch]$Build,

    # D-030 / F-43: re-sign the host APK with the release keystore BEFORE LSPatch,
    # so LSPatch -l 2 sigbypass reports the release cert (e3e13a49) at runtime, not
    # the clone/original host sig (a40da80a / 0fe4ff85 / ca421ec3). Without it the
    # output file sig is right but runtime certBind reads the host original -> A2
    # scatter. Release auto-rebinds regardless; this switch forces it for debug too.
    [switch]$RebindHost,

    [string]$HostApk = $null,
    [string]$Output  = $null,

    # LSPatch outer signing overrides. Default: debug -> guardFixed debug key;
    # release -> read signing/keystore.properties (guardOfficial*).
    [string]$LsKeystore  = $null,
    [string]$LsAlias     = $null,
    [string]$LsStorePass = $null,
    [string]$LsKeyPass   = $null
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# Default paths. The tool dir on disk is literally "02_tools_工具" (CJK),
# but we build the string char-by-char from Unicode code points so this .ps1
# file stays pure ASCII -- PS 5.1 reads .ps1 as cp936 by default and would
# mangle in-file CJK literals, breaking Test-Path. ToolsDir can be overridden
# via -ToolsDir if the layout ever changes.
$cjkGong = [char]0x5DE5  # 工
$cjkJu   = [char]0x5177  # 具
$toolsDir = "02_tools_" + $cjkGong + $cjkJu
$defaultHosts = @{
    "official" = (Join-Path $toolsDir "host_official_clean_8.0.71.apk")
    "coexist"  = (Join-Path $toolsDir "mn_clean_origin_8071.apk")
}
$lspatchJar = Join-Path $toolsDir "lspatch.jar"

if ([string]::IsNullOrEmpty($HostApk)) { $HostApk = $defaultHosts[$Flavor] }
if ([string]::IsNullOrEmpty($Output))  { $Output  = Join-Path $toolsDir "lspatch_out" }

# --- LSPatch outer signing material resolution -------------------------------
# Priority: explicit -Ls* params > release reads keystore.properties > debug
# falls back to the fixed guardFixed debug keystore.
function Read-KeystoreProps {
    param([string]$Path = "signing/keystore.properties")
    $props = @{}
    if (-not (Test-Path $Path)) { return $props }
    foreach ($line in Get-Content $Path) {
        $trim = $line.Trim()
        if ($trim -eq "" -or $trim.StartsWith("#")) { continue }
        $eq = $trim.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $trim.Substring(0, $eq).Trim()
        $v = $trim.Substring($eq + 1).Trim()
        $props[$k] = $v
    }
    return $props
}

# Resolve the Android SDK dir (local.properties sdk.dir > env > default), then the
# newest build-tools apksigner. Used by -RebindHost / release auto-rebind to re-sign
# the host APK so LSPatch sigbypass reports the release cert at runtime (F-43/D-030).
function Get-SdkDir {
    $lp = Join-Path $repoRoot "local.properties"
    if (Test-Path $lp) {
        $line = (Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1)
        if ($line) { return (($line -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\(.)', '$1').Trim() }
    }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME)     { return $env:ANDROID_HOME }
    return (Join-Path $env:LOCALAPPDATA "Android\Sdk")
}

function Find-Apksigner {
    $cmd = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $sdk = Get-SdkDir
    $bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
          Sort-Object Name -Descending | Select-Object -First 1
    if ($bt) {
        foreach ($n in @("apksigner.bat", "apksigner")) {
            $a = Join-Path $bt.FullName $n
            if (Test-Path $a) { return $a }
        }
    }
    return $null
}

$keystore = $LsKeystore
$keyAlias = $LsAlias
$storePass = $LsStorePass
$keyPass  = $LsKeyPass

if ([string]::IsNullOrEmpty($keystore)) {
    if ($BuildType -eq "release") {
        $props = Read-KeystoreProps
        # Cert-converge v2: both flavors use guardOfficial* (official jks).
        $keystore  = $props["guardOfficialStoreFile"]
        if ([string]::IsNullOrEmpty($keyAlias))  { $keyAlias  = $props["guardOfficialKeyAlias"] }
        if ([string]::IsNullOrEmpty($storePass)) { $storePass = $props["guardOfficialStorePassword"] }
        if ([string]::IsNullOrEmpty($keyPass))   { $keyPass   = $props["guardOfficialKeyPassword"] }
        if ([string]::IsNullOrEmpty($keystore)) {
            Write-Host "[lspatch_pack] FAIL: release mode needs signing/keystore.properties with guardOfficial* keys" -ForegroundColor Red
            Write-Host "  -- override with -LsKeystore/-LsAlias/-LsStorePass/-LsKeyPass if the file is elsewhere" -ForegroundColor Yellow
            exit 1
        }
    } else {
        # debug smoke: use fixed guardFixed debug keystore. Embedded module
        # cert will be ca421ec3 != GUARD_EXPECTED_CERT -> registry scatter
        # is expected (this is module/notice/C2 smoke only, not a release
        # candidate).
        $keystore  = "signing/guard-native-debug.keystore"
        if ([string]::IsNullOrEmpty($keyAlias))  { $keyAlias  = "androiddebugkey" }
        if ([string]::IsNullOrEmpty($storePass)) { $storePass = "android" }
        if ([string]::IsNullOrEmpty($keyPass))   { $keyPass   = "android" }
    }
}

if ([string]::IsNullOrEmpty($keyAlias))  { $keyAlias  = "androiddebugkey" }
if ([string]::IsNullOrEmpty($storePass)) { $storePass = "android" }
if ([string]::IsNullOrEmpty($keyPass))   { $keyPass   = "android" }

# Module APK path (AGP standard: build/outputs/apk/<flavor>/<buildType>/).
$moduleApk = "build/outputs/apk/$Flavor/$BuildType/guard-native-$Flavor-$BuildType.apk"

$ti = (Get-Culture).TextInfo
$gradleTask = ":assemble" + $ti.ToTitleCase($Flavor) + $ti.ToTitleCase($BuildType)

# --- Summary -----------------------------------------------------------------
Write-Host ""
Write-Host "[lspatch_pack] $Flavor / $BuildType"
Write-Host "  host    : $HostApk"
Write-Host "  module  : $moduleApk"
Write-Host "  out     : $Output"
Write-Host "  keystore: $keystore (alias=$keyAlias)"
if ($Clean) { Write-Host "  clean   : yes" }
if ($Build) { Write-Host "  build   : yes ($gradleTask)" }
if ($BuildType -eq "debug") {
    Write-Host "  NOTE    : debug build -- module cert won't match" -ForegroundColor Yellow
    Write-Host "            BuildConfig.GUARD_EXPECTED_CERT (registry will" -ForegroundColor Yellow
    Write-Host "            scatter by design; smoke / module update only)." -ForegroundColor Yellow
}
Write-Host ""

# --- Optional clean + build --------------------------------------------------
if ($Clean) {
    Write-Host "[lspatch_pack] gradlew clean ..."
    & .\gradlew clean
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[lspatch_pack] FAIL: gradlew clean exit=$LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
}

if ($Build) {
    Write-Host "[lspatch_pack] gradlew $gradleTask ..."
    & .\gradlew $gradleTask
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[lspatch_pack] FAIL: gradlew $gradleTask exit=$LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
}

# --- Input check (after gradle so a missing module surfaces last) ------------
$missing = @()
foreach ($f in @($HostApk, $moduleApk, $lspatchJar, $keystore)) {
    if (-not (Test-Path $f)) { $missing += $f }
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "[lspatch_pack] FAIL: missing inputs" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "  - module missing -> add -Build, or run gradlew $gradleTask first" -ForegroundColor Yellow
    Write-Host "  - host missing   -> pass -HostApk path/to.apk, or check 02_tools_xxx/" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $Output)) {
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
}

# --- D-030 / F-43: rebind host so LSPatch sigbypass reports the release cert --
# LSPatch -l 2 returns the HOST's ORIGINAL signature at runtime (not LSPatch -k).
# A clone/original host (a40da80a / 0fe4ff85 / ca421ec3) => runtime certBind !=
# e3e13a49 => A2 scatter, even though the output file sig is correct. Re-sign the
# host with the SAME release keystore first so its embedded origin.apk = e3e13a49.
# Release auto-rebinds (foolproof); -RebindHost forces it for debug too.
if ($RebindHost -or $BuildType -eq "release") {
    # NOTE: local var must NOT be named $rebindHost — PowerShell vars are
    # case-insensitive so it would alias the $RebindHost switch param (string ->
    # SwitchParameter cast error). Use $resignedHost.
    $apksignerExe = Find-Apksigner
    if (-not $apksignerExe) {
        Write-Host "[lspatch_pack] FAIL: -RebindHost/release needs apksigner (Android SDK build-tools) on PATH or under SDK" -ForegroundColor Red
        exit 6
    }
    $hostLeaf = Split-Path $HostApk -Leaf
    $resignedHost = Join-Path $Output ($hostLeaf -replace '\.apk$', '_rebind.apk')
    Write-Host "[lspatch_pack] rebind: re-signing host with $keystore (alias=$keyAlias) -> $resignedHost"
    $signArgs = @("sign", "--ks", $keystore, "--ks-key-alias", $keyAlias, "--ks-pass", "pass:$storePass", "--key-pass", "pass:$keyPass", "--out", $resignedHost, $HostApk)
    & $apksignerExe @signArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[lspatch_pack] FAIL: host rebind sign failed (exit=$LASTEXITCODE)" -ForegroundColor Red
        exit 6
    }
    $rsha = (& $apksignerExe verify --print-certs $resignedHost 2>&1 |
             Select-String "Signer #1 certificate SHA-256" | Select-Object -First 1)
    Write-Host "[lspatch_pack] rebind: $rsha" -ForegroundColor Green
    $HostApk = $resignedHost
}

# --- Run LSPatch -------------------------------------------------------------
# LSPatch -k arg order: <keystore> <storePass> <alias> <keyPass>.
Write-Host "[lspatch_pack] LSPatch packing ..."
$lsArgs = @(
    "-jar", $lspatchJar, $HostApk,
    "-m", $moduleApk,
    "-l", "2",
    "-k", $keystore, $storePass, $keyAlias, $keyPass,
    "-o", $Output,
    "-f"
)
& java @lsArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "[lspatch_pack] FAIL: LSPatch exit=$LASTEXITCODE" -ForegroundColor Red
    exit 1
}

# --- Locate freshest artifact ------------------------------------------------
$out = Get-ChildItem $Output -Filter "*-lspatched.apk" -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $out) {
    Write-Host "[lspatch_pack] FAIL: no *-lspatched.apk produced" -ForegroundColor Red
    exit 1
}

# --- Done report + next-step hint --------------------------------------------
$pkg = if ($Flavor -eq "official") { "com.tencent.mm" } else { "com.tencent.mn" }
$sizeMB = [math]::Round($out.Length / 1MB, 1)

Write-Host ""
Write-Host "[lspatch_pack] PASS" -ForegroundColor Green
Write-Host "  artifact : $($out.FullName)"
Write-Host "  size     : $sizeMB MB"
Write-Host "  mtime    : $($out.LastWriteTime)"
Write-Host ""
Write-Host "Next (RELEASE_RULES coexist 4-step quick path):"
Write-Host "  adb uninstall $pkg"
Write-Host "  adb reboot                  # wait for sys.boot_completed=1"
Write-Host "  adb install `"$($out.FullName)`""
Write-Host "  # then launch via the phone's home screen icon"
Write-Host "  # (do NOT use am start / monkey -- crashes LSPatch metaloader)"
Write-Host ""
exit 0
