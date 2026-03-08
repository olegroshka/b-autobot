#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# stop-mock-uat.sh  —  Stop all three PT-Blotter mock UAT servers
#
# Works on both Linux/macOS (kill) and Windows/Git Bash (PowerShell).
# ─────────────────────────────────────────────────────────────────────────────
PID_DIR="/tmp/bbot-mock-uat"

# Detect Windows (Git Bash / MSYS2)
is_windows() { [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; }

kill_process() {
    local pid="$1"
    local label="$2"
    if [ -z "$pid" ]; then return; fi
    if is_windows; then
        # On Windows the bash PID may differ from the Windows PID; use PowerShell
        # to stop the process tree so child JVMs are also terminated.
        powershell.exe -NoProfile -Command "
            try { Stop-Process -Id $pid -Force -ErrorAction Stop }
            catch { }
            # Also kill any child processes
            Get-CimInstance Win32_Process | Where-Object { \$_.ParentProcessId -eq $pid } |
                ForEach-Object { try { Stop-Process -Id \$_.ProcessId -Force -ErrorAction Stop } catch { } }
        " 2>/dev/null
    else
        kill "$pid" 2>/dev/null
    fi
    echo "  Stopped PID $pid ($label)"
}

kill_by_port() {
    local port="$1"
    local name="$2"
    local pid
    if is_windows; then
        pid=$(netstat -ano 2>/dev/null | grep ":${port}.*LISTENING" | awk '{print $5}' | head -1)
        if [ -n "$pid" ] && [ "$pid" -gt 0 ] 2>/dev/null; then
            powershell.exe -NoProfile -Command "
                try { Stop-Process -Id $pid -Force -ErrorAction Stop; Write-Host 'Stopped' }
                catch { Write-Host 'Already stopped' }
            " 2>/dev/null
            echo "  Stopped port $port PID $pid ($name)"
        fi
    else
        pid=$(lsof -ti tcp:"$port" 2>/dev/null)
        if [ -n "$pid" ]; then
            kill "$pid" 2>/dev/null
            echo "  Stopped port $port PID $pid ($name)"
        fi
    fi
}

echo "Stopping Mock UAT environment..."

# Primary: kill by port (most reliable — finds the actual listening JVM)
kill_by_port 9099 "blotter"
kill_by_port 8090  "config-service"
kill_by_port 9098  "deployment"

# Fallback: kill by saved PID files (cleans up any remaining wrapper processes)
for name in blotter config deployment; do
    pid_file="$PID_DIR/${name}.pid"
    holder_file="$PID_DIR/${name}-holder.pid"
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        kill_process "$pid" "$pid_file" 2>/dev/null || true
        rm -f "$pid_file"
    fi
    if [ -f "$holder_file" ]; then
        pid=$(cat "$holder_file")
        kill "$pid" 2>/dev/null || true
        rm -f "$holder_file"
    fi
    rm -f "$PID_DIR/${name}.fifo"
done

echo "Done."
