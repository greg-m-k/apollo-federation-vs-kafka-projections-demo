# -*- mode: python -*-
# Tiltfile for Apollo Federation vs Kafka Projections Demo
# ========================================================
#
# Usage:
#   tilt up                       # Start all services
#   tilt up -- --federation-only  # Federation stack only
#   tilt up -- --kafka-only       # Kafka Projections stack only
#
# Prerequisites:
#   - kind cluster created via: .\tilt\scripts\setup-dev.ps1
#   - Maven projects pre-built (setup script does this)

# Load extensions
load('ext://restart_process', 'docker_build_with_restart')

# ============================================================================
# CONFIGURATION
# ============================================================================

# Allow local development clusters
allow_k8s_contexts(['kind-apollo-demo', 'docker-desktop'])

# Update settings for better performance
update_settings(
    max_parallel_updates=3,
    k8s_upsert_timeout_secs=120
)

# User settings (can be overridden via command line)
config.define_bool('federation-only')
config.define_bool('kafka-only')
cfg = config.parse()

# Determine which stacks to deploy
deploy_federation = not cfg.get('kafka-only', False)
deploy_kafka = not cfg.get('federation-only', False)

# ============================================================================
# NAMESPACES
# ============================================================================

k8s_yaml('k8s/namespace.yaml')

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def quarkus_service(name, context, namespace, db_name, port_forward, resource_deps=[], extra_env={}):
    """
    Build and deploy a Quarkus JVM service.

    1. Runs Maven locally to build the quarkus-app directory
    2. Builds Docker image from the pre-built artifacts
    3. Configures live_update for JAR syncing (requires restart)
    """
    # Local Maven build
    local_resource(
        name + '-build',
        cmd='cd ' + context + ' && .\\mvnw.cmd package -DskipTests -q',
        deps=[
            context + '/src',
            context + '/pom.xml'
        ],
        resource_deps=resource_deps,
        labels=[namespace, 'build']
    )

    # Docker build with restart capability
    docker_build_with_restart(
        name + ':latest',
        context=context,
        dockerfile=context + '/src/main/docker/Dockerfile.jvm',
        entrypoint=['/opt/jboss/container/java/run/run-java.sh'],
        only=[
            'target/quarkus-app'
        ],
        live_update=[
            sync(context + '/target/quarkus-app/lib/', '/deployments/lib/'),
            sync(context + '/target/quarkus-app/app/', '/deployments/app/'),
            sync(context + '/target/quarkus-app/quarkus/', '/deployments/quarkus/'),
            sync(context + '/target/quarkus-app/quarkus-run.jar', '/deployments/quarkus-run.jar'),
        ]
    )

# ============================================================================
# FEDERATION STACK
# ============================================================================

if deploy_federation:
    # PostgreSQL for Federation
    k8s_yaml('k8s/federation/postgres.yaml')
    k8s_resource(
        'postgres-federation',
        port_forwards=['5434:5432'],
        labels=['federation', 'infra']
    )

    # HR Subgraph
    quarkus_service(
        name='hr-subgraph',
        context='./services/federation/hr-subgraph',
        namespace='federation',
        db_name='hr_db',
        port_forward='8091:8080',
        resource_deps=['postgres-federation']
    )
    k8s_yaml('k8s/federation/hr-subgraph.yaml')
    k8s_resource(
        'hr-subgraph',
        port_forwards=['8091:8080'],
        resource_deps=['hr-subgraph-build', 'postgres-federation'],
        labels=['federation', 'subgraph']
    )

    # Employment Subgraph
    quarkus_service(
        name='employment-subgraph',
        context='./services/federation/employment-subgraph',
        namespace='federation',
        db_name='employment_db',
        port_forward='8092:8080',
        resource_deps=['postgres-federation']
    )
    k8s_yaml('k8s/federation/employment-subgraph.yaml')
    k8s_resource(
        'employment-subgraph',
        port_forwards=['8092:8080'],
        resource_deps=['employment-subgraph-build', 'postgres-federation'],
        labels=['federation', 'subgraph']
    )

    # Security Subgraph
    quarkus_service(
        name='security-subgraph',
        context='./services/federation/security-subgraph',
        namespace='federation',
        db_name='security_db',
        port_forward='8093:8080',
        resource_deps=['postgres-federation']
    )
    k8s_yaml('k8s/federation/security-subgraph.yaml')
    k8s_resource(
        'security-subgraph',
        port_forwards=['8093:8080'],
        resource_deps=['security-subgraph-build', 'postgres-federation'],
        labels=['federation', 'subgraph']
    )

    # Apollo Router
    k8s_yaml('k8s/federation/router.yaml')
    k8s_resource(
        'router',
        port_forwards=['4000:4000', '8088:8088'],
        resource_deps=['hr-subgraph', 'employment-subgraph', 'security-subgraph'],
        labels=['federation', 'gateway']
    )

# ============================================================================
# KAFKA PROJECTIONS STACK
# ============================================================================

if deploy_kafka:
    # PostgreSQL for Kafka services
    k8s_yaml('k8s/kafka/postgres.yaml')
    k8s_resource(
        'postgres-kafka',
        port_forwards=['5433:5432'],
        labels=['kafka', 'infra']
    )

    # Kafka
    k8s_yaml('k8s/kafka/kafka.yaml')
    k8s_resource(
        'kafka',
        port_forwards=['9092:9092'],
        resource_deps=['postgres-kafka'],
        labels=['kafka', 'infra']
    )

    # HR Events Service
    quarkus_service(
        name='hr-events-service',
        context='./services/kafka/hr-events-service',
        namespace='kafka',
        db_name='hr_events_db',
        port_forward='8084:8080',
        resource_deps=['postgres-kafka', 'kafka']
    )
    k8s_yaml('k8s/kafka/hr-events-service.yaml')
    k8s_resource(
        'hr-events-service',
        port_forwards=['8084:8080'],
        resource_deps=['hr-events-service-build', 'postgres-kafka', 'kafka'],
        labels=['kafka', 'service']
    )

    # Employment Events Service
    quarkus_service(
        name='employment-events-service',
        context='./services/kafka/employment-events-service',
        namespace='kafka',
        db_name='employment_events_db',
        port_forward='8085:8080',
        resource_deps=['postgres-kafka', 'kafka']
    )
    k8s_yaml('k8s/kafka/employment-events-service.yaml')
    k8s_resource(
        'employment-events-service',
        port_forwards=['8085:8080'],
        resource_deps=['employment-events-service-build', 'postgres-kafka', 'kafka'],
        labels=['kafka', 'service']
    )

    # Security Events Service
    quarkus_service(
        name='security-events-service',
        context='./services/kafka/security-events-service',
        namespace='kafka',
        db_name='security_events_db',
        port_forward='8086:8080',
        resource_deps=['postgres-kafka', 'kafka']
    )
    k8s_yaml('k8s/kafka/security-events-service.yaml')
    k8s_resource(
        'security-events-service',
        port_forwards=['8086:8080'],
        resource_deps=['security-events-service-build', 'postgres-kafka', 'kafka'],
        labels=['kafka', 'service']
    )

    # Projection Consumer
    quarkus_service(
        name='projection-consumer',
        context='./services/kafka/projection-consumer',
        namespace='kafka',
        db_name='projections_db',
        port_forward='8089:8080',
        resource_deps=['postgres-kafka', 'kafka']
    )
    k8s_yaml('k8s/kafka/projection-consumer.yaml')
    k8s_resource(
        'projection-consumer',
        port_forwards=['8089:8080'],
        resource_deps=['projection-consumer-build', 'hr-events-service', 'employment-events-service', 'security-events-service'],
        labels=['kafka', 'service']
    )

    # Query Service
    quarkus_service(
        name='query-service',
        context='./services/kafka/query-service',
        namespace='kafka',
        db_name='projections_db',
        port_forward='8090:8080',
        resource_deps=['postgres-kafka']
    )
    k8s_yaml('k8s/kafka/query-service.yaml')
    k8s_resource(
        'query-service',
        port_forwards=['8090:8080'],
        resource_deps=['query-service-build', 'projection-consumer'],
        labels=['kafka', 'service']
    )

# ============================================================================
# DASHBOARD
# ============================================================================

if deploy_federation and deploy_kafka:
    # Build dashboard
    docker_build(
        'comparison-dashboard:latest',
        context='./dashboard',
        dockerfile='./dashboard/Dockerfile',
        live_update=[
            sync('./dashboard/src', '/app/src'),
            run('cd /app && npm run build', trigger=['./dashboard/package.json']),
        ]
    )

    # Deploy dashboard
    k8s_yaml('k8s/kafka/dashboard.yaml')
    k8s_resource(
        'comparison-dashboard',
        port_forwards=['3000:80'],
        resource_deps=['router', 'query-service'],
        labels=['dashboard']
    )

# ============================================================================
# CONVENIENCE RESOURCES
# ============================================================================

if deploy_federation:
    local_resource(
        'federation-ready',
        cmd='echo "Federation stack is ready at http://localhost:4000"',
        resource_deps=['router'],
        labels=['federation']
    )

if deploy_kafka:
    local_resource(
        'kafka-ready',
        cmd='echo "Kafka Projections stack is ready at http://localhost:8090"',
        resource_deps=['query-service'],
        labels=['kafka']
    )

if deploy_federation and deploy_kafka:
    local_resource(
        'all-ready',
        cmd='echo "All services ready! Dashboard: http://localhost:3000"',
        resource_deps=['comparison-dashboard'],
        labels=['dashboard']
    )
