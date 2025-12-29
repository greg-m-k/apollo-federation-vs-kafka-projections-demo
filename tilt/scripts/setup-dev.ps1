#!/usr/bin/env pwsh
# setup-dev.ps1 - Setup script for Apollo-Demo (Windows)
#
# Usage:
#   .\tilt\scripts\setup-dev.ps1              # Full setup
#   .\tilt\scripts\setup-dev.ps1 -SkipBuild   # Skip Maven pre-build
#   .\tilt\scripts\setup-dev.ps1 -CheckOnly   # Just check prerequisites

param(
    [switch]$SkipBuild,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== Apollo-Demo Development Setup ===" -ForegroundColor Cyan
Write-Host ""

function Test-Command($cmd) {
    $null -ne (Get-Command $cmd -ErrorAction SilentlyContinue)
}

Write-Host "Checking prerequisites..." -ForegroundColor Yellow
Write-Host ""

$allGood = $true

# Docker
if (Test-Command "docker") {
    try {
        $dockerVersion = (docker --version 2>$null) -replace "Docker version ", "" -replace ",.*", ""
        Write-Host "  [OK] docker $dockerVersion" -ForegroundColor Green
        
        # Check Docker is running via 'docker info'
        $null = docker info 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  [OK] Docker daemon is running" -ForegroundColor Green
        } else {
            Write-Host "  [!!] Docker installed but daemon not running" -ForegroundColor Red
            Write-Host "       Start Docker Desktop first" -ForegroundColor Yellow
            $allGood = $false
        }
    } catch {
        Write-Host "  [!!] Docker error: $_" -ForegroundColor Red
        $allGood = $false
    }
} else {
    Write-Host "  [!!] docker - NOT INSTALLED" -ForegroundColor Red
    Write-Host "       Install: https://docs.docker.com/get-docker/" -ForegroundColor Yellow
    $allGood = $false
}

# kubectl
if (Test-Command "kubectl") {
    try {
        $kubectlVersion = kubectl version --client --short 2>$null
        if (-not $kubectlVersion) {
            $kubectlVersion = (kubectl version --client -o json 2>$null | ConvertFrom-Json).clientVersion.gitVersion
        }
        Write-Host "  [OK] kubectl $kubectlVersion" -ForegroundColor Green
    } catch {
        Write-Host "  [OK] kubectl (version check failed)" -ForegroundColor Green
    }
    
    # Check docker-desktop context exists
    $contexts = kubectl config get-contexts -o name 2>$null
    if ($contexts -match "docker-desktop") {
        $currentContext = kubectl config current-context 2>$null
        if ($currentContext -eq "docker-desktop") {
            Write-Host "  [OK] kubectl context: docker-desktop" -ForegroundColor Green
        } else {
            Write-Host "  [!!] kubectl context is '$currentContext', not docker-desktop" -ForegroundColor Yellow
            Write-Host "       Will switch to docker-desktop" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  [!!] docker-desktop context not found" -ForegroundColor Red
        Write-Host "       Enable Kubernetes in Docker Desktop Settings" -ForegroundColor Yellow
        $allGood = $false
    }
} else {
    Write-Host "  [!!] kubectl - NOT INSTALLED" -ForegroundColor Red
    Write-Host "       Install: winget install Kubernetes.kubectl" -ForegroundColor Yellow
    $allGood = $false
}

# tilt
if (Test-Command "tilt") {
    try {
        $tiltVersion = tilt version 2>$null
        Write-Host "  [OK] tilt $tiltVersion" -ForegroundColor Green
    } catch {
        Write-Host "  [OK] tilt (version check failed)" -ForegroundColor Green
    }
} else {
    Write-Host "  [!!] tilt - NOT INSTALLED" -ForegroundColor Red
    Write-Host "       Install: winget install Tilt.Tilt" -ForegroundColor Yellow
    $allGood = $false
}

# Java
if (Test-Command "java") {
    try {
        $javaOutput = java -version 2>&1 | Select-Object -First 1
        $javaVersion = $javaOutput -replace '.*version "', '' -replace '".*', ''
        Write-Host "  [OK] java $javaVersion" -ForegroundColor Green
    } catch {
        Write-Host "  [OK] java (version check failed)" -ForegroundColor Green
    }
} else {
    Write-Host "  [!!] java - NOT INSTALLED" -ForegroundColor Red
    Write-Host "       Install: winget install Microsoft.OpenJDK.17" -ForegroundColor Yellow
    $allGood = $false
}

Write-Host ""

if (-not $allGood) {
    Write-Host "Some prerequisites are missing. Install them and try again." -ForegroundColor Red
    Write-Host ""
    Write-Host "Quick install (Windows):" -ForegroundColor Cyan
    Write-Host "  winget install Kubernetes.kubectl"
    Write-Host "  winget install Tilt.Tilt"
    Write-Host "  winget install Microsoft.OpenJDK.17"
    Write-Host ""
    exit 1
}

Write-Host "All prerequisites OK!" -ForegroundColor Green

if ($CheckOnly) {
    exit 0
}

Write-Host ""

# Switch to docker-desktop context
Write-Host "Setting kubectl context to docker-desktop..." -ForegroundColor Yellow
kubectl config use-context docker-desktop 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to set context. Is Docker Desktop Kubernetes enabled?" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Pre-build Maven projects
if (-not $SkipBuild) {
    Write-Host "Pre-building Maven projects..." -ForegroundColor Yellow
    Write-Host "(First run takes several minutes)" -ForegroundColor Gray
    Write-Host ""

    $services = @(
        "services/federation/hr-subgraph",
        "services/federation/employment-subgraph",
        "services/federation/security-subgraph",
        "services/kafka/hr-events-service",
        "services/kafka/employment-events-service",
        "services/kafka/security-events-service",
        "services/kafka/projection-consumer",
        "services/kafka/query-service"
    )

    $failed = @()
    foreach ($service in $services) {
        if (Test-Path $service) {
            Write-Host "  Building $service..." -ForegroundColor Gray -NoNewline
            Push-Location $service
            try {
                & ./mvnw.cmd package -DskipTests -q 2>$null
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

    if ($failed.Count -gt 0) {
        Write-Host ""
        Write-Host "Some builds failed: $($failed -join ', ')" -ForegroundColor Yellow
        Write-Host "Tilt will rebuild these automatically" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Setup Complete! ===" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  tilt up          # Start all services"
Write-Host "  make up          # Same thing"
Write-Host "  make help        # See all commands"
Write-Host ""
Write-Host "Access points:" -ForegroundColor Cyan
Write-Host "  Tilt UI:   http://localhost:10350"
Write-Host "  Dashboard: http://localhost:3000"
Write-Host "  Router:    http://localhost:4000"
Write-Host "  Kafka Query: http://localhost:8090"
Write-Host ""
