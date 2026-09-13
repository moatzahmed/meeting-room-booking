#!/bin/bash
set -e

echo "========================================================"
echo "Meeting Room Booking - Building and Starting Services"
echo "========================================================"

echo "[1/2] Building microservice images with Google Jib..."
mvn compile jib:dockerBuild -DskipTests

echo "[2/2] Starting containers via Docker Compose..."
docker compose up -d

echo "========================================================"
echo "All services started! Check status with: docker compose ps"
echo "========================================================"
