@echo off
echo ========================================================
echo Meeting Room Booking - Building and Starting Services
echo ========================================================

echo [1/3] Compiling and building Docker image tarballs with Google Jib...
call mvn compile jib:buildTar -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven Jib build failed!
    exit /b %ERRORLEVEL%
)

echo [2/3] Loading images into Docker daemon...
for %%s in (config-server discovery-server api-gateway room-service booking-service notification-service) do (
    echo   Loading %%s...
    docker load -i %%s\target\jib-image.tar
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to load %%s!
        exit /b %ERRORLEVEL%
    )
)

echo [3/3] Starting containers via Docker Compose...
docker compose up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker Compose failed!
    exit /b %ERRORLEVEL%
)

echo ========================================================
echo All services started! Check status with: docker compose ps
echo ========================================================
