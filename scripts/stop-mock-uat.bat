@echo off
rem ─────────────────────────────────────────────────────────────────────────────
rem stop-mock-uat.bat  —  Stop all three PT-Blotter mock UAT servers (Windows)
rem ─────────────────────────────────────────────────────────────────────────────

echo Stopping Mock UAT environment...

rem Close console windows opened by start-mock-uat.bat by title
taskkill /FI "WINDOWTITLE eq BlotterDevServer" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq ConfigDevServer"  /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq DeploymentDevServer" /T /F >nul 2>&1

echo Done.
echo.
echo If servers are still running, close their console windows manually,
echo or identify their PIDs with:  netstat -ano ^| findstr "9099 8090 9098"
echo and kill with:  taskkill /PID ^<pid^> /T /F
