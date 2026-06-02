<#
  guard_status.ps1 — Guard Native desktop status panel

  What it does:
    1. finds adb, runs `adb forward tcp:<port> tcp:<port>`
    2. polls the in-app DebugServer JSON endpoints
    3. prints a clean auto-refreshing dashboard in the console

  Notes:
    - Labels are ASCII on purpose: a UTF-8 (no BOM) .ps1 gets Chinese
      garbled under Windows PowerShell 5.1. Ask for a BOM/zh version if wanted.
    - DEV builds only. In a release build the DebugServer is OFF by design,
      so "module unreachable" here is the CORRECT, expected result for release.

  Usage:
    powershell -ExecutionPolicy Bypass -File guard_status.ps1
    (or double-click guard_status.cmd)
    -Port 8080      DebugServer port (default 8080, matches AppConfig)
    -RefreshSec 2   refresh interval
    -Once           print one snapshot and exit
#>

param(
    [int]$Port = 8080,
    [int]$RefreshSec = 2,
    [switch]$Once
)

$ErrorActionPreference = 'Stop'
$base = "http://127.0.0.1:$Port"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe",
        "C:\Android\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    return $null
}

$adb = Find-Adb
if (-not $adb) {
    Write-Host "adb not found. Add platform-tools to PATH or install adb." -ForegroundColor Red
    Read-Host "Press Enter to exit" | Out-Null
    exit 1
}

# Set up port forward once (harmless if repeated).
& $adb forward "tcp:$Port" "tcp:$Port" 2>$null | Out-Null

function Get-Json($path) {
    try { return Invoke-RestMethod -Uri "$base$path" -TimeoutSec 3 }
    catch { return $null }
}

function Show-Snapshot {
    Clear-Host
    $now = Get-Date -Format 'HH:mm:ss'
    Write-Host "  Guard Native  -  status panel   (port $Port)   $now" -ForegroundColor Cyan
    Write-Host ("-" * 56) -ForegroundColor DarkGray

    $devLines = (& $adb devices) 2>$null
    $hasDev = $false
    foreach ($l in $devLines) { if ($l -match "device$" -and $l -notmatch "List of") { $hasDev = $true } }
    if (-not $hasDev) {
        Write-Host "  [device]  not connected - plug phone / enable USB debugging" -ForegroundColor Red
        Write-Host ("-" * 56) -ForegroundColor DarkGray
        return
    }

    $state = Get-Json "/api/state"
    if (-not $state) {
        Write-Host "  [module]  unreachable on :$Port" -ForegroundColor Yellow
        Write-Host "            WeChat not open / module off / release build (server OFF = expected)" -ForegroundColor DarkGray
        Write-Host ("-" * 56) -ForegroundColor DarkGray
        return
    }

    $cfg     = Get-Json "/api/config"
    $feature = Get-Json "/api/feature"
    $hidden  = Get-Json "/api/hidden"

    $stName  = "$($state.state)"
    $stColor = if ($stName -eq 'HIDDEN') { 'Red' } elseif ($stName -eq 'VISIBLE') { 'Green' } else { 'Yellow' }
    Write-Host "  State        : " -NoNewline
    Write-Host $stName -ForegroundColor $stColor -NoNewline
    Write-Host "   (filter active: $($state.mode))"

    if ($cfg) {
        $modeColor = if ($cfg.mode -eq 'PROD') { 'Green' } else { 'Yellow' }
        Write-Host "  Run mode     : " -NoNewline
        Write-Host "$($cfg.mode)" -ForegroundColor $modeColor -NoNewline
        Write-Host "   localDev: $($cfg.localDev)   overlay: $($cfg.overlay)"
    }
    if ($feature) {
        $fOn = [bool]$feature.enabled
        Write-Host "  Privacy sw   : " -NoNewline
        Write-Host $(if ($fOn) { 'ON' } else { 'OFF' }) -ForegroundColor $(if ($fOn) { 'Green' } else { 'DarkGray' })
    }
    if ($hidden) {
        Write-Host "  Hidden       : $($hidden.count) total  (friends $($hidden.wxids) / groups $($hidden.groups))"
    }

    $nat = Get-Json "/api/native"
    if ($nat) {
        Write-Host ("-" * 56) -ForegroundColor DarkGray
        Write-Host "  Protection (SO):" -ForegroundColor DarkCyan
        Write-Host "    SO loaded   : " -NoNewline
        Write-Host $(if ($nat.soLoaded) { 'yes' } else { 'no' }) -ForegroundColor $(if ($nat.soLoaded) { 'Green' } else { 'Red' })
        Write-Host "    Role        : $($nat.role)"
        $aColor = if ($nat.authState -eq 'OK') { 'Green' } elseif ($nat.authState -eq 'TAMPERED' -or $nat.authState -eq 'EXPIRED') { 'Red' } else { 'Yellow' }
        Write-Host "    Auth        : " -NoNewline; Write-Host "$($nat.authState)" -ForegroundColor $aColor
        $rColor = if ($nat.risk -and $nat.risk -ne 'NONE') { 'Red' } else { 'Green' }
        Write-Host "    Risk        : " -NoNewline; Write-Host "$($nat.risk)" -ForegroundColor $rColor
        Write-Host "    Config ver  : $($nat.configVersion)"
        $lease = if ($null -eq $nat.leaseValid) { 'n/a (Phase1)' } elseif ($nat.leaseValid) { 'valid' } else { 'expired' }
        $dec   = if ($null -eq $nat.decryptOk)  { 'n/a (Phase1)' } elseif ($nat.decryptOk)  { 'OK' } else { 'FAIL' }
        Write-Host "    Lease       : $lease" -ForegroundColor DarkGray
        Write-Host "    Decrypt     : $dec" -ForegroundColor DarkGray
        Write-Host "    Tampered    : $($nat.tampered)" -ForegroundColor $(if ($nat.tampered) { 'Red' } else { 'DarkGray' })
    }

    $counters = Get-Json "/api/counters"
    if ($counters) {
        Write-Host ("-" * 56) -ForegroundColor DarkGray
        Write-Host "  Counters:" -ForegroundColor DarkCyan
        $counters.PSObject.Properties | Select-Object -First 10 | ForEach-Object {
            Write-Host ("    {0,-20}: {1}" -f $_.Name, $_.Value)
        }
    }
    Write-Host ("-" * 56) -ForegroundColor DarkGray
    Write-Host "  Ctrl+C to exit   refresh ${RefreshSec}s" -ForegroundColor DarkGray
}

if ($Once) { Show-Snapshot; exit 0 }

while ($true) {
    Show-Snapshot
    Start-Sleep -Seconds $RefreshSec
}
