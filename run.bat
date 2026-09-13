@echo off
echo ========================================================
echo Meeting Room Booking - Building and Starting Services
echo ========================================================

echo [1/2] Building microservice images with Google Jib...
call mvn compile jib:dockerBuild -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven Jib build failed!
    exit /b %ERRORLEVEL%
)

echo [2/2] Starting containers via Docker Compose...
docker compose up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker Compose failed!
    exit /b %ERRORLEVEL%
)

echo ========================================================
echo All services started! Check status with: docker compose ps
echo ========================================================
