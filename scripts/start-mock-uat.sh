#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# start-mock-uat.sh  —  Start all three PT-Blotter mock UAT servers
#
# Starts:
#   BlotterDevServer   — WireMock + React blotter  →  http://localhost:9099/blotter/
#   ConfigDevServer    — In-memory Config Service  →  http://localhost:8090
#   DeploymentDevServer — Service registry         →  http://localhost:9098/deployment/
#
# Requires: Maven on PATH (or set MVN env var), Java 21+
#
# Usage:
#   ./scripts/start-mock-uat.sh          # from the project root
#
# Then run the regression suite:
#   mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
#
# Stop with:
#   ./scripts/stop-mock-uat.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MVN="${MVN:-mvn}"
PID_DIR="/tmp/bbot-mock-uat"

mkdir -p "$PID_DIR"

# Each server uses a named FIFO to keep stdin open.
# Without this, System.in.read() returns immediately when stdin is /dev/null.
start_server() {
    local name="$1"
    local class="$2"
    local fifo="$PID_DIR/${name}.fifo"
    local logfile="$PID_DIR/${name}.log"

    rm -f "$fifo"
    mkfifo "$fifo"

    # Hold the write end of the FIFO open (keeps System.in.read() blocking)
    tail -f /dev/null > "$fifo" &
    echo $! > "$PID_DIR/${name}-holder.pid"

    # Start the server; its stdin reads from the FIFO
    (cd "$PROJECT_DIR" && \
     $MVN exec:java -pl b-bot-sandbox \
         -Dexec.mainClass="com.bbot.sandbox.utils.${class}" \
         -Dexec.classpathScope=test \
         -q < "$fifo") > "$logfile" 2>&1 &
    echo $! > "$PID_DIR/${name}.pid"

    echo "  ${name}: started (log: ${logfile})"
}

echo ""
echo "Starting Mock UAT environment..."
echo ""
start_server blotter     BlotterDevServer
start_server config      ConfigDevServer
start_server deployment  DeploymentDevServer
echo ""
echo "Waiting for servers to be ready..."

wait_for_url() {
    local url="$1"
    local name="$2"
    local retries=30
    printf "  %-20s" "$name"
    for i in $(seq 1 $retries); do
        if curl -s -f -o /dev/null "$url" 2>/dev/null; then
            echo " ready  ($url)"
            return 0
        fi
        printf "."
        sleep 1
    done
    echo " TIMED OUT waiting for $url"
    echo "  Check log: $PID_DIR/${name}.log"
    return 1
}

wait_for_url "http://localhost:9099/blotter/"       "blotter"
wait_for_url "http://localhost:8090/api/config"     "config-service"
wait_for_url "http://localhost:9098/api/deployments" "deployment"

echo ""
echo "Mock UAT environment is running."
echo ""
echo "Run the regression suite:"
echo "  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat"
echo ""
echo "Or a subset by tag:"
echo "  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat -Dcucumber.filter.tags=\"@smoke\""
echo "  mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat -Dcucumber.filter.tags=\"@workflow\""
echo ""
echo "Stop with: scripts/stop-mock-uat.sh"
echo ""
