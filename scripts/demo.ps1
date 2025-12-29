# demo.ps1 - Automated demo script for Architecture Comparison
# ============================================================

$ErrorActionPreference = "Stop"

$FEDERATION_URL = "http://localhost:4000"
$KAFKA_URL = "http://localhost:8090"

Write-Host "=== ARCHITECTURE COMPARISON DEMO ===" -ForegroundColor Cyan
Write-Host ""

# Check services are running
Write-Host "Checking services..."
try {
    $null = Invoke-RestMethod -Uri "$FEDERATION_URL/health" -TimeoutSec 5
} catch {
    Write-Host "ERROR: Federation Router not responding at $FEDERATION_URL" -ForegroundColor Red
    Write-Host "Run 'make up' or 'tilt up' first to start services."
    exit 1
}

try {
    $null = Invoke-RestMethod -Uri "$KAFKA_URL/health" -TimeoutSec 5
} catch {
    Write-Host "ERROR: Query Service not responding at $KAFKA_URL" -ForegroundColor Red
    Write-Host "Run 'make up' or 'tilt up' first to start services."
    exit 1
}
Write-Host "All services are running!" -ForegroundColor Green
Write-Host ""

# Step 1: Create person via Federation
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "1. Creating person via Federation"
Write-Host "=========================================" -ForegroundColor Yellow
$body = @{
    query = 'mutation { createPerson(name: "Demo User", email: "demo@example.com") { id name email } }'
} | ConvertTo-Json

$result = Invoke-RestMethod -Uri "$FEDERATION_URL/graphql" -Method Post -ContentType "application/json" -Body $body
$result | ConvertTo-Json -Depth 10
Write-Host ""

# Step 2: Query composed view from Federation
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "2. Query composed view from FEDERATION"
Write-Host "   (calls HR + Employment + Security subgraphs)"
Write-Host "=========================================" -ForegroundColor Yellow

$body = @{
    query = '{ persons { id name email employee { title department } badge { badgeNumber accessLevel } } }'
} | ConvertTo-Json

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$fedResult = Invoke-RestMethod -Uri "$FEDERATION_URL/graphql" -Method Post -ContentType "application/json" -Body $body
$sw.Stop()
$fedLatency = $sw.ElapsedMilliseconds

$fedResult | ConvertTo-Json -Depth 10
Write-Host ""
Write-Host "Federation latency: ${fedLatency}ms (3 service calls)" -ForegroundColor Magenta
Write-Host ""

# Step 3: Query composed view from Kafka Projections
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "3. Query composed view from Kafka Projections"
Write-Host "   (single local query)"
Write-Host "=========================================" -ForegroundColor Yellow

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$kafkaResult = Invoke-RestMethod -Uri "$KAFKA_URL/api/persons" -Method Get
$sw.Stop()
$kafkaLatency = $sw.ElapsedMilliseconds

$kafkaResult | ConvertTo-Json -Depth 10
Write-Host ""
Write-Host "Kafka latency: ${kafkaLatency}ms (1 local query)" -ForegroundColor Magenta
Write-Host ""

# Step 4: Compare latencies
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "4. LATENCY COMPARISON"
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "Federation: ${fedLatency}ms (sync, 3 services)"
Write-Host "Kafka:      ${kafkaLatency}ms (local projection)"
if ($fedLatency -gt $kafkaLatency) {
    $diff = $fedLatency - $kafkaLatency
    Write-Host ""
    Write-Host "Kafka is ${diff}ms faster!" -ForegroundColor Green
}
Write-Host ""

# Step 5: Demonstrate failure scenario
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "5. FAILURE SCENARIO"
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "Killing Security service..."
kubectl scale deployment security-subgraph --replicas=0 -n federation-demo 2>$null
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "Querying Federation (Security is DOWN):" -ForegroundColor Red
$body = @{
    query = '{ persons { id name badge { badgeNumber } } }'
} | ConvertTo-Json
try {
    $fedFail = Invoke-RestMethod -Uri "$FEDERATION_URL/graphql" -Method Post -ContentType "application/json" -Body $body
    $fedFail | ConvertTo-Json -Depth 10
} catch {
    Write-Host "Federation query FAILED (as expected)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Querying Kafka (Security is DOWN but data is local):" -ForegroundColor Green
$kafkaStillWorks = Invoke-RestMethod -Uri "$KAFKA_URL/api/persons" -Method Get
$kafkaStillWorks | ConvertTo-Json -Depth 10

Write-Host ""
Write-Host "Restoring Security service..."
kubectl scale deployment security-subgraph --replicas=1 -n federation-demo 2>$null

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "DEMO COMPLETE"
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Key Takeaways:"
Write-Host "  - Federation: Real-time data, but coupled availability"
Write-Host "  - Kafka: Fast local queries, but eventually consistent"
Write-Host ""
Write-Host "Open the dashboard at http://localhost:3000 to explore more!"
