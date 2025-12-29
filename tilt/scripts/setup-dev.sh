#!/usr/bin/env bash
# setup-dev.sh - Setup script for Apollo-Demo (Linux/Mac)
#
# Usage:
#   ./tilt/scripts/setup-dev.sh              # Full setup
#   ./tilt/scripts/setup-dev.sh --skip-build # Skip Maven pre-build
#   ./tilt/scripts/setup-dev.sh --check-only # Just check prerequisites

set -e

SKIP_BUILD=false
CHECK_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true; shift ;;
        --check-only) CHECK_ONLY=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo ""
echo "=== Apollo-Demo Development Setup ==="
echo ""

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

ALL_GOOD=true

echo "Checking prerequisites..."
echo ""

# Docker
if command_exists docker; then
    DOCKER_VERSION=$(docker --version 2>/dev/null | sed 's/Docker version //' | sed 's/,.*//')
    echo "  [OK] docker $DOCKER_VERSION"
    
    # Check Docker is running via 'docker info'
    if docker info >/dev/null 2>&1; then
        echo "  [OK] Docker daemon is running"
    else
        echo "  [!!] Docker installed but daemon not running"
        echo "       Start Docker Desktop first"
        ALL_GOOD=false
    fi
else
    echo "  [!!] docker - NOT INSTALLED"
    echo "       Install: https://docs.docker.com/get-docker/"
    ALL_GOOD=false
fi

# kubectl
if command_exists kubectl; then
    KUBECTL_VERSION=$(kubectl version --client -o json 2>/dev/null | grep -o '"gitVersion": "[^"]*"' | head -1 | sed 's/.*: "//' | sed 's/"//')
    echo "  [OK] kubectl $KUBECTL_VERSION"
    
    # Check docker-desktop context exists
    if kubectl config get-contexts -o name 2>/dev/null | grep -q "docker-desktop"; then
        CURRENT_CTX=$(kubectl config current-context 2>/dev/null)
        if [[ "$CURRENT_CTX" == "docker-desktop" ]]; then
            echo "  [OK] kubectl context: docker-desktop"
        else
            echo "  [!!] kubectl context is '$CURRENT_CTX', not docker-desktop"
            echo "       Will switch to docker-desktop"
        fi
    else
        echo "  [!!] docker-desktop context not found"
        echo "       Enable Kubernetes in Docker Desktop Settings"
        ALL_GOOD=false
    fi
else
    echo "  [!!] kubectl - NOT INSTALLED"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "       Install: brew install kubectl"
    else
        echo "       Install: https://kubernetes.io/docs/tasks/tools/"
    fi
    ALL_GOOD=false
fi

# tilt
if command_exists tilt; then
    TILT_VERSION=$(tilt version 2>/dev/null)
    echo "  [OK] tilt $TILT_VERSION"
else
    echo "  [!!] tilt - NOT INSTALLED"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "       Install: brew install tilt"
    else
        echo "       Install: curl -fsSL https://raw.githubusercontent.com/tilt-dev/tilt/master/scripts/install.sh | bash"
    fi
    ALL_GOOD=false
fi

# Java
if command_exists java; then
    # java -version writes to stderr, redirect to stdout
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "  [OK] java $JAVA_VERSION"
else
    echo "  [!!] java - NOT INSTALLED"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "       Install: brew install openjdk@17"
    else
        echo "       Install: sudo apt install openjdk-17-jdk"
    fi
    ALL_GOOD=false
fi

echo ""

if [[ "$ALL_GOOD" == "false" ]]; then
    echo "Some prerequisites are missing. Install them and try again."
    echo ""
    exit 1
fi

echo "All prerequisites OK!"

if [[ "$CHECK_ONLY" == "true" ]]; then
    exit 0
fi

echo ""

# Switch to docker-desktop context
echo "Setting kubectl context to docker-desktop..."
kubectl config use-context docker-desktop 2>/dev/null || {
    echo "Failed to set context. Is Docker Desktop Kubernetes enabled?"
    exit 1
}
echo ""

# Pre-build Maven projects
if [[ "$SKIP_BUILD" == "false" ]]; then
    echo "Pre-building Maven projects..."
    echo "(First run takes several minutes)"
    echo ""

    SERVICES=(
        "services/federation/hr-subgraph"
        "services/federation/employment-subgraph"
        "services/federation/security-subgraph"
        "services/kafka/hr-cdc-service"
        "services/kafka/employment-cdc-service"
        "services/kafka/security-cdc-service"
        "services/kafka/projection-consumer"
        "services/kafka/query-service"
    )

    FAILED=()
    for service in "${SERVICES[@]}"; do
        if [[ -d "$service" ]]; then
            printf "  Building %s..." "$service"
            pushd "$service" > /dev/null
            if ./mvnw package -DskipTests -q 2>/dev/null; then
                echo " OK"
            else
                echo " FAILED"
                FAILED+=("$service")
            fi
            popd > /dev/null
        fi
    done

    if [[ ${#FAILED[@]} -gt 0 ]]; then
        echo ""
        echo "Some builds failed: ${FAILED[*]}"
        echo "Tilt will rebuild these automatically"
    fi
fi

echo ""
echo "=== Setup Complete! ==="
echo ""
echo "Next steps:"
echo "  tilt up          # Start all services"
echo "  make up          # Same thing"
echo "  make help        # See all commands"
echo ""
echo "Access points:"
echo "  Tilt UI:   http://localhost:10350"
echo "  Dashboard: http://localhost:3000"
echo "  Router:    http://localhost:4000"
echo "  CDC Query: http://localhost:8090"
echo ""
