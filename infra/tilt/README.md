# Tilt

Configuration for [Tilt](https://tilt.dev) local Kubernetes development.

## Files

### `kind-cluster.yaml`

Kind cluster configuration with port mappings for all services.

**Creates cluster:**
```bash
kind create cluster --config infra/tilt/kind-cluster.yaml
```

**Port mappings configured:**

| Service | Container Port | Host Port |
|---------|---------------|-----------|
| Apollo Router | 30400 | 4000 |
| Router Health | 30088 | 8088 |
| Query Service | 30090 | 8090 |
| Dashboard | 30300 | 3000 |
| HR Subgraph | 30091 | 8091 |
| Employment Subgraph | 30092 | 8092 |
| Security Subgraph | 30093 | 8093 |
| HR Events | 30084 | 8084 |
| Employment Events | 30085 | 8085 |
| Security Events | 30086 | 8086 |
| PostgreSQL (Fed) | 30434 | 5434 |
| PostgreSQL (Kafka) | 30433 | 5433 |

### Bootstrap Scripts (in repo root)

Use the root-level bootstrap scripts for zero-to-running setup:

```bash
./bootstrap.sh        # macOS - installs prereqs via Homebrew
.\bootstrap.ps1       # Windows - installs prereqs via winget
```

For Windows-only prerequisite checking without starting Tilt:
```powershell
.\infra\tilt\scripts\setup-dev.ps1 -CheckOnly
```

## How Tilt Works

The root `Tiltfile` orchestrates everything:

1. **Namespace creation** - Applies `infra/k8s/namespace.yaml`
2. **Maven builds** - Runs `mvnw package` for each service (parallel)
3. **Docker builds** - Uses `infra/docker/Dockerfile.quarkus-jvm`
4. **K8s deploys** - Applies manifests from `infra/k8s/`
5. **Live updates** - Syncs JAR changes without full rebuild

## Starting Development

```bash
# First time: full setup (from repo root)
./bootstrap.sh        # macOS
.\bootstrap.ps1       # Windows

# Or start Tilt directly (prereqs must be installed)
tilt up

# Open Tilt UI
# http://localhost:10350
```

## Selective Stacks

```bash
tilt up -- --federation-only   # Just Federation
tilt up -- --kafka-only        # Just Event-Driven Projections
```

## Troubleshooting

**Cluster not found:**
```bash
kind create cluster --config infra/tilt/kind-cluster.yaml
```

**Ports already in use:**
```bash
# Check what's using the port
lsof -i :4000  # Mac/Linux
netstat -ano | findstr :4000  # Windows

# Delete old cluster and recreate
kind delete cluster --name apollo-demo
kind create cluster --config infra/tilt/kind-cluster.yaml
```

**Slow builds:**
The bootstrap scripts pre-build Maven projects. If running `tilt up` directly, the first build takes longer.
