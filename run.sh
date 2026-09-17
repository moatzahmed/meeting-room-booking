#!/bin/bash
set -e

echo "========================================================"
echo "Meeting Room Booking - Building and Starting Services"
echo "========================================================"

echo "[1/3] Compiling and building Docker image tarballs with Google Jib..."
mvn compile jib:buildTar -DskipTests

echo "[2/3] Loading images into Docker daemon..."
for service in config-server discovery-server api-gateway room-service booking-service notification-service; do
    echo "  Loading $service..."
    docker load -i "$service/target/jib-image.tar"
done

echo "[3/3] Starting containers via Docker Compose..."
docker compose up -d

echo "========================================================"
echo "All services started! Check status with: docker compose ps"
echo "========================================================"
