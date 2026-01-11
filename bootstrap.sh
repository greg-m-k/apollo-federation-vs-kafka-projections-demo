#!/bin/bash
set -e

# Apollo Federation vs Event-Driven Projections Demo
# Zero-to-Tilt bootstrap script for macOS
# Usage: ./bootstrap.sh

#------------------------------------------------------------------------------
# Colors and helpers
#------------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ok() { echo -e "${GREEN}✓${NC} $1"; }
info() { echo -e "${BLUE}→${NC} $1"; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }

#------------------------------------------------------------------------------
# 1. Platform check - macOS only
#------------------------------------------------------------------------------
if [[ "$(uname)" != "Darwin" ]]; then
    fail "This script is for macOS only. For Windows, use: .\\bootstrap.ps1"
fi

echo ""
echo "======================================"
echo "  Apollo Demo Bootstrap (macOS)"
echo "======================================"
echo ""
echo "This script will install (if missing):"
echo "  • Homebrew       - macOS package manager"
echo "  • Docker Desktop - containers + Kubernetes"
echo "  • kubectl        - Kubernetes CLI"
echo "  • Tilt           - local dev orchestration"
echo "  • OpenJDK 21     - Java runtime for builds"
echo ""
echo "Then it will:"
echo "  • Enable Kubernetes in Docker Desktop"
echo "  • Pre-build 8 Maven services"
echo "  • Launch Tilt"
echo ""
echo "NOTE: Docker Desktop will ask for your admin password once"
echo "      to install its privileged helper (first time only)."
echo ""
read -p "Press Enter to continue (or Ctrl+C to cancel)... "

#------------------------------------------------------------------------------
# 2. Install Homebrew if missing
#------------------------------------------------------------------------------
if ! command -v brew &>/dev/null; then
    info "Installing Homebrew (non-interactive)..."
    NONINTERACTIVE=1 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

    # Add brew to PATH for Apple Silicon Macs
    if [[ -f "/opt/homebrew/bin/brew" ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
    ok "Homebrew installed"
else
    ok "Homebrew already installed"
fi

#------------------------------------------------------------------------------
# 3. Install prerequisites via brew
#------------------------------------------------------------------------------
# Prevent brew from auto-updating during installs (slow + interactive)
export HOMEBREW_NO_AUTO_UPDATE=1
export HOMEBREW_NO_INSTALL_CLEANUP=1

install_brew_package() {
    local pkg=$1
    local type=${2:-formula}  # formula or cask
    local cmd=${3:-$pkg}      # command to check (defaults to package name)

    # First check if command already exists (regardless of how it was installed)
    if command -v "$cmd" &>/dev/null; then
        ok "$cmd already installed"
        return 0
    fi

    # Not found, install via brew
    if [[ "$type" == "cask" ]]; then
        info "Installing $pkg (may require sudo for cask)..."
        brew install --cask "$pkg"
    else
        info "Installing $pkg..."
        brew install "$pkg"
    fi
    ok "$pkg installed"
}

# Docker Desktop
install_brew_package "docker" "cask"

# kubectl
install_brew_package "kubectl"

# Tilt (needs tap first)
if ! brew tap | grep -q "tilt-dev/tap"; then
    info "Adding tilt-dev tap..."
    brew tap tilt-dev/tap
fi
install_brew_package "tilt"

# Java 21 - need version 21+, not just any Java
java_version_ok() {
    if ! command -v java &>/dev/null; then
        return 1
    fi
    local version=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
    [[ "$version" -ge 21 ]]
}

if java_version_ok; then
    ok "Java 21+ already installed"
else
    info "Installing OpenJDK 21..."
    brew install openjdk@21
    ok "OpenJDK 21 installed"
fi

#------------------------------------------------------------------------------
# 4. Configure Java PATH
#------------------------------------------------------------------------------
# If brew's openjdk@21 exists, use it; otherwise use system Java
JAVA_HOME_BREW="$(brew --prefix openjdk@21 2>/dev/null)"
if [[ -d "$JAVA_HOME_BREW" ]]; then
    export JAVA_HOME="$JAVA_HOME_BREW"
    export PATH="$JAVA_HOME/bin:$PATH"
    ok "Java 21 configured (JAVA_HOME=$JAVA_HOME)"
elif java_version_ok; then
    ok "Using existing Java 21+ installation"
else
    fail "Java 21+ required but not found"
fi

# Verify Java works
if ! java -version &>/dev/null; then
    fail "Java not working. Please check your installation."
fi

#------------------------------------------------------------------------------
# 5. Start Docker Desktop if not running
#------------------------------------------------------------------------------
if ! docker info &>/dev/null; then
    info "Starting Docker Desktop..."
    open -a Docker

    echo -n "   Waiting for Docker to start"
    timeout=120
    while ! docker info &>/dev/null; do
        echo -n "."
        sleep 2
        timeout=$((timeout - 2))
        if [[ $timeout -le 0 ]]; then
            echo ""
            fail "Docker failed to start within 120 seconds"
        fi
    done
    echo ""
    ok "Docker Desktop is running"
else
    ok "Docker Desktop already running"
fi

#------------------------------------------------------------------------------
# 6. Enable Kubernetes in Docker Desktop (automatic)
#------------------------------------------------------------------------------
DOCKER_SETTINGS="$HOME/Library/Group Containers/group.com.docker/settings.json"

enable_kubernetes() {
    # Wait for settings.json to exist (Docker creates it on first run)
    if [[ ! -f "$DOCKER_SETTINGS" ]]; then
        info "Waiting for Docker Desktop to initialize settings..."
        local wait_count=0
        while [[ ! -f "$DOCKER_SETTINGS" ]] && [[ $wait_count -lt 60 ]]; do
            sleep 2
            wait_count=$((wait_count + 2))
        done
        if [[ ! -f "$DOCKER_SETTINGS" ]]; then
            fail "Docker Desktop settings not found after 60 seconds. Please open Docker Desktop manually first."
        fi
    fi

    # Check if Kubernetes is already enabled
    if grep -q '"kubernetesEnabled"[[:space:]]*:[[:space:]]*true' "$DOCKER_SETTINGS"; then
        ok "Kubernetes already enabled in Docker Desktop"
        return 0
    fi

    info "Enabling Kubernetes in Docker Desktop..."

    # Stop Docker Desktop BEFORE modifying settings (avoid race condition)
    info "Stopping Docker Desktop to modify settings..."
    osascript -e 'quit app "Docker"' 2>/dev/null || true
    sleep 3

    # Update settings.json to enable Kubernetes
    # Use Python for reliable JSON manipulation (available on macOS)
    python3 -c "
import json
with open('$DOCKER_SETTINGS', 'r') as f:
    settings = json.load(f)
settings['kubernetesEnabled'] = True
with open('$DOCKER_SETTINGS', 'w') as f:
    json.dump(settings, f, indent=2)
"

    # Start Docker Desktop with new settings
    info "Starting Docker Desktop with Kubernetes enabled..."
    open -a Docker

    # Wait for Docker to restart
    echo -n "   Waiting for Docker to restart"
    local timeout=120
    while ! docker info &>/dev/null; do
        echo -n "."
        sleep 2
        timeout=$((timeout - 2))
        if [[ $timeout -le 0 ]]; then
            echo ""
            fail "Docker failed to restart within 120 seconds"
        fi
    done
    echo ""
    ok "Docker Desktop restarted with Kubernetes enabled"
}

# Enable Kubernetes if not already enabled
enable_kubernetes

# Wait for docker-desktop context to appear
if ! kubectl config get-contexts docker-desktop &>/dev/null; then
    echo -n "   Waiting for Kubernetes to initialize"
    timeout=180
    while ! kubectl config get-contexts docker-desktop &>/dev/null; do
        echo -n "."
        sleep 3
        timeout=$((timeout - 3))
        if [[ $timeout -le 0 ]]; then
            echo ""
            fail "Kubernetes context not available after 180 seconds. Check Docker Desktop settings."
        fi
    done
    echo ""
fi
ok "Kubernetes context available"

# Switch to docker-desktop context
kubectl config use-context docker-desktop &>/dev/null

# Wait for Kubernetes nodes to be ready
if ! kubectl wait --for=condition=Ready nodes --all --timeout=5s &>/dev/null; then
    echo -n "   Waiting for nodes"
    timeout=180
    while ! kubectl wait --for=condition=Ready nodes --all --timeout=10s &>/dev/null; do
        echo -n "."
        sleep 5
        timeout=$((timeout - 5))
        if [[ $timeout -le 0 ]]; then
            echo ""
            fail "Kubernetes nodes not ready after 180 seconds"
        fi
    done
    echo ""
fi
ok "Kubernetes is ready"

#------------------------------------------------------------------------------
# 7. Pre-build Maven services (parallel)
#------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAVEN_DIR="$SCRIPT_DIR/infra/maven"

# Ensure mvnw is executable
chmod +x "$MAVEN_DIR/mvnw" 2>/dev/null || true

SERVICES=(
    "services/federation/hr-subgraph"
    "services/federation/employment-subgraph"
    "services/federation/security-subgraph"
    "services/event/hr-events-service"
    "services/event/employment-events-service"
    "services/event/security-events-service"
    "services/event/projection-consumer"
    "services/event/query-service"
)

echo ""
info "Pre-building ${#SERVICES[@]} Maven services (parallel)..."
echo ""

# Build all services in parallel
# Must run from infra/maven so mvnw can find .mvn/wrapper
pids=()
for svc in "${SERVICES[@]}"; do
    svc_name=$(basename "$svc")
    pom_path="../../$svc/pom.xml"
    (
        cd "$MAVEN_DIR"
        # Use pipefail to preserve mvnw exit code through the pipe
        set -o pipefail
        ./mvnw -f "$pom_path" package -DskipTests -q 2>&1 | sed "s/^/   [$svc_name] /"
    ) &
    pids+=($!)
done

# Wait for all builds to complete
failed=0
for i in "${!pids[@]}"; do
    if ! wait "${pids[$i]}"; then
        warn "Build failed for ${SERVICES[$i]}"
        failed=1
    fi
done

if [[ $failed -eq 0 ]]; then
    ok "All services built successfully"
else
    warn "Some builds failed, but continuing (Tilt will retry)"
fi

#------------------------------------------------------------------------------
# 8. Launch Tilt
#------------------------------------------------------------------------------
echo ""
echo "======================================"
echo "  Starting Tilt"
echo "======================================"
echo ""
info "Access points once running:"
echo "   • Dashboard:        http://localhost:3000"
echo "   • Federation Router: http://localhost:4000"
echo "   • Query Service:    http://localhost:8090"
echo "   • Tilt UI:          http://localhost:10350"
echo ""
info "Press Ctrl+C to stop"
echo ""

cd "$SCRIPT_DIR"
exec tilt up
