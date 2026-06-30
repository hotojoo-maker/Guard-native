# sync_skills.ps1 - mirror .cursor/skills -> .claude/skills + .agents/skills
#
# Usage:
#   powershell -File sync_skills.ps1                    # one-shot
#   powershell -File sync_skills.ps1 -Watch             # watch mode
#   powershell -File sync_skills.ps1 -Direction reverse # claude -> cursor
#
# Design:
#   Primary  : .cursor/skills/  (edit here daily)
#   Mirror 1 : .claude/skills/  (Claude Code compat)
#   Mirror 2 : .agents/skills/  (Devin native skill dir)
#   Strategy : robocopy /MIR, idempotent (handles Chinese folder names via
#              wide-char API; do NOT use Copy-Item — it mangles CJK names)
#   Secrets  : /XF DEV_SECRETS.md — never mirror dev secrets into .agents/.claude
#              (.agents is git-tracked); the GitHub token stays one copy under .cursor

param(
    [switch]$Watch,
    [ValidateSet('forward','reverse')]
    [string]$Direction = 'forward'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition

$cursorSkills = Join-Path $root '.cursor\skills'
$claudeSkills = Join-Path $root '.claude\skills'
$agentsSkills = Join-Path $root '.agents\skills'

function Sync-Once {
    param([string]$Src, [string]$Dst)

    if (-not (Test-Path $Src)) {
        Write-Warning "Source not found: $Src"
        return
    }

    New-Item -ItemType Directory -Force -Path $Dst | Out-Null
    Write-Host ("[{0}] {1} -> {2}" -f (Get-Date -Format HH:mm:ss), $Src, $Dst) -ForegroundColor Cyan

    robocopy $Src $Dst /MIR /XF DEV_SECRETS.md /NP /NS /NJH /NJS /NC /NDL | Out-Null

    $count = (Get-ChildItem -Path $Dst -Recurse -Filter 'SKILL.md').Count
    Write-Host ("  -> synced {0} SKILL.md" -f $count) -ForegroundColor Green
}

if ($Direction -eq 'forward') {
    $src = $cursorSkills
    $dsts = @($claudeSkills, $agentsSkills)   # cursor -> claude + agents
} else {
    $src = $claudeSkills
    $dsts = @($cursorSkills)                   # claude -> cursor only
    Write-Warning "Reverse mode: claude -> cursor, starting in 5s..."
    Start-Sleep -Seconds 5
}

foreach ($d in $dsts) { Sync-Once -Src $src -Dst $d }

if ($Watch) {
    Write-Host "Watch mode active, Ctrl+C to exit" -ForegroundColor Yellow
    $watcher = New-Object System.IO.FileSystemWatcher
    $watcher.Path = $src
    $watcher.IncludeSubdirectories = $true
    $watcher.EnableRaisingEvents = $true

    $action = { foreach ($d in $dsts) { Sync-Once -Src $src -Dst $d } }.GetNewClosure()
    Register-ObjectEvent $watcher 'Changed' -Action $action | Out-Null
    Register-ObjectEvent $watcher 'Created' -Action $action | Out-Null
    Register-ObjectEvent $watcher 'Deleted' -Action $action | Out-Null
    Register-ObjectEvent $watcher 'Renamed' -Action $action | Out-Null

    while ($true) { Start-Sleep -Seconds 1 }
}
