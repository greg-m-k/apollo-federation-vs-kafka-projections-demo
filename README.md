# Apollo Federation vs Event-Driven Projections Demo

> **Architecture is all about tradeoffs.** This demo lets you experience those tradeoffs firsthand.

A side-by-side demonstration of two distributed systems architectures:

1. **GraphQL Federation** — Synchronous composition, real-time data, coupled availability
2. **Event-Driven Projections** — Asynchronous events, local materialized views, eventual consistency

Both architectures model the same domain: **Person/Employee/Badge** across HR, Employment, and Security bounded contexts.

### ⚠️ What This Is (and Isn't)

This is a **learning tool**, not a production benchmark. The implementations are intentionally basic and unoptimized to clearly illustrate architectural patterns and their inherent tradeoffs. Real-world systems would include caching, connection pooling, optimized queries, and many other improvements.

**The goal:** Help you understand *when* each pattern shines and *why* you might choose one over the other—not to declare a winner.

---

## Root Configuration Files

The project root contains these orchestration files:

| File | Purpose | When to Use |
|------|---------|-------------|
| **`bootstrap.sh`** | macOS/Linux setup script | Installs prerequisites (brew on macOS), then starts Tilt |
| **`bootstrap.ps1`** | Windows setup script | Installs prerequisites via winget, then starts Tilt |
| **`Tiltfile`** | Kubernetes development orchestration | Used by Tilt for live-reload local development on K8s |
| **`docker-compose.yml`** | Standalone Docker orchestration | When you want to run without Kubernetes |

---

## Quick Start (Recommended)

The bootstrap scripts install all prerequisites automatically and launch Tilt:

**macOS/Linux:**
```bash
./bootstrap.sh
```

**Windows (PowerShell):**
```powershell
.\bootstrap.ps1
```

**macOS:** Installs via Homebrew (auto-installs brew if missing)
**Linux:** Checks prerequisites and provides install instructions if missing
**Windows:** Installs via winget

All scripts will:
1. Install Docker Desktop, kubectl, Tilt, and Java 21 (skips if already installed)
2. Enable Kubernetes in Docker Desktop
3. Pre-build all Maven services
4. Launch Tilt

---

## Manual Setup (If You Prefer)

If you want to install prerequisites yourself and just run `tilt up`:

### Prerequisites

| Tool | Purpose | Install |
|------|---------|---------|
| Docker Desktop | Containers + K8s | [docker.com](https://docs.docker.com/get-docker/) — enable Kubernetes in Settings |
| kubectl | K8s CLI | `brew install kubectl` or `winget install Kubernetes.kubectl` |
| Tilt | Dev orchestration | `brew tap tilt-dev/tap && brew install tilt` or `winget install Tilt.Tilt` |
| Java 21+ | Build services | `brew install openjdk@21` or `winget install Microsoft.OpenJDK.21` |

### Verify Prerequisites

```bash
docker info          # Docker running
kubectl get nodes    # Kubernetes ready (should show "docker-desktop")
tilt version         # Tilt installed
java -version        # Java 21+
```

### Run Tilt Directly

Once prerequisites are installed:

```bash
tilt up              # Builds services on first run (slower initial start)
```

---

## Start Specific Stacks

```bash
tilt up -- --federation-only   # Just Federation architecture
tilt up -- --event-only        # Just Event-Driven Projections
```

## Stop

```bash
tilt down                      # Stop services (Ctrl+C also works)
kubectl delete namespace federation kafka  # Full cleanup
```

## Alternative: Docker Compose (No Kubernetes)

If you don't want to use Tilt/Kubernetes:

```bash
docker compose up --build      # Build and start
docker compose down            # Stop
```

## Access Points

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| Federation Router | http://localhost:4000 |
| Query Service (Event-Driven) | http://localhost:8090 |
| Tilt UI | http://localhost:10350 |

## Using the Demo

Once running, open the **Dashboard** at http://localhost:3000

### What You Can Do

1. **Compare Query Performance**
   - The dashboard shows Federation vs Event-Driven side-by-side
   - See latency differences for the same queries
   - Observe real-time vs eventual consistency

2. **Create Data**
   - Use the forms to add new people
   - Both architectures will reflect the new data
   - Event-Driven shows a brief lag before data appears (Kafka propagation)

3. **Test Failure Scenarios**
   ```bash
   # Stop Security service
   kubectl scale deployment security-subgraph -n federation --replicas=0

   # Bring it back
   kubectl scale deployment security-subgraph -n federation --replicas=1
   ```
   - Federation queries fail when services are down
   - Event-Driven continues working with stale data

4. **Explore GraphQL**
   - Open Apollo Sandbox at http://localhost:4000
   - Run queries across federated subgraphs

### Example Query (Federation)

```graphql
{
  persons {
    id
    name
    email
    employee {
      title
      department
    }
    badge {
      badgeNumber
      accessLevel
    }
  }
}
```

---

## Architecture Comparison

Both architectures serve the same data but make fundamentally different tradeoffs.

### GraphQL Federation

```
┌─────────────────────────────────────────────────────────┐
│                    Client Query                         │
└─────────────────────────┬───────────────────────────────┘
                          │ Router orchestrates subgraph calls
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │    HR    │    │Employment│    │ Security │
    │ Subgraph │    │ Subgraph │    │ Subgraph │
    └────┬─────┘    └────┬─────┘    └────┬─────┘
         ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ HR DB    │    │ Emp DB   │    │ Sec DB   │
    └──────────┘    └──────────┘    └──────────┘

✓ Data is always fresh (real-time)
✗ Latency is additive across services
✗ One service down = entire query fails
```

### Event-Driven Projections

```
WRITE PATH (async):
┌──────────┐     ┌──────────┐     ┌──────────┐
│ HR Event │ ──► │  Kafka   │ ──► │ Consumer │ ──► Local Projection
│ Service  │     │          │     │          │
└──────────┘     └──────────┘     └──────────┘

READ PATH (sync):
┌─────────────────────────────────────────────────────────┐
│                    Client Query                         │
└─────────────────────────┬───────────────────────────────┘
                          │ Single local query
                          ▼
                    ┌──────────┐
                    │Projection│
                    │ Service  │
                    └────┬─────┘
                         ▼
                    ┌──────────┐
                    │  Local   │
                    │   DB     │
                    └──────────┘

✓ Blazing fast reads (local data)
✓ Services can be down; queries still work
✗ Data may be stale (eventual consistency)
✗ More complex infrastructure (Kafka, consumers)
```

---

## Key Tradeoffs

| Aspect | Federation | Event-Driven Projections |
|--------|-----------|-------------------|
| **Query Latency** | Higher (multiple network hops) | Lower (local database) |
| **Data Freshness** | Real-time | Eventually consistent |
| **Service Coupling** | Tight (all must be up) | Loose (async via Kafka) |
| **Failure Mode** | Cascading failures | Graceful degradation |
| **Write Complexity** | Simple (direct mutation) | Complex (event + propagation) |
| **Infrastructure** | Simpler | More moving parts |

**Neither is "better"** — the right choice depends on your requirements for consistency, availability, and latency.

---

## Project Structure

```
├── clients/
│   └── dashboard/               # React comparison dashboard
│
├── services/
│   ├── federation/              # GraphQL Federation subgraphs
│   │   ├── hr-subgraph/
│   │   ├── employment-subgraph/
│   │   └── security-subgraph/
│   └── event/                   # Event-Driven services
│       ├── hr-events-service/
│       ├── employment-events-service/
│       ├── security-events-service/
│       ├── projection-consumer/
│       └── query-service/
│
├── infra/                       # Infrastructure configs
│   ├── docker/                  # Shared Dockerfiles — [README](infra/docker/README.md)
│   ├── k8s/                     # Kubernetes manifests — [README](infra/k8s/README.md)
│   ├── maven/                   # Shared Maven wrapper
│   ├── router/                  # Apollo Router config — [README](infra/router/README.md)
│   ├── scripts/                 # Utility scripts — [README](infra/scripts/README.md)
│   └── tilt/                    # Tilt setup scripts — [README](infra/tilt/README.md)
│
├── tests/                       # Playwright E2E tests
│
├── bootstrap.sh                 # macOS/Linux setup script
├── bootstrap.ps1                # Windows setup script (winget + tilt up)
├── Tiltfile                     # Tilt/K8s orchestration
└── docker-compose.yml           # Docker orchestration
```

## Technology Stack

- **Subgraphs**: Quarkus 3.x + SmallRye GraphQL
- **Router**: Apollo Router v1.57.1
- **Event Streaming**: Apache Kafka 3.9.0 (KRaft mode)
- **Database**: PostgreSQL 15
- **Local Dev**: Tilt + Docker Desktop Kubernetes

## Troubleshooting

**Docker build fails**
- Ensure Docker Desktop is running
- Try `docker system prune` to free up space

**Services not starting**
- Check Docker Desktop has enough memory (4GB+ recommended)
- Verify Kubernetes is enabled in Docker Desktop settings

**Port already in use**
- Stop other services using ports 3000, 4000, 8080-8085, 8090
- Run `docker compose down` to clean up orphaned containers

## Documentation

- [Tilt Development Guide](docs/tilt-development.md) — Detailed local dev setup
