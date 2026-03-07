@echo off
rem ─────────────────────────────────────────────────────────────────────────────
rem start-mock-uat.bat  —  Start all three PT-Blotter mock UAT servers (Windows)
rem
rem Starts:
rem   BlotterDevServer    — WireMock + React blotter  →  http://localhost:9099/blotter/
rem   ConfigDevServer     — In-memory Config Service  →  http://localhost:8090
rem   DeploymentDevServer — Service registry          →  http://localhost:9098/deployment/
rem
rem Requires: Maven on PATH, Java 21+
rem
rem Usage (from the project root):
rem   scripts\start-mock-uat.bat
rem
rem Then run the regression suite:
rem   mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
rem
rem Stop with:
rem   scripts\stop-mock-uat.bat
rem ─────────────────────────────────────────────────────────────────────────────

set PROJECT_DIR=%~dp0..

echo.
echo Starting Mock UAT environment...
echo.
echo   BlotterDevServer    ^>  http://localhost:9099/blotter/
echo   ConfigDevServer     ^>  http://localhost:8090
echo   DeploymentDevServer ^>  http://localhost:9098/deployment/
echo.

rem Each server opens in its own console window.
rem Press ENTER in the server window to stop that individual server.
start "BlotterDevServer" cmd /k "cd /d "%PROJECT_DIR%" && mvn exec:java -pl b-bot-sandbox -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test"
start "ConfigDevServer" cmd /k "cd /d "%PROJECT_DIR%" && mvn exec:java -pl b-bot-sandbox -Dexec.mainClass=utils.ConfigDevServer -Dexec.classpathScope=test"
start "DeploymentDevServer" cmd /k "cd /d "%PROJECT_DIR%" && mvn exec:java -pl b-bot-sandbox -Dexec.mainClass=utils.DeploymentDevServer -Dexec.classpathScope=test"

echo Waiting 20 seconds for all servers to start...
timeout /t 20 /nobreak >nul

echo.
echo Mock UAT environment should now be running.
echo.
echo Run the regression suite:
echo   mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat
echo.
echo Or a subset by tag:
echo   mvn verify -pl pt-blotter-regression-template -Db-bot.env=mockuat -Dcucumber.filter.tags="@smoke"
echo.
echo Stop:  close the three server console windows,
echo        or run:  scripts\stop-mock-uat.bat
echo.
