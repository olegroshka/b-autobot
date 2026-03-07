#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# stop-mock-uat.sh  —  Stop all three PT-Blotter mock UAT servers
# ─────────────────────────────────────────────────────────────────────────────
PID_DIR="/tmp/bbot-mock-uat"

kill_pid_file() {
    local file="$1"
    if [ -f "$file" ]; then
        local pid
        pid=$(cat "$file")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null && echo "  Stopped PID $pid ($file)"
        fi
        rm -f "$file"
    fi
}

echo "Stopping Mock UAT environment..."
for name in blotter config deployment; do
    kill_pid_file "$PID_DIR/${name}.pid"
    kill_pid_file "$PID_DIR/${name}-holder.pid"
    rm -f "$PID_DIR/${name}.fifo"
done

echo "Done."
