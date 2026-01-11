# Kubernetes Manifests

Kubernetes deployment manifests for both architectures.

## Directory Structure

```
k8s/
├── namespace.yaml           # Creates federation-demo and kafka-demo namespaces
├── kustomization.yaml       # Kustomize base config
├── federation/              # GraphQL Federation stack
│   ├── postgres.yaml        # PostgreSQL for subgraphs
│   ├── hr-subgraph.yaml
│   ├── employment-subgraph.yaml
│   ├── security-subgraph.yaml
│   └── router.yaml          # Apollo Router + ConfigMap with supergraph
├── kafka/                   # Event-Driven Projections stack
│   ├── postgres.yaml        # PostgreSQL for events + projections
│   ├── kafka.yaml           # Apache Kafka (KRaft mode)
│   ├── hr-events-service.yaml
│   ├── employment-events-service.yaml
│   ├── security-events-service.yaml
│   ├── projection-consumer.yaml
│   ├── query-service.yaml
│   └── dashboard.yaml       # Comparison dashboard
└── infra/                   # Shared infrastructure (optional)
    └── monitoring/          # Prometheus/Grafana (if enabled)
```

## Namespaces

Two namespaces isolate the architectures:
- `federation` - GraphQL Federation services
- `kafka` - Event-Driven Projection services

## Port Mappings

Services use NodePort for local access:

| Service | NodePort | localhost |
|---------|----------|-----------|
| Apollo Router | 30400 | :4000 |
| Query Service | 30090 | :8090 |
| Dashboard | 30300 | :3000 |
| HR Subgraph | 30091 | :8091 |
| Employment Subgraph | 30092 | :8092 |
| Security Subgraph | 30093 | :8093 |
| PostgreSQL (Federation) | 30434 | :5434 |
| PostgreSQL (Kafka) | 30433 | :5433 |

## Key Configurations

### Router ConfigMap (`federation/router.yaml`)

Contains inline:
- `router.yaml` - Router config
- `timing.rhai` - Timing header script
- `supergraph.graphql` - Composed schema

This is duplicated from `infra/router/federation/` for k8s deployment.

### Kafka (`kafka/kafka.yaml`)

Runs Apache Kafka 3.9 in KRaft mode (no Zookeeper):
- Single broker for local dev
- Auto-creates topics on first use
- Topics: `events.hr.person`, `events.employment.employee`, `events.security.badge`

## Usage

These manifests are applied by Tilt automatically. For manual use:

```bash
# Create namespaces
kubectl apply -f infra/k8s/namespace.yaml

# Deploy Federation stack
kubectl apply -f infra/k8s/federation/

# Deploy Kafka stack
kubectl apply -f infra/k8s/kafka/
```

## Tilt Integration

The Tiltfile references these manifests:
```python
k8s_yaml('infra/k8s/namespace.yaml')
k8s_yaml('infra/k8s/federation/hr-subgraph.yaml')
# etc.
```

Tilt handles image building and live updates on top of these base manifests.
