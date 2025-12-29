# PowerShell script to deploy the comparison demo
# Usage: .\deploy-comparison.ps1

param(
    [switch]$Build,
    [switch]$FederationOnly,
    [switch]$KafkaOnly
)

Write-Host "=== Architecture Comparison Demo Deployment ===" -ForegroundColor Cyan

# Build Java projects if requested
if ($Build) {
    Write-Host "`nBuilding Java projects..." -ForegroundColor Yellow

    $projects = @(
        "hr-subgraph",
        "employment-subgraph",
        "security-subgraph",
        "hr-events-service",
        "employment-events-service",
        "security-events-service",
        "projection-consumer",
        "query-service"
    )

    foreach ($project in $projects) {
        if (Test-Path "$project\mvnw.cmd") {
            Write-Host "Building $project..." -ForegroundColor Gray
            Push-Location $project
            .\mvnw.cmd clean package -DskipTests -q
            Pop-Location
        }
    }
}

# Apply namespaces
Write-Host "`nCreating namespaces..." -ForegroundColor Yellow
kubectl apply -f k8s\namespace.yaml

if (-not $KafkaOnly) {
    Write-Host "`nDeploying Federation architecture..." -ForegroundColor Yellow
    kubectl apply -f k8s\federation\
}

if (-not $FederationOnly) {
    Write-Host "`nDeploying Kafka architecture..." -ForegroundColor Yellow
    kubectl apply -f k8s\kafka\
}

Write-Host "`n=== Deployment Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Federation Router: http://localhost:30400" -ForegroundColor Cyan
Write-Host "Kafka Query Service: http://localhost:30090" -ForegroundColor Cyan
Write-Host ""
Write-Host "To check status: kubectl get pods -n federation-demo && kubectl get pods -n kafka-demo"
