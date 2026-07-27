<#
.SYNOPSIS
  Stops all instances (and frontend, if running) started by start-local.ps1
#>
$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir   = Resolve-Path (Join-Path $ScriptDir "..")
$PidDir    = Join-Path $RootDir ".pids"

function Write-Log($msg) { Write-Host "[stop-local] $msg" }

if (-not (Test-Path $PidDir)) {
    Write-Log "No $PidDir directory found; nothing to stop."
    exit 0
}

$pidFiles = Get-ChildItem -Path $PidDir -Filter "*.pid" -ErrorAction SilentlyContinue
if (-not $pidFiles -or $pidFiles.Count -eq 0) {
    Write-Log "No pid files found; nothing to stop."
    exit 0
}

foreach ($pidFile in $pidFiles) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($pidFile.Name)
    $procId = Get-Content $pidFile.FullName -ErrorAction SilentlyContinue

    $proc = $null
    if ($procId) { $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue }

    if (-not $proc) {
        Write-Log "$name`: stale pid file (process not running), removing."
        Remove-Item $pidFile.FullName -Force -ErrorAction SilentlyContinue
        continue
    }

    Write-Log "$name`: stopping pid $procId (graceful)..."
    Stop-Process -Id $procId -ErrorAction SilentlyContinue

    $waited = 0
    while ((Get-Process -Id $procId -ErrorAction SilentlyContinue) -and $waited -lt 10) {
        Start-Sleep -Seconds 1
        $waited++
    }

    if (Get-Process -Id $procId -ErrorAction SilentlyContinue) {
        Write-Log "$name`: still running after 10s, force killing."
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }

    Remove-Item $pidFile.FullName -Force -ErrorAction SilentlyContinue
    Write-Log "$name`: stopped."
}

Write-Log "All done."
