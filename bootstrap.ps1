#!/usr/bin/env pwsh
# Apollo Federation vs Event-Driven Projections Demo
# Zero-to-Tilt bootstrap script for Windows
# Usage: .\bootstrap.ps1

$ErrorActionPreference = "Stop"

#------------------------------------------------------------------------------
# Helpers
#------------------------------------------------------------------------------
function Write-Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "[->] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[!!] $msg" -ForegroundColor Yellow }
function Write-Fail($msg) { Write-Host "[XX] $msg" -ForegroundColor Red; exit 1 }

function Test-Command($cmd) {
    $null -ne (Get-Command $cmd -ErrorAction SilentlyContinue)
}

#------------------------------------------------------------------------------
# 1. Welcome and confirmation
#------------------------------------------------------------------------------
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Apollo Demo Bootstrap (Windows)" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will install (if missing):"
Write-Host "  - Docker Desktop - containers + Kubernetes"
Write-Host "  - kubectl        - Kubernetes CLI"
Write-Host "  - Tilt           - local dev orchestration"
Write-Host "  - OpenJDK 21     - Java runtime for builds"
Write-Host ""
Write-Host "Then it will:"
Write-Host "  - Enable Kubernetes in Docker Desktop"
Write-Host "  - Pre-build 8 Maven services"
Write-Host "  - Launch Tilt"
Write-Host ""
Write-Host "NOTE: Docker Desktop will ask for admin privileges on first run." -ForegroundColor Yellow
Write-Host ""
$null = Read-Host "Press Enter to continue (or Ctrl+C to cancel)"

#------------------------------------------------------------------------------
# 2. Install prerequisites via winget
#------------------------------------------------------------------------------
Write-Host ""
Write-Info "Checking and installing prerequisites..."
Write-Host ""

# Check if winget is available
if (-not (Test-Command "winget")) {
    Write-Fail "winget not found. Please install App Installer from Microsoft Store."
}

# Docker Desktop
if (Test-Command "docker") {
    Write-Ok "Docker Desktop already installed"
} else {
    Write-Info "Installing Docker Desktop..."
    winget install --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    Write-Ok "Docker Desktop installed"

    # Verify docker command is now available
    if (-not (Test-Command "docker")) {
        Write-Warn "Docker command not yet available."
        Write-Warn "You may need to restart your terminal or log out and back in."
        Write-Fail "Please restart your terminal and run this script again."
    }
}

# kubectl
if (Test-Command "kubectl") {
    Write-Ok "kubectl already installed"
} else {
    Write-Info "Installing kubectl..."
    winget install --id Kubernetes.kubectl --accept-source-agreements --accept-package-agreements
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    Write-Ok "kubectl installed"
}

# Tilt
if (Test-Command "tilt") {
    Write-Ok "Tilt already installed"
} else {
    Write-Info "Installing Tilt..."
    winget install --id Tilt.Tilt --accept-source-agreements --accept-package-agreements
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    Write-Ok "Tilt installed"
}

# Java 21 - need version 21+, not just any Java
function Test-JavaVersion {
    try {
        $javaOutput = java -version 2>&1 | Select-Object -First 1
        if ($javaOutput -match 'version "(\d+)') {
            $version = [int]$Matches[1]
            return $version -ge 21
        }
    } catch {}
    return $false
}

if (Test-JavaVersion) {
    Write-Ok "Java 21+ already installed"
} else {
    Write-Info "Installing OpenJDK 21..."
    winget install --id Microsoft.OpenJDK.21 --accept-source-agreements --accept-package-agreements
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    Write-Ok "OpenJDK 21 installed"
}

# Verify Java 21+ works after install
if (-not (Test-JavaVersion)) {
    Write-Fail "Java 21+ not working after install. Please restart your terminal."
}

#------------------------------------------------------------------------------
# 3. Start Docker Desktop if not running
#------------------------------------------------------------------------------
Write-Host ""
$dockerRunning = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) { $dockerRunning = $true }
} catch {}

if ($dockerRunning) {
    Write-Ok "Docker Desktop already running"
} else {
    Write-Info "Starting Docker Desktop..."
    # Find and start Docker Desktop executable
    $dockerExe = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerExe) {
        Start-Process $dockerExe -ErrorAction SilentlyContinue
    } else {
        # Fallback: try shell execution
        Start-Process "shell:AppsFolder\Docker.DockerDesktop" -ErrorAction SilentlyContinue
    }

    Write-Host "   Waiting for Docker to start" -NoNewline
    $timeout = 120
    while ($timeout -gt 0) {
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
        $timeout -= 2
        try {
            $null = docker info 2>&1
            if ($LASTEXITCODE -eq 0) { break }
        } catch {}
    }
    Write-Host ""

    if ($timeout -le 0) {
        Write-Fail "Docker failed to start within 120 seconds"
    }
    Write-Ok "Docker Desktop is running"
}

#------------------------------------------------------------------------------
# 4. Enable Kubernetes in Docker Desktop
#------------------------------------------------------------------------------
$dockerSettings = "$env:APPDATA\Docker\settings.json"

# Wait for settings file to exist
if (-not (Test-Path $dockerSettings)) {
    Write-Info "Waiting for Docker Desktop to initialize settings..."
    $waitCount = 0
    while (-not (Test-Path $dockerSettings) -and $waitCount -lt 60) {
        Start-Sleep -Seconds 2
        $waitCount += 2
    }
    if (-not (Test-Path $dockerSettings)) {
        Write-Fail "Docker settings not found after 60 seconds"
    }
}

# Check if Kubernetes is enabled
$settings = Get-Content $dockerSettings -Raw | ConvertFrom-Json
if ($settings.kubernetesEnabled -eq $true) {
    Write-Ok "Kubernetes already enabled in Docker Desktop"
} else {
    Write-Info "Enabling Kubernetes in Docker Desktop..."

    # Update settings
    $settings.kubernetesEnabled = $true
    $settings | ConvertTo-Json -Depth 10 | Set-Content $dockerSettings

    # Restart Docker Desktop
    Write-Info "Restarting Docker Desktop to apply Kubernetes setting..."
    # Stop all Docker processes
    Get-Process | Where-Object { $_.ProcessName -like "*docker*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    # Start Docker Desktop
    $dockerExe = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerExe) {
        Start-Process $dockerExe -ErrorAction SilentlyContinue
    } else {
        Start-Process "shell:AppsFolder\Docker.DockerDesktop" -ErrorAction SilentlyContinue
    }

    Write-Host "   Waiting for Docker to restart" -NoNewline
    $timeout = 120
    while ($timeout -gt 0) {
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
        $timeout -= 2
        try {
            $null = docker info 2>&1
            if ($LASTEXITCODE -eq 0) { break }
        } catch {}
    }
    Write-Host ""

    if ($timeout -le 0) {
        Write-Fail "Docker failed to restart within 120 seconds"
    }
    Write-Ok "Docker Desktop restarted with Kubernetes enabled"
}

#------------------------------------------------------------------------------
# 5. Wait for Kubernetes to be ready
#------------------------------------------------------------------------------
# Wait for docker-desktop context
$contextExists = kubectl config get-contexts docker-desktop 2>$null
if (-not $contextExists) {
    Write-Host "   Waiting for Kubernetes to initialize" -NoNewline
    $timeout = 180
    while ($timeout -gt 0) {
        Start-Sleep -Seconds 3
        Write-Host "." -NoNewline
        $timeout -= 3
        $contextExists = kubectl config get-contexts docker-desktop 2>$null
        if ($contextExists) { break }
    }
    Write-Host ""

    if ($timeout -le 0) {
        Write-Fail "Kubernetes context not available after 180 seconds"
    }
}
Write-Ok "Kubernetes context available"

# Switch context
kubectl config use-context docker-desktop 2>$null | Out-Null

# Wait for nodes to be ready
$nodesReady = kubectl wait --for=condition=Ready nodes --all --timeout=5s 2>$null
if (-not $nodesReady) {
    Write-Host "   Waiting for nodes" -NoNewline
    $timeout = 180
    while ($timeout -gt 0) {
        Start-Sleep -Seconds 5
        Write-Host "." -NoNewline
        $timeout -= 5
        $nodesReady = kubectl wait --for=condition=Ready nodes --all --timeout=10s 2>$null
        if ($nodesReady) { break }
    }
    Write-Host ""

    if ($timeout -le 0) {
        Write-Fail "Kubernetes nodes not ready after 180 seconds"
    }
}
Write-Ok "Kubernetes is ready"

#------------------------------------------------------------------------------
# 6. Pre-build Maven services
#------------------------------------------------------------------------------
Write-Host ""
Write-Info "Pre-building 8 Maven services..."
Write-Host ""

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }
$mvnDir = Join-Path $scriptDir "infra\maven"

$services = @(
    "services/federation/hr-subgraph",
    "services/federation/employment-subgraph",
    "services/federation/security-subgraph",
    "services/event/hr-events-service",
    "services/event/employment-events-service",
    "services/event/security-events-service",
    "services/event/projection-consumer",
    "services/event/query-service"
)

$failed = @()
foreach ($service in $services) {
    $serviceName = Split-Path $service -Leaf
    $servicePath = Join-Path $scriptDir $service

    if (Test-Path $servicePath) {
        Write-Host "  Building $serviceName..." -NoNewline
        Push-Location $mvnDir
        try {
            $pomPath = "../../$service/pom.xml"
            & .\mvnw.cmd -f $pomPath package -DskipTests -q 2>$null
            if ($LASTEXITCODE -ne 0) {
                Write-Host " FAILED" -ForegroundColor Red
                $failed += $service
            } else {
                Write-Host " OK" -ForegroundColor Green
            }
        } catch {
            Write-Host " ERROR" -ForegroundColor Red
            $failed += $service
        } finally {
            Pop-Location
        }
    }
}

if ($failed.Count -eq 0) {
    Write-Ok "All services built successfully"
} else {
    Write-Warn "Some builds failed, but continuing (Tilt will retry)"
}

#------------------------------------------------------------------------------
# 7. Launch Tilt
#------------------------------------------------------------------------------
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Starting Tilt" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Info "Access points once running:"
Write-Host "   - Dashboard:        http://localhost:3000"
Write-Host "   - Federation Router: http://localhost:4000"
Write-Host "   - Query Service:    http://localhost:8090"
Write-Host "   - Tilt UI:          http://localhost:10350"
Write-Host ""
Write-Info "Press Ctrl+C to stop"
Write-Host ""

Set-Location $scriptDir
& tilt up
