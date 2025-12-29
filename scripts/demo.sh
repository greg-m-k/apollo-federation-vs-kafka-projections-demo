#!/bin/bash
# demo.sh - Automated demo script for Architecture Comparison
# ============================================================

set -e

FEDERATION_URL="http://localhost:4000"
KAFKA_URL="http://localhost:8090"
HR_EVENTS_URL="http://localhost:8084"

echo "=== ARCHITECTURE COMPARISON DEMO ==="
echo ""

# Check services are running
echo "Checking services..."
if ! curl -s "$FEDERATION_URL/.well-known/apollo/server-health" > /dev/null 2>&1; then
    echo "ERROR: Federation Router not responding at $FEDERATION_URL"
    echo "Run 'make up' first to start services."
    exit 1
fi

if ! curl -s "$KAFKA_URL/health" > /dev/null 2>&1; then
    echo "ERROR: Query Service not responding at $KAFKA_URL"
    echo "Run 'make up' first to start services."
    exit 1
fi
echo "All services are running!"
echo ""

# Step 1: Create person via Federation
echo "========================================="
echo "1. Creating person via Federation"
echo "========================================="
PERSON_RESULT=$(curl -s -X POST "$FEDERATION_URL/graphql" \
    -H "Content-Type: application/json" \
    -d '{"query":"mutation { createPerson(name: \"Demo User\", email: \"demo@example.com\") { id name email } }"}')
echo "$PERSON_RESULT" | jq . 2>/dev/null || echo "$PERSON_RESULT"
echo ""

# Step 2: Query composed view from Federation
echo "========================================="
echo "2. Query composed view from FEDERATION"
echo "   (calls HR + Employment + Security subgraphs)"
echo "========================================="
START=$(date +%s%N)
FED_RESULT=$(curl -s -X POST "$FEDERATION_URL/graphql" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ persons { id name email employee { title department } badge { badgeNumber accessLevel } } }"}')
END=$(date +%s%N)
FED_LATENCY=$(( (END - START) / 1000000 ))
echo "$FED_RESULT" | jq . 2>/dev/null || echo "$FED_RESULT"
echo ""
echo "Federation latency: ${FED_LATENCY}ms (3 service calls)"
echo ""

# Step 3: Query composed view from Kafka Projections
echo "========================================="
echo "3. Query composed view from Kafka Projections"
echo "   (single local query)"
echo "========================================="
START=$(date +%s%N)
KAFKA_RESULT=$(curl -s "$KAFKA_URL/api/persons")
END=$(date +%s%N)
KAFKA_LATENCY=$(( (END - START) / 1000000 ))
echo "$KAFKA_RESULT" | jq . 2>/dev/null || echo "$KAFKA_RESULT"
echo ""
echo "Kafka latency: ${KAFKA_LATENCY}ms (1 local query)"
echo ""

# Step 4: Compare latencies
echo "========================================="
echo "4. LATENCY COMPARISON"
echo "========================================="
echo "Federation: ${FED_LATENCY}ms (sync, 3 services)"
echo "Kafka:      ${KAFKA_LATENCY}ms (local projection)"
if [ $FED_LATENCY -gt $KAFKA_LATENCY ]; then
    DIFF=$((FED_LATENCY - KAFKA_LATENCY))
    echo ""
    echo "Kafka is ${DIFF}ms faster!"
fi
echo ""

# Step 5: Demonstrate failure scenario
echo "========================================="
echo "5. FAILURE SCENARIO"
echo "========================================="
echo "Killing Security service..."
kubectl scale deployment security-subgraph --replicas=0 -n federation-demo 2>/dev/null || true
sleep 3

echo ""
echo "Querying Federation (Security is DOWN):"
FED_FAIL=$(curl -s -X POST "$FEDERATION_URL/graphql" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ persons { id name badge { badgeNumber } } }"}')
echo "$FED_FAIL" | jq . 2>/dev/null || echo "$FED_FAIL"

echo ""
echo "Querying Kafka (Security is DOWN but data is local):"
KAFKA_STILL_WORKS=$(curl -s "$KAFKA_URL/api/persons")
echo "$KAFKA_STILL_WORKS" | jq . 2>/dev/null || echo "$KAFKA_STILL_WORKS"

echo ""
echo "Restoring Security service..."
kubectl scale deployment security-subgraph --replicas=1 -n federation-demo 2>/dev/null || true

echo ""
echo "========================================="
echo "DEMO COMPLETE"
echo "========================================="
echo ""
echo "Key Takeaways:"
echo "  - Federation: Real-time data, but coupled availability"
echo "  - Kafka: Fast local queries, but eventually consistent"
echo ""
echo "Open the dashboard at http://localhost:3000 to explore more!"
