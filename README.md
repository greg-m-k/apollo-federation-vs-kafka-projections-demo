# Apollo Federation vs Kafka Projections Demo

A side-by-side demonstration of two distributed systems architectures:

1. **GraphQL Federation** - Synchronous composition, real-time data, coupled availability
2. **Kafka Projections** - Asynchronous events, local materialized views, eventual consistency

Both architectures model the same domain: **Person/Employee/Badge** across HR, Employment, and Security bounded contexts.

## Prerequisites

- [Docker Desktop](https://docs.docker.com/get-docker/) with Kubernetes enabled
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Tilt](https://docs.tilt.dev/install.html)
- Java 17+
- `make` (included on Mac/Linux; Windows users: use Git Bash, WSL, or [install make](https://gnuwin32.sourceforge.net/packages/make.htm))

## Quick Start

```bash
# 1. Check prerequisites and pre-build (first time only)
make setup

# 2. Start all services
make up
```

Or just check prerequisites without building:
```bash
make prereqs
```

### Start Specific Stacks

```bash
make federation-only   # Just Federation architecture
make kafka-only        # Just Kafka Projections architecture
```

### Stop

```bash
make down              # Stop services
make clean             # Stop and delete namespaces
```

### Alternative: Docker Compose (No Kubernetes)

If you don't want to use Tilt/Kubernetes:

```bash
# Build and start all services
docker compose -f docker-compose.comparison.yml up --build

# Stop
docker compose -f docker-compose.comparison.yml down
```

### Manual Setup (No Make Required)

If you can't or don't want to use `make`:

**Windows (PowerShell):**
```powershell
# 1. Check prerequisites and pre-build
.\tilt\scripts\setup-dev.ps1

# 2. Start with Tilt
tilt up

# Or start with Docker Compose
docker compose -f docker-compose.comparison.yml up --build
```

**Mac/Linux (Bash):**
```bash
# 1. Check prerequisites and pre-build
./tilt/scripts/setup-dev.sh

# 2. Start with Tilt
tilt up

# Or start with Docker Compose
docker compose -f docker-compose.comparison.yml up --build
```

**Skip pre-build entirely** (Tilt/Docker will build on first run, just slower):
```bash
tilt up
# or
docker compose -f docker-compose.comparison.yml up --build
```

## Access Points

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| Federation Router | http://localhost:4000 |
| Projection Service | http://localhost:8090 |
| Tilt UI | http://localhost:10350 |

## Using the Demo

Once running, open the **Dashboard** at http://localhost:3000

### What You Can Do

1. **Compare Query Performance**
   - The dashboard shows Federation vs Kafka Projections side-by-side
   - See latency differences for the same queries
   - Observe real-time vs eventual consistency

2. **Create Data**
   - Use the forms to add new people
   - Both architectures will reflect the new data
   - Kafka Projections shows a brief lag before data appears

3. **Test Failure Scenarios**
   ```bash
   make kill-security     # Stop Security service
   make restore-security  # Bring it back
   ```
   - Federation queries fail when services are down
   - Kafka Projections continues working with stale data

4. **Run Automated Demo**
   ```bash
   make demo
   ```

5. **Explore GraphQL**
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

## Architecture Comparison

### GraphQL Federation
```
┌─────────────────────────────────────────────────────────┐
│                    Client Query                         │
└─────────────────────────┬───────────────────────────────┘
                          │ 3 network calls (sync)
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

Latency: 45-100ms (additive)
Consistency: REAL-TIME
Failure: 1 service down = query fails
```

### Kafka Projections
```
┌─────────────────────────────────────────────────────────┐
│                    Client Query                         │
└─────────────────────────┬───────────────────────────────┘
                          │ 1 local query
                          ▼
                    ┌──────────┐
                    │Projection│
                    │ Service  │
                    └────┬─────┘
                         ▼
                    ┌──────────┐
                    │  Local   │ ◄─── Consumer ◄─── Kafka
                    │Projections│
                    └──────────┘

Latency: 3-10ms (local)
Consistency: 1-5s lag (eventual)
Failure: Queries work (stale data)
```

## Key Tradeoffs

| Aspect | Federation | Kafka Projections |
|--------|-----------|-------------------|
| Query Latency | High (additive) | Low (local) |
| Data Freshness | Real-time | Eventually consistent |
| Service Coupling | Tight | Loose |
| Failure Mode | Cascading | Isolated |
| Complexity | Lower | Higher |

## Project Structure

```
├── services/
│   ├── federation/              # GraphQL Federation subgraphs
│   │   ├── hr-subgraph/
│   │   ├── employment-subgraph/
│   │   └── security-subgraph/
│   └── kafka/                     # Kafka Projections services
│       ├── hr-cdc-service/
│       ├── employment-cdc-service/
│       ├── security-cdc-service/
│       ├── projection-consumer/ # Kafka consumer
│       └── query-service/       # Projection service
│
├── dashboard/                   # React comparison dashboard
├── router/                      # Apollo Router config
│   └── federation/              # Supergraph schemas
│
├── k8s/
│   ├── federation/              # Federation k8s manifests
│   ├── cdc/                     # CDC k8s manifests
│   └── infra/                   # Shared infrastructure (postgres, kafka)
│
├── tests/                       # Playwright E2E tests
├── scripts/                     # Demo scripts
├── Tiltfile                     # Tilt configuration
└── Makefile                     # Make commands
```

## Technology Stack

- **Subgraphs**: Quarkus 3.x + SmallRye GraphQL
- **Router**: Apollo Router v1.57.1
- **Event Streaming**: Apache Kafka 3.9.0 (KRaft mode)
- **Database**: PostgreSQL 15
- **Local Dev**: Tilt + Docker Desktop Kubernetes

## All Make Commands

```bash
make help             # Show all commands
make setup            # Check prerequisites + pre-build
make prereqs          # Just check prerequisites
make up               # Start all services
make down             # Stop services
make clean            # Full cleanup
make federation-only  # Start Federation only
make kafka-only       # Start Kafka Projections only
make demo             # Run demo script
make test             # Run Playwright tests
make kill-security    # Stop Security service
make restore-security # Restore Security service
make logs-kafka       # Tail Kafka logs
make lag              # Show consumer lag
```

## Troubleshooting

**`make` command not found (Windows)**
- Use Git Bash instead of PowerShell/CMD, or
- Run PowerShell scripts directly: `.\tilt\scripts\setup-dev.ps1`

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

- [Tilt Development Guide](docs/tilt-development.md) - Detailed local dev setup
