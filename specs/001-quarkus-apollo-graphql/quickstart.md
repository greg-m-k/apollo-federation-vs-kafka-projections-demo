# Quickstart: GraphQL Federation vs Kafka Projections Demo

Compare two architectural approaches for distributed data composition in under 5 minutes.

## Prerequisites

- Docker Desktop with Kubernetes enabled (or Kind cluster)
- [Tilt](https://tilt.dev/) installed (`brew install tilt` or [download](https://docs.tilt.dev/install.html))
- ~4GB available RAM for the cluster

## Start the Demo

```bash
# Clone and start
git clone <repository-url>
cd apollo-demo
tilt up
```

Open **http://localhost:10350** to watch services build and deploy.

**First run**: Expect 3-5 minutes for Maven builds and image creation.
**Subsequent runs**: Services start in ~30 seconds.

## Access the Comparison Dashboard

Once Tilt shows all services green, open:

**http://localhost:3000** - Live Architectural Comparison Dashboard

## What You'll See

The dashboard compares two approaches side-by-side:

### GraphQL Federation (Left Panel)
- Apollo Router composing 3 subgraphs (HR, Employment, Security)
- Synchronous real-time data composition
- Per-subgraph timing displayed on diagram edges

### Kafka Projections (Right Panel)
- Event-driven CQRS with local projections
- Asynchronous eventual consistency
- Single-hop queries from pre-composed data

## Try It Out

1. **Query Both Architectures**: Click the blue button to fetch the same person from both systems
2. **Create Person**: Add a new person and watch:
   - Federation: Immediate availability
   - Kafka: Propagation delay through Kafka → Consumer → Projection
3. **Compare Timing**: Observe per-service latency on the architecture diagrams

## Timing Instrumentation

The demo includes comprehensive timing headers:

### Federation Headers
| Header | Description |
|--------|-------------|
| `X-HR-Time-Ms` | HR subgraph total time |
| `X-Employment-Time-Ms` | Employment subgraph total time |
| `X-Security-Time-Ms` | Security subgraph total time |
| `X-*-Timing-Details` | JSON breakdown (db_query, db_resolve, etc.) |

### Kafka Headers
| Header | Description |
|--------|-------------|
| `X-Query-Time-Ms` | Projection service query time |
| `X-Data-Freshness` | Age of projected data |
| `X-HR-Events-Time-Ms` | HR Events service mutation time |
| `X-HR-Events-Timing-Details` | JSON breakdown (db_write, outbox_write) |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    FEDERATION SIDE                               │
├─────────────────────────────────────────────────────────────────┤
│  Client → Router → HR Subgraph    → PostgreSQL                  │
│                  → Employment     → PostgreSQL                  │
│                  → Security       → PostgreSQL                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    KAFKA SIDE                                    │
├─────────────────────────────────────────────────────────────────┤
│  Client → Projection Service → Local Projections (PostgreSQL)  │
│                                                                  │
│  HR Events Svc → Outbox → Kafka → Consumer → Projections       │
│  (write path)              (async propagation)                  │
└─────────────────────────────────────────────────────────────────┘
```

## Useful URLs

| URL | Purpose |
|-----|---------|
| http://localhost:3000 | Comparison Dashboard |
| http://localhost:4000 | Apollo Router GraphQL endpoint |
| http://localhost:10350 | Tilt UI |
| http://localhost:8090/api/composed/{personId} | Kafka query service |
| http://localhost:8084/api/persons | HR Events service (mutations) |

## Example GraphQL Query

```graphql
{
  person(id: "person-001") {
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

## Stopping the Demo

```bash
# Stop all services (keeps images)
tilt down

# For CI/batch mode (auto-exits)
tilt ci --timeout 5m
```

## Troubleshooting

### Services won't start
```bash
# Check Tilt status
tilt get uiresources

# View logs for specific service
kubectl logs -n federation-demo -l app=hr-subgraph
```

### Port conflicts
Tilt handles port-forwarding automatically. If ports are in use:
```bash
# Check what's using a port
netstat -ano | findstr :4000
```

### Rebuild a service
```bash
tilt trigger hr-subgraph-build
```
