# Tilt Local Development Guide

This guide covers local Kubernetes development using Tilt with Docker Desktop.

## Prerequisites

Install the following tools:

| Tool | Installation |
|------|--------------|
| Docker Desktop | https://docs.docker.com/get-docker/ (enable Kubernetes in settings) |
| kubectl | `winget install Kubernetes.kubectl` |
| tilt | `winget install Tilt.Tilt` or download to `C:\Users\<user>\bin` |
| Java 21+ | `winget install Microsoft.OpenJDK.21` |

Ensure Docker Desktop Kubernetes is enabled:
1. Open Docker Desktop → Settings → Kubernetes
2. Check "Enable Kubernetes"
3. Click "Apply & Restart"

## Quick Start

### First-Time Setup

```powershell
# Verify kubectl context is docker-desktop
kubectl config current-context
# Should output: docker-desktop

# Or use the bootstrap script (installs prereqs + starts Tilt)
.\bootstrap.ps1
```

### Starting Development

```powershell
# Start all services (Federation + Event-Driven CQRS)
tilt up

# Start only Federation stack
tilt up -- --federation-only

# Start only Event-Driven Projections stack
tilt up -- --event-only
```

### Stopping Development

```powershell
# Stop all services and delete namespaces
tilt down --delete-namespaces

# Or just stop (keeps data)
tilt down
```

## Access Points

| Service | URL | Description |
|---------|-----|-------------|
| Tilt Dashboard | http://localhost:10350 | Tilt UI for logs/status |
| Comparison Dashboard | http://localhost:3000 | Demo comparison UI |
| Apollo Router | http://localhost:4000 | GraphQL Federation endpoint |
| Query Service | http://localhost:8090 | Event-Driven query API |
| HR Subgraph | http://localhost:8091 | Federation subgraph |
| Employment Subgraph | http://localhost:8092 | Federation subgraph |
| Security Subgraph | http://localhost:8093 | Federation subgraph |
| Federation Postgres | localhost:5434 | Database |
| Event-Driven Postgres | localhost:5433 | Database |

## Development Workflow

### Making Code Changes

1. Edit Java source files in any service directory
2. Tilt detects changes and triggers Maven build
3. New JARs are synced to the container
4. Container restarts automatically (~5 seconds)

### Viewing Logs

- Open Tilt UI at http://localhost:10350
- Click on any resource to see its logs
- Use keyboard shortcuts:
  - `s` - Stream logs
  - `r` - Trigger rebuild
  - `x` - Clear logs

### Manual Rebuild

```powershell
# Trigger rebuild of specific service
tilt trigger hr-subgraph

# View all resources
tilt get
```

## Architecture Overview

```
                    Docker Desktop Kubernetes
    ┌─────────────────────────────────────────────────────┐
    │                                                     │
    │  FEDERATION STACK              EVENT-DRIVEN STACK   │
    │  (namespace: federation)       (namespace: kafka)     │
    │                                                     │
    │  ┌─────────────────┐          ┌─────────────────┐  │
    │  │  hr-subgraph    │          │ hr-events-svc   │  │
    │  │  employment-sub │          │ emp-events-svc  │  │
    │  │  security-sub   │          │ sec-events-svc  │  │
    │  └────────┬────────┘          └────────┬────────┘  │
    │           │                            │           │
    │           ▼                            ▼           │
    │  ┌─────────────────┐          ┌─────────────────┐  │
    │  │     router      │          │     kafka       │  │
    │  │   :4000         │          │ (apache/kafka)  │  │
    │  └─────────────────┘          └────────┬────────┘  │
    │           │                            │           │
    │           │                            ▼           │
    │           │                   ┌─────────────────┐  │
    │           │                   │ projection-     │  │
    │           │                   │ consumer        │  │
    │           │                   └────────┬────────┘  │
    │           │                            │           │
    │           │                            ▼           │
    │           │                   ┌─────────────────┐  │
    │           │                   │ projection-svc  │  │
    │           │                   │   :8090         │  │
    │           │                   └─────────────────┘  │
    │           │                            │           │
    │           └──────────┬─────────────────┘           │
    │                      ▼                             │
    │  ┌──────────────────────────────────────────────┐ │
    │  │            comparison-dashboard :3000         │ │
    │  └──────────────────────────────────────────────┘ │
    └─────────────────────────────────────────────────────┘
```

## Key Images

| Service | Image |
|---------|-------|
| Apollo Router | `ghcr.io/apollographql/router:v1.57.1` |
| Kafka | `apache/kafka:3.9.0` (KRaft mode, no Zookeeper) |
| Postgres | `postgres:15-alpine` |
| Subgraphs/Services | Built locally via Tilt |

## Running Tests

```powershell
# Run Playwright tests against running stack
cd tests
npx playwright test

# Run with headed browser
npx playwright test --headed
```

## Troubleshooting

### Build Fails with Maven Errors

```powershell
# Clean and rebuild manually
cd hr-subgraph
./mvnw.cmd clean package -DskipTests
```

### Container Keeps Restarting

1. Check logs in Tilt UI
2. Verify database/Kafka is healthy (green in Tilt)
3. Check environment variables in K8s manifest

### Port Already in Use

```powershell
# Find process using port (e.g., 10350 for Tilt UI)
netstat -ano | findstr :10350

# Kill process by PID
taskkill /PID <pid> /F
```

### Tilt UI Port Conflict

If you see "bind: Only one usage of each socket address", a previous Tilt instance is still running:

```powershell
# Find and kill existing Tilt process
taskkill /IM tilt.exe /F

# Then start fresh
tilt up
```

### Router Not Connecting to Subgraphs

Ensure all subgraphs are healthy before router starts. Check Tilt UI for dependency order.

### Dashboard Can't Reach Services

The dashboard uses nginx proxies to reach services across namespaces:
- Federation: `router.federation.svc.cluster.local:4000`
- Event-Driven: `query-service:8080` (same namespace)

### Clean Restart

```powershell
# Full clean restart
tilt down --delete-namespaces
tilt up
```

## Tips

1. **Keep Tilt UI open** - It provides real-time feedback on builds and deployments
2. **Use resource labels** - Filter by `federation` or `kafka` in Tilt UI
3. **Check dependencies** - If a service fails, check its dependencies first
4. **Maven cache** - First build is slow, subsequent builds use cached dependencies
5. **Watch startup order** - Postgres/Kafka must be ready before dependent services
