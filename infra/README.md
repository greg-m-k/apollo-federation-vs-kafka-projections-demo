# Infrastructure

All deployment, containerization, and orchestration configuration.

## Directory Structure

```
infra/
├── docker/          # Shared Dockerfiles
├── k8s/             # Kubernetes manifests
├── maven/           # Shared Maven wrapper
├── router/          # Apollo Router configuration
├── scripts/         # Utility and demo scripts
└── tilt/            # Tilt local dev setup
```

## Quick Reference

| Folder | Purpose | Key Files |
|--------|---------|-----------|
| `docker/` | Container builds | `Dockerfile.quarkus-jvm` |
| `k8s/` | K8s deployments | `federation/*.yaml`, `kafka/*.yaml` |
| `maven/` | Build tooling | `mvnw`, `mvnw.cmd` |
| `router/` | GraphQL Federation | `supergraph.yaml`, `router.yaml` |
| `scripts/` | Automation | `init-multiple-dbs.sh` |
| `tilt/` | Local dev | `kind-cluster.yaml` |

## Getting Started

```bash
# From repo root - installs prereqs and starts Tilt
./bootstrap.sh        # macOS
.\bootstrap.ps1       # Windows

# Or if prereqs already installed
tilt up

# Open dashboard
open http://localhost:3000
```

## What Lives Where

**Application code:** `services/`
- Federation subgraphs own their `schema.graphql`
- Kafka event services own their domain logic

**Infrastructure:** `infra/`
- How to build it (docker/)
- How to deploy it (k8s/)
- How to compose it (router/)
- How to run it locally (tilt/)

This separation keeps deployment concerns out of application code.

## See Also

Each subfolder has its own README with detailed documentation.
