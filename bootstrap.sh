#!/bin/bash
set -e

# Apollo Federation vs Event-Driven Projections Demo
# Zero-to-Tilt bootstrap script for macOS and Linux
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
# 1. Platform detection
#------------------------------------------------------------------------------
OS="$(uname)"
if [[ "$OS" != "Darwin" && "$OS" != "Linux" ]]; then
    fail "This script is for macOS/Linux only. For Windows, use: .\\bootstrap.ps1"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

#------------------------------------------------------------------------------
# 2. Java version check helper
#------------------------------------------------------------------------------
java_version_ok() {
    if ! command -v java &>/dev/null; then
        return 1
    fi
    local version=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
    [[ "$version" -ge 21 ]]
}

#------------------------------------------------------------------------------
# 3. Platform-specific setup
#------------------------------------------------------------------------------
if [[ "$OS" == "Darwin" ]]; then
    #==========================================================================
    # macOS Setup
    #==========================================================================
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

    # Install Homebrew if missing
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

    # Prevent brew from auto-updating during installs
    export HOMEBREW_NO_AUTO_UPDATE=1
    export HOMEBREW_NO_INSTALL_CLEANUP=1

    install_brew_package() {
        local pkg=$1
        local type=${2:-formula}
        local cmd=${3:-$pkg}
        if command -v "$cmd" &>/dev/null; then
            ok "$cmd already installed"
            return 0
        fi
        if [[ "$type" == "cask" ]]; then
            info "Installing $pkg (may require sudo for cask)..."
            brew install --cask "$pkg"
        else
            info "Installing $pkg..."
            brew install "$pkg"
        fi
        ok "$pkg installed"
    }

    # Install prerequisites
    install_brew_package "docker" "cask"
    install_brew_package "kubectl"

    if ! brew tap | grep -q "tilt-dev/tap"; then
        info "Adding tilt-dev tap..."
        brew tap tilt-dev/tap
    fi
    install_brew_package "tilt"

    if java_version_ok; then
        ok "Java 21+ already installed"
    else
        info "Installing OpenJDK 21..."
        brew install openjdk@21
        ok "OpenJDK 21 installed"
    fi

    # Configure Java PATH
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

    # Start Docker Desktop if not running
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

    # Enable Kubernetes in Docker Desktop
    DOCKER_SETTINGS="$HOME/Library/Group Containers/group.com.docker/settings.json"

    if [[ ! -f "$DOCKER_SETTINGS" ]]; then
        info "Waiting for Docker Desktop to initialize settings..."
        wait_count=0
        while [[ ! -f "$DOCKER_SETTINGS" ]] && [[ $wait_count -lt 60 ]]; do
            sleep 2
            wait_count=$((wait_count + 2))
        done
        if [[ ! -f "$DOCKER_SETTINGS" ]]; then
            fail "Docker Desktop settings not found after 60 seconds."
        fi
    fi

    if grep -q '"kubernetesEnabled"[[:space:]]*:[[:space:]]*true' "$DOCKER_SETTINGS"; then
        ok "Kubernetes already enabled in Docker Desktop"
    else
        info "Enabling Kubernetes in Docker Desktop..."
        info "Stopping Docker Desktop to modify settings..."
        osascript -e 'quit app "Docker"' 2>/dev/null || true
        sleep 3

        python3 -c "
import json
with open('$DOCKER_SETTINGS', 'r') as f:
    settings = json.load(f)
settings['kubernetesEnabled'] = True
with open('$DOCKER_SETTINGS', 'w') as f:
    json.dump(settings, f, indent=2)
"
        info "Starting Docker Desktop with Kubernetes enabled..."
        open -a Docker

        echo -n "   Waiting for Docker to restart"
        timeout=120
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
    fi

    K8S_CONTEXT="docker-desktop"

else
    #==========================================================================
    # Linux Setup
    #==========================================================================
    echo ""
    echo "======================================"
    echo "  Apollo Demo Bootstrap (Linux)"
    echo "======================================"
    echo ""
    echo "Checking prerequisites..."
    echo ""

    missing=()

    # Check Docker
    if command -v docker &>/dev/null && docker info &>/dev/null 2>&1; then
        ok "Docker is installed and running"
    elif command -v docker &>/dev/null; then
        warn "Docker is installed but not running"
        echo "   Start Docker with: sudo systemctl start docker"
        echo "   Or for Docker Desktop: systemctl --user start docker-desktop"
        missing+=("docker (not running)")
    else
        missing+=("docker")
    fi

    # Check kubectl
    if command -v kubectl &>/dev/null; then
        ok "kubectl is installed"
    else
        missing+=("kubectl")
    fi

    # Check Tilt
    if command -v tilt &>/dev/null; then
        ok "Tilt is installed"
    else
        missing+=("tilt")
    fi

    # Check Java 21+
    if java_version_ok; then
        ok "Java 21+ is installed"
    else
        missing+=("java 21+")
    fi

    # If anything missing, print install instructions and exit
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo ""
        warn "Missing prerequisites: ${missing[*]}"
        echo ""
        echo "Install instructions:"
        echo ""
        echo "  Docker (pick one):"
        echo "    # Docker Engine"
        echo "    curl -fsSL https://get.docker.com | sudo sh"
        echo "    sudo usermod -aG docker \$USER && newgrp docker"
        echo ""
        echo "    # Or Docker Desktop (includes Kubernetes)"
        echo "    https://docs.docker.com/desktop/install/linux-install/"
        echo ""
        echo "  kubectl:"
        echo "    curl -LO \"https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl\""
        echo "    chmod +x kubectl && sudo mv kubectl /usr/local/bin/"
        echo ""
        echo "  Tilt:"
        echo "    curl -fsSL https://raw.githubusercontent.com/tilt-dev/tilt/master/scripts/install.sh | bash"
        echo ""
        echo "  Java 21+:"
        echo "    # Ubuntu/Debian"
        echo "    sudo apt install openjdk-21-jdk"
        echo "    # Fedora"
        echo "    sudo dnf install java-21-openjdk-devel"
        echo "    # Arch"
        echo "    sudo pacman -S jdk21-openjdk"
        echo ""
        echo "After installing, run this script again."
        exit 1
    fi

    echo ""
    read -p "Press Enter to continue (or Ctrl+C to cancel)... "

    # Check for Kubernetes - Docker Desktop or other
    DOCKER_SETTINGS="$HOME/.docker/desktop/settings.json"

    if [[ -f "$DOCKER_SETTINGS" ]]; then
        # Docker Desktop on Linux
        if grep -q '"kubernetes"[[:space:]]*:[[:space:]]*{[^}]*"enabled"[[:space:]]*:[[:space:]]*true' "$DOCKER_SETTINGS" 2>/dev/null || \
           grep -q '"kubernetesEnabled"[[:space:]]*:[[:space:]]*true' "$DOCKER_SETTINGS" 2>/dev/null; then
            ok "Kubernetes enabled in Docker Desktop"
        else
            warn "Kubernetes not enabled in Docker Desktop"
            echo "   Enable it: Docker Desktop > Settings > Kubernetes > Enable"
            echo "   Then run this script again."
            exit 1
        fi
        K8S_CONTEXT="docker-desktop"
    else
        # Check for any available K8s context
        if kubectl config current-context &>/dev/null; then
            K8S_CONTEXT=$(kubectl config current-context)
            ok "Using Kubernetes context: $K8S_CONTEXT"
        else
            warn "No Kubernetes context found"
            echo ""
            echo "Options:"
            echo "  1. Install Docker Desktop and enable Kubernetes"
            echo "  2. Install minikube: https://minikube.sigs.k8s.io/docs/start/"
            echo "  3. Install kind: https://kind.sigs.k8s.io/docs/user/quick-start/"
            echo ""
            exit 1
        fi
    fi
fi

#------------------------------------------------------------------------------
# 4. Verify Kubernetes is ready (common for both platforms)
#------------------------------------------------------------------------------
if ! kubectl config get-contexts "$K8S_CONTEXT" &>/dev/null 2>&1; then
    echo -n "   Waiting for Kubernetes context"
    timeout=180
    while ! kubectl config get-contexts "$K8S_CONTEXT" &>/dev/null 2>&1; do
        echo -n "."
        sleep 3
        timeout=$((timeout - 3))
        if [[ $timeout -le 0 ]]; then
            echo ""
            fail "Kubernetes context '$K8S_CONTEXT' not available after 180 seconds"
        fi
    done
    echo ""
fi
ok "Kubernetes context available"

kubectl config use-context "$K8S_CONTEXT" &>/dev/null

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
# 5. Pre-build Maven services (parallel)
#------------------------------------------------------------------------------
MAVEN_DIR="$SCRIPT_DIR/infra/maven"
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

pids=()
for svc in "${SERVICES[@]}"; do
    svc_name=$(basename "$svc")
    pom_path="../../$svc/pom.xml"
    (
        cd "$MAVEN_DIR"
        set -o pipefail
        ./mvnw -f "$pom_path" package -DskipTests -q 2>&1 | sed "s/^/   [$svc_name] /"
    ) &
    pids+=($!)
done

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
# 6. Launch Tilt
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
