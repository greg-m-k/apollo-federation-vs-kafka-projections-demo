# Docker

Shared Dockerfiles used by all services.

## Files

### `Dockerfile.quarkus-jvm`

Standard Dockerfile for all Quarkus JVM services. Used by both Federation subgraphs and Kafka event services.

**Base image:** `registry.access.redhat.com/ubi8/openjdk-21:1.20`

**How it works:**
1. Expects pre-built Quarkus app in `target/quarkus-app/`
2. Copies the layered Quarkus structure (lib/, app/, quarkus/)
3. Runs as non-root user (185)
4. Exposes port 8080

**Usage in docker-compose.yml:**
```yaml
build:
  context: ./services/federation/hr-subgraph
  dockerfile: ../../../infra/docker/Dockerfile.quarkus-jvm
```

**Usage in Tiltfile:**
```python
dockerfile='infra/docker/Dockerfile.quarkus-jvm'
```

## Why Shared?

All 8 Quarkus services use identical containerization:
- 3 Federation subgraphs (hr, employment, security)
- 3 Event services (hr-events, employment-events, security-events)
- 2 Query services (projection-consumer, query-service)

One Dockerfile = consistent builds, easier maintenance.
