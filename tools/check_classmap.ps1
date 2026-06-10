<#
.SYNOPSIS
  check_classmap.ps1 - verify docs/classmap/v8071.yaml stays in sync with the
  obfuscated class/member literals hardcoded in the Java source.

.DESCRIPTION
  P24 tooling. Two-way check:
    1) FORWARD : every "ok" symbol in the yaml must still appear somewhere in src.
                 If a ref file is listed, that file must contain the literal too.
    2) REVERSE : scan src for short obfuscated literals (e.g. "kc5.v0", "z15.ef6")
                 that are NOT documented in the yaml -> warn (undocumented drift).

  Output is English-only on purpose so the script file stays ASCII and is immune
  to PowerShell 5.1 codepage issues. The Chinese descriptions live in the yaml,
  which is read explicitly as UTF-8.

  Exit code 0 = clean (no MISSING). Exit code 1 = at least one MISSING symbol.

.PARAMETER ClassmapPath
  Path to the yaml dictionary. Default: <repo>/docs/classmap/v8071.yaml

.PARAMETER SrcRoot
  Root of the Java source to scan. Default: <repo>/src/main/java/com/ghost/assist

.EXAMPLE
  pwsh tools/check_classmap.ps1
#>

[CmdletBinding()]
param(
    [string]$ClassmapPath,
    [string]$SrcRoot
)

$ErrorActionPreference = 'Stop'

# --- resolve default paths relative to this script (tools/ -> repo root) ------
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = Split-Path -Parent $scriptDir
if (-not $ClassmapPath) { $ClassmapPath = Join-Path $repoRoot 'docs/classmap/v8071.yaml' }
if (-not $SrcRoot)      { $SrcRoot      = Join-Path $repoRoot 'src/main/java/com/ghost/assist' }

if (-not (Test-Path $ClassmapPath)) { Write-Host "[FATAL] classmap not found: $ClassmapPath" -ForegroundColor Red; exit 2 }
if (-not (Test-Path $SrcRoot))      { Write-Host "[FATAL] src root not found: $SrcRoot"      -ForegroundColor Red; exit 2 }

Write-Host "=== check_classmap ===" -ForegroundColor Cyan
Write-Host "classmap : $ClassmapPath"
Write-Host "src root : $SrcRoot"
Write-Host ""

# --- minimal yaml parse (this file's fixed structure only) --------------------
# Each symbol block starts with a line "  - name: <value>".
$yamlText = Get-Content -Path $ClassmapPath -Raw -Encoding UTF8
$lines = $yamlText -split "`r?`n"

$symbols = @()
$cur = $null
foreach ($line in $lines) {
    if ($line -match '^\s*-\s*name:\s*"?([^"#]+?)"?\s*$') {
        if ($cur) { $symbols += $cur }
        $cur = [ordered]@{ name = $Matches[1].Trim(); kind = ''; status = 'ok'; refs = @() }
    }
    elseif ($cur -and $line -match '^\s*kind:\s*(\S+)') {
        $cur.kind = $Matches[1]
    }
    elseif ($cur -and $line -match '^\s*status:\s*(\S+)') {
        $cur.status = $Matches[1]
    }
    elseif ($cur -and $line -match '^\s*refs:\s*\[(.*)\]') {
        $cur.refs = ($Matches[1] -split ',') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    }
}
if ($cur) { $symbols += $cur }

Write-Host "parsed $($symbols.Count) symbols from dictionary" -ForegroundColor Gray
Write-Host ""

# --- load all java files once -------------------------------------------------
$javaFiles = Get-ChildItem -Path $SrcRoot -Recurse -Filter *.java -File
$fileCache = @{}
foreach ($f in $javaFiles) {
    $fileCache[$f.FullName] = Get-Content -Path $f.FullName -Raw -Encoding UTF8
}

# --- FORWARD check ------------------------------------------------------------
$missing = @()
$refWarn = @()
$okCount = 0
$dictNames = @{}

foreach ($s in $symbols) {
    $dictNames[$s.name] = $true
    $needle = $s.name
    # literal search (escape regex meta in the class name)
    $found = $false
    foreach ($kv in $fileCache.GetEnumerator()) {
        if ($kv.Value -match [regex]::Escape($needle)) { $found = $true; break }
    }

    if (-not $found) {
        if ($s.status -eq 'deprecated') {
            Write-Host "[INFO]    $needle  (deprecated, not found - ok)" -ForegroundColor DarkGray
        } else {
            Write-Host "[MISSING] $needle  ($($s.kind)) - in dict but NOT in src" -ForegroundColor Red
            $missing += $needle
        }
        continue
    }

    # ref-file presence check
    $badRefs = @()
    foreach ($r in $s.refs) {
        $full = Join-Path $SrcRoot $r
        if (-not (Test-Path $full)) { $badRefs += "$r (no file)"; continue }
        if ($fileCache[$full] -notmatch [regex]::Escape($needle)) { $badRefs += "$r (no literal)" }
    }
    if ($badRefs.Count -gt 0) {
        Write-Host "[WARN]    $needle - ref drift: $($badRefs -join '; ')" -ForegroundColor Yellow
        $refWarn += $needle
    } else {
        Write-Host "[OK]      $needle" -ForegroundColor Green
    }
    $okCount++
}

# --- REVERSE check : undocumented obfuscated literals -------------------------
Write-Host ""
Write-Host "--- reverse scan (undocumented obfuscated literals) ---" -ForegroundColor Cyan

# short obfuscated form inside double quotes, e.g. "kc5.v0", "z15.ef6", "pz0.h"
# member part must start with a letter -> excludes things like "8.0.71" / "v15.2"
$obfRegex = '"([a-z]{1,4}[0-9]{1,3}\.[a-zA-Z][a-zA-Z0-9_]*)"'
$undoc = @{}
foreach ($kv in $fileCache.GetEnumerator()) {
    $shortName = Split-Path $kv.Key -Leaf
    $mm = [regex]::Matches($kv.Value, $obfRegex)
    foreach ($m in $mm) {
        $tok = $m.Groups[1].Value
        if (-not $dictNames.ContainsKey($tok)) {
            if (-not $undoc.ContainsKey($tok)) { $undoc[$tok] = New-Object System.Collections.Generic.HashSet[string] }
            [void]$undoc[$tok].Add($shortName)
        }
    }
}

if ($undoc.Count -eq 0) {
    Write-Host "[OK]      no undocumented obfuscated literals" -ForegroundColor Green
} else {
    foreach ($k in ($undoc.Keys | Sort-Object)) {
        Write-Host "[UNDOC]   $k  <- $(($undoc[$k]) -join ', ')" -ForegroundColor Yellow
    }
}

# --- summary ------------------------------------------------------------------
Write-Host ""
Write-Host "=== summary ===" -ForegroundColor Cyan
Write-Host ("  ok        : {0}" -f $okCount)
Write-Host ("  missing   : {0}" -f $missing.Count)
Write-Host ("  ref-warn  : {0}" -f $refWarn.Count)
Write-Host ("  undoc     : {0}" -f $undoc.Count)
Write-Host ""

if ($missing.Count -gt 0) {
    Write-Host "RESULT: FAIL (missing symbols - dict drifted from code)" -ForegroundColor Red
    exit 1
}
Write-Host "RESULT: PASS" -ForegroundColor Green
exit 0
