<#
.SYNOPSIS
  Starts 6 identical instances of distributed-batch-orchestrator in local mode.
.PARAMETER Build
  Force a rebuild of the jar even if it already exists.
.PARAMETER WithFrontend
  Also install (if needed) and start the frontend on port 3000.
.EXAMPLE
  scripts\start-local.ps1 -Build -WithFrontend
#>
param(
    [switch]$Build,
    [switch]$WithFrontend
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir   = Resolve-Path (Join-Path $ScriptDir "..")
$JarPath   = Join-Path $RootDir "target/distributed-batch-orchestrator-1.0.0.jar"
$Ports     = @(8080, 8081, 8082, 8083, 8084, 8085)
$PidDir    = Join-Path $RootDir ".pids"
$LogDir    = Join-Path $RootDir "logs"
$DataDir   = Join-Path $RootDir "data"

function Write-Log($msg)  { Write-Host "[start-local] $msg" }
function Fail($msg)       { Write-Error "[start-local] ERROR: $msg"; exit 1 }

# --- check Java 21+ ---
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { Fail "java not found on PATH. Install Java 21+." }
$verOutput = (& java -version) 2>&1 | Select-Object -First 1
if ($verOutput -match '"(\d+)(\.\d+)?') {
    $javaMajor = [int]$Matches[1]
} else {
    Fail "Could not parse java version from: $verOutput"
}
if ($javaMajor -lt 21) { Fail "Java 21+ required, found major version '$javaMajor'." }
Write-Log "Java $javaMajor detected."

# --- build jar if needed ---
if ($Build -or -not (Test-Path $JarPath)) {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnCmd) { Fail "mvn not found on PATH." }
    Write-Log "Building jar (mvn -q package -DskipTests)..."
    Push-Location $RootDir
    try {
        & mvn -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { Fail "Maven build failed." }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $JarPath)) { Fail "Build finished but jar not found at $JarPath" }
} else {
    Write-Log "Jar already present at $JarPath (use -Build to force rebuild)."
}

# --- fresh state ---
Write-Log "Resetting .\data ..."
if (Test-Path $DataDir) { Remove-Item -Recurse -Force $DataDir }
New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
New-Item -ItemType Directory -Force -Path $LogDir  | Out-Null
New-Item -ItemType Directory -Force -Path $PidDir  | Out-Null

function Wait-Healthy([int]$Port, [int]$TimeoutSec) {
    $waited = 0
    while ($waited -lt $TimeoutSec) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 2
            if ($resp.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 1
        $waited++
    }
    return $false
}

function Start-Instance([int]$Port, [string]$BatchInit) {
    $logFile = Join-Path $LogDir "instance-$Port.log"
    $pidFile = Join-Path $PidDir "instance-$Port.pid"
    Write-Log "Starting instance-$Port (BATCH_INIT=$BatchInit) -> $logFile"

    $env:SERVER_PORT     = "$Port"
    $env:APP_INSTANCE_ID = "instance-$Port"
    $env:BATCH_INIT      = "$BatchInit"

    $proc = Start-Process -FilePath "java" -ArgumentList "-jar", "`"$JarPath`"" `
        -WorkingDirectory $RootDir `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$logFile.err" `
        -PassThru -WindowStyle Hidden

    Remove-Item Env:SERVER_PORT, Env:APP_INSTANCE_ID, Env:BATCH_INIT -ErrorAction SilentlyContinue

    Set-Content -Path $pidFile -Value $proc.Id
}

# instance 1 first (schema init owner), then wait for it healthy
Start-Instance -Port $Ports[0] -BatchInit "always"
Write-Log "Waiting for instance-$($Ports[0]) to become healthy (up to 60s)..."
if (-not (Wait-Healthy -Port $Ports[0] -TimeoutSec 60)) {
    Fail "instance-$($Ports[0]) did not become healthy in time. Check $LogDir\instance-$($Ports[0]).log"
}
Write-Log "instance-$($Ports[0]) healthy."

Start-Sleep -Seconds 3

# remaining instances can start together
foreach ($port in $Ports[1..($Ports.Length - 1)]) {
    Start-Instance -Port $port -BatchInit "never"
}

Write-Log "Waiting for all instances to become healthy (up to 90s)..."
$allHealthy = $true
foreach ($port in $Ports[1..($Ports.Length - 1)]) {
    if (-not (Wait-Healthy -Port $port -TimeoutSec 90)) {
        Write-Log "instance-$port did NOT become healthy. Check $LogDir\instance-$port.log"
        $allHealthy = $false
    }
}

if ($WithFrontend) {
    $FrontendDir = Join-Path $RootDir "frontend"
    if (Test-Path $FrontendDir) {
        $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
        if (-not $npmCmd) { Fail "npm not found on PATH." }
        if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
            Write-Log "Installing frontend dependencies..."
            Push-Location $FrontendDir
            try { & npm install } finally { Pop-Location }
        }
        Write-Log "Starting frontend (npm start) -> $LogDir\frontend.log"
        $feProc = Start-Process -FilePath "npm" -ArgumentList "start" `
            -WorkingDirectory $FrontendDir `
            -RedirectStandardOutput (Join-Path $LogDir "frontend.log") `
            -RedirectStandardError (Join-Path $LogDir "frontend.log.err") `
            -PassThru -WindowStyle Hidden
        Set-Content -Path (Join-Path $PidDir "frontend.pid") -Value $feProc.Id
    } else {
        Write-Log "WARNING: -WithFrontend requested but $FrontendDir not found; skipping."
    }
}

Write-Host ""
"{0,-14} {1,-8} {2,-38} {3,-8}" -f "INSTANCE", "PORT", "URL", "STATUS"
foreach ($port in $Ports) {
    $status = "DOWN"
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) { $status = "UP" }
    } catch { }
    "{0,-14} {1,-8} {2,-38} {3,-8}" -f "instance-$port", "$port", "http://localhost:$port", $status
}
if ($WithFrontend -and (Test-Path (Join-Path $RootDir "frontend"))) {
    "{0,-14} {1,-8} {2,-38} {3,-8}" -f "frontend", "3000", "http://localhost:3000", "STARTED"
}
Write-Host ""

if (-not $allHealthy) { Fail "One or more instances failed health check. See logs in $LogDir." }
Write-Log "All instances healthy."
