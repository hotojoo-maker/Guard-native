#!/usr/bin/env pwsh
# Guard Native -- release APK string-leak gate.
# =============================================================================
# Scans the dex + native .so inside an APK for DEBUG / self-test / unit-test
# artifacts that must NEVER ship in a release package. Pure .NET (zip open +
# byte scan): no strings.exe / 7z / jadx / NDK needed, so it runs headless on a
# bare PowerShell 5.1 box and in CI. Recurses into LSPatch-repacked module APKs
# embedded under assets/ (our shipping form is a repacked host APK).
#
#   Exit 0 = PASS  (no FAIL hit; SUSPECT/KNOWN are reported but do not fail
#                   unless -StrictSuspect is given)
#   Exit 1 = FAIL  (a debug/test artifact leaked into the package)
#   Exit 2 = usage / file error
#
# WHY A GATE: native debug code is physically isolated by #ifdef
# GUARD_DEV_SELFTEST (CMakeLists Debug-only), but the Java/dex side is isolated
# only by `if (BuildConfig.DEBUG)` + R8 dead-code elimination -- a LOGICAL strip.
# If R8 ever fails to strip, the release dex ships the test tags. This scan is the
# net that catches that R8 miss. Wired as a gradle hard gate (build.gradle:
# checkStringLeak{Official,Coexist}Release finalizes assembleXxxRelease).
#
# THREE TIERS (a gate that false-positives a working release gets muted):
#   FAIL    -- exact literals proven ABSENT in a real release build. Any hit is a
#              real R8 / test-packaging regression. Breaks the build.
#   SUSPECT -- tokens matching a debug-ish naming pattern ($SuspectPattern) that
#              are NOT on the known-OK allowlist. This is the BACKSTOP for the
#              blacklist's blind spot: a newly-written debug tag nobody added to
#              FAIL still surfaces here instead of shipping silently. Highlighted
#              for review; does NOT break the build unless -StrictSuspect.
#   KNOWN   -- debug-ish names/tags that legitimately ship in this release by
#              design (allowlisted). Shown for transparency; never fails.
#
# Usage:
#   powershell -NoProfile -File tools\check_release_strings.ps1 -Apk <release.apk>
#   powershell -NoProfile -File tools\check_release_strings.ps1 -So  <libguardcore.so>
#   ... -StrictSuspect     # also fail the build on any SUSPECT hit
# =============================================================================

[CmdletBinding()]
param(
    [string]$Apk,
    [string]$So,
    [int]$MaxDepth = 2,
    [switch]$StrictSuspect
)

$ErrorActionPreference = "Stop"

# --- FAIL: dex -- exact DEBUG/test literals proven absent from a real release.
$DexFail = @(
    '[ANTIBAN-GATE]',          # ModuleMain.java:235 antiBanGateSelfTest, if(BuildConfig.DEBUG)
    '[ANTIBAN-BRANCH]',        # GuardRuntime.branchCheck via antiBanBranchSelfTest (DEBUG-only)
    'KDF_VECTOR_VERIFY',       # ModuleMain native KDF self-test log, BuildConfig.DEBUG gated
    'GuardRuntimeAntiBanTest'  # JUnit class from src/test/ -- must never be inside any APK
)

# --- FAIL: native .so -- JNI self-test export present ONLY in Debug SO builds
# (guard_core.cpp wraps the whole nativeKdfSelfTest JNI fn in #ifdef). Matches
# docs/RELEASE_RULES.md clause #8 `nm -D`; verified absent in release scan.
# Do NOT add registry_self_test / nativeRegistrySelfTest -- release control syms.
$SoFail = @(
    'nativeKdfSelfTest'
)

# --- KNOWN-OK tokens -- debug-ish identifier names that legitimately ship in a
# release. Anything matching $SuspectPattern but NOT here becomes SUSPECT.
# (To make any of these truly absent = lean-closeout follow-up: gate D2D3 diag
# behind BuildConfig.DEBUG / strip release SO. See RELEASE_RULES.md clause #9.)
$KnownOk = @(
    'kdfSelfTest',              # NativeBridge is -keep'd -> method name stays in dex (legit)
    'fallbackSelfTest',         # Filter install-log tag (ConvFilter/MomentsFilter) -- runs in release
    'nativeRegistrySelfTest',   # release control symbol (SUPPOSED to be present)
    'registrySelfTest',         # NativeBridge wrapper for the above
    'registry_self_test',       # SO control symbol (SUPPOSED to be present)
    'kdf_self_test',            # SO internal fn name kept by RelWithDebInfo (stub in release)
    'decrypt_config_self_test', # SO internal fn name kept by RelWithDebInfo (real release fn)
    'envelope_expiry_self_test' # SO internal fn name kept by RelWithDebInfo
)

# --- KNOWN-OK literal tags -- not identifier tokens; ship by design, reviewed.
$KnownTags = @(
    '[D2D3:'   # D2/D3 moments diag log: cleanD2D3 (live path, MomentsFilter:589) +
               #   dump block (432-485) are RUNTIME-gated (sDiagSeen), not BuildConfig.DEBUG,
               #   so ~17 ship in release by current design.
)

# --- SUSPECT pattern -- debug-ish full identifiers. A match NOT in $KnownOk is an
# unregistered debug artifact -> highlight. Anchored to identifier chars so dex
# binary noise won't match; targets the names debug/self-test code actually uses.
$SuspectPattern = '[A-Za-z_][A-Za-z0-9_]*(?:SelfTest|self_test|SelfCheck|Diag)[A-Za-z0-9_]*'

Add-Type -AssemblyName System.IO.Compression | Out-Null            # ZipArchive / ZipArchiveMode
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null # ZipFile (PS 5.1 needs both)
$Latin1 = [System.Text.Encoding]::GetEncoding(28591)   # 1 byte -> 1 char, lossless
$SuspectRe = [regex]::new($SuspectPattern)

$script:fails    = New-Object System.Collections.ArrayList
$script:suspects = New-Object System.Collections.ArrayList
$script:knowns   = New-Object System.Collections.ArrayList
$script:seen     = New-Object 'System.Collections.Generic.HashSet[string]'

function Get-HitCount([string]$hay, [string]$needle) {
    $n = 0; $i = 0
    while (($i = $hay.IndexOf($needle, $i, [System.StringComparison]::Ordinal)) -ge 0) {
        $n++; $i += $needle.Length
    }
    return $n
}

function Invoke-Scan([byte[]]$bytes, [string]$label, [string[]]$failList, [bool]$scanTags) {
    $hay = $Latin1.GetString($bytes)

    # tier 1: exact FAIL literals
    foreach ($w in $failList) {
        $c = Get-HitCount $hay $w
        if ($c -gt 0) { [void]$script:fails.Add([pscustomobject]@{ Where = $label; Term = $w; Count = $c }) }
    }

    # known-OK literal tags (dex only): record as KNOWN, never fail
    if ($scanTags) {
        foreach ($w in $KnownTags) {
            $c = Get-HitCount $hay $w
            if ($c -gt 0) { [void]$script:knowns.Add([pscustomobject]@{ Where = $label; Term = $w; Count = $c }) }
        }
    }

    # tier 2/3: pattern-extract debug-ish tokens -> KNOWN (allowlisted) or SUSPECT
    foreach ($m in $SuspectRe.Matches($hay)) {
        $tok = $m.Value
        if ($failList -contains $tok) { continue }   # already reported as FAIL
        $isKnown = $KnownOk -contains $tok
        $kp = if ($isKnown) { 'K:' } else { 'S:' }
        $key = $kp + $label + "`t" + $tok
        if (-not $script:seen.Add($key)) { continue } # dedup per (token, file)
        if ($isKnown) {
            [void]$script:knowns.Add([pscustomobject]@{ Where = $label; Term = $tok; Count = 0 })
        }
        else {
            [void]$script:suspects.Add([pscustomobject]@{ Where = $label; Term = $tok })
        }
    }
}

function Get-EntryBytes($entry) {
    $ms = New-Object System.IO.MemoryStream
    $s = $entry.Open()
    try { $s.CopyTo($ms) } finally { $s.Dispose() }
    return $ms.ToArray()
}

function Invoke-ScanZip([byte[]]$zipBytes, [string]$prefix, [int]$depth) {
    $msz = New-Object System.IO.MemoryStream(, $zipBytes)
    $zip = New-Object System.IO.Compression.ZipArchive($msz, [System.IO.Compression.ZipArchiveMode]::Read)
    try {
        foreach ($e in $zip.Entries) {
            $name = $e.FullName
            if ($name -match '\.dex$') {
                Invoke-Scan (Get-EntryBytes $e) "$prefix$name" $DexFail $true
            }
            elseif ($name -match '\.so$') {
                Invoke-Scan (Get-EntryBytes $e) "$prefix$name" $SoFail $false
            }
            elseif ($name -match '\.apk$' -and $depth -lt $MaxDepth) {
                # LSPatch repacks the module APK inside assets/ -- recurse into it.
                Invoke-ScanZip (Get-EntryBytes $e) "$prefix$name!/" ($depth + 1)
            }
        }
    }
    finally {
        $zip.Dispose(); $msz.Dispose()
    }
}

# --- entry point -----------------------------------------------------------
$target = $null
if ($So) {
    if (-not (Test-Path -LiteralPath $So)) { [Console]::Error.WriteLine("so not found: $So"); exit 2 }
    $target = $So
    Invoke-Scan ([System.IO.File]::ReadAllBytes($So)) (Split-Path $So -Leaf) $SoFail $false
}
elseif ($Apk) {
    if (-not (Test-Path -LiteralPath $Apk)) { [Console]::Error.WriteLine("apk not found: $Apk"); exit 2 }
    $target = $Apk
    Invoke-ScanZip ([System.IO.File]::ReadAllBytes($Apk)) ((Split-Path $Apk -Leaf) + "!/") 0
}
else {
    [Console]::Error.WriteLine("usage: -Apk <release.apk>  |  -So <libguardcore.so>  [-StrictSuspect]")
    exit 2
}

Write-Host ""
Write-Host "=== Guard release string-leak gate ==="
Write-Host "target: $target"

if ($script:knowns.Count -gt 0) {
    Write-Host ""
    Write-Host "[KNOWN] allowlisted, ships by design (review only):"
    foreach ($r in $script:knowns) {
        $cnt = if ($r.Count -gt 0) { " x$($r.Count)" } else { "" }
        Write-Host ("  KNOWN    {0,-26}{1}  in {2}" -f $r.Term, $cnt, $r.Where)
    }
}

if ($script:suspects.Count -gt 0) {
    Write-Host ""
    Write-Host "[SUSPECT] unregistered debug-ish token (review; add to FAIL if it's a leak, or to KnownOk if legit):"
    foreach ($r in $script:suspects) {
        Write-Host ("  SUSPECT  {0,-26}  in {1}" -f $r.Term, $r.Where)
    }
}

if ($script:fails.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] debug/test artifacts leaked into release package:"
    foreach ($r in $script:fails) {
        Write-Host ("  FAIL     {0,-26} x{1}  in {2}" -f $r.Term, $r.Count, $r.Where)
    }
}

$blocked = ($script:fails.Count -gt 0) -or ($StrictSuspect -and $script:suspects.Count -gt 0)
Write-Host ""
if ($blocked) {
    if ($StrictSuspect -and $script:fails.Count -eq 0) {
        Write-Host "RESULT: FAIL (strict: SUSPECT present)"
    } else {
        Write-Host "RESULT: FAIL"
    }
    exit 1
}
Write-Host "RESULT: PASS"
exit 0
