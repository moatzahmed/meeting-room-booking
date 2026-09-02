# 🎯 Meeting Room Booking System

The project is a meeting room booking system for use inside a company.

The company has multiple meeting rooms, and employees can:

-   View available rooms.
-   Book a room for a specific time.
-   View their own bookings.
-   Cancel their own bookings.

Administrators can:

-   Add rooms.
-   Enable or disable rooms.
-   View all bookings.

The business domain is intentionally small, but the project is designed
to apply the most important concepts in:

-   Spring Boot
-   Microservices
-   Security
-   Resilience
-   Messaging
-   Observability
-   Docker
-   Continuous Integration

The goal is to use every technology for a real reason instead of adding
technologies only for the sake of using them.

------------------------------------------------------------------------

# Architecture

Use only **3 business microservices**:

``` text
                    ┌───────────────┐
                    │   Keycloak    │
                    │ OAuth2 / OIDC │
                    └───────┬───────┘
                            │
                            │ JWT
                            ▼

Client
  │
  ▼
┌─────────────────────────┐
│       API Gateway       │
│                         │
│ Security                │
│ Rate Limiting           │
│ Routing                 │
│ Correlation ID          │
└────────────┬────────────┘
             │
             │
       ┌─────┴──────────────────────────┐
       │                                │
       ▼                                ▼
┌─────────────────┐            ┌──────────────────┐
│  Room Service   │◄──Feign────│ Booking Service  │
│                 │            │                  │
│ Rooms           │            │ Reservations     │
│ Room Status     │            │ Business Rules   │
│ Availability    │            │ Event Publisher  │
└─────────────────┘            └─────────┬────────┘
                                        │
                                        │ Kafka
                                        ▼
                              ┌────────────────────┐
                              │ Notification Svc   │
                              │                    │
                              │ Kafka Consumer     │
                              │ Mock Notification  │
                              └────────────────────┘
```

Supporting infrastructure:

``` text
Config Server
Eureka Server
Redis
Kafka
Keycloak

Prometheus
Loki
Tempo
Grafana
```

Development and CI tooling:

``` text
GitHub
GitHub Actions
Docker Registry / GHCR
Docker
Docker Compose
```

------------------------------------------------------------------------

# Business Services

## 1. Room Service

Responsibilities:

``` text
Rooms
Room Status
Room Information
Availability
```

A room can contain:

``` text
id
name
location
capacity
status
createdAt
createdBy
updatedAt
```

Room status:

``` text
ACTIVE
INACTIVE
```

------------------------------------------------------------------------

# 2. Booking Service

This is the most important service in the project.

Responsibilities:

``` text
Reservations
Booking Business Rules
Room Availability Check
Booking Cancellation
Kafka Event Publishing
```

A booking can contain:

``` text
id
roomId
userId
startTime
endTime
purpose
status
createdAt
updatedAt
```

Booking status:

``` text
CONFIRMED
CANCELLED
```

------------------------------------------------------------------------

# 3. Notification Service

This is a very small service.

It does not need a public CRUD API.

It is simply a Kafka consumer that listens for:

``` text
BookingCreatedEvent
BookingCancelledEvent
```

Initially, it can only log a notification:

``` text
Booking confirmed for user c65c...
Room: 15
Time: 10:00 - 11:00
```

Storing notifications in a small database is optional.

The following are intentionally out of scope:

``` text
SendGrid
SMTP
Twilio
Real Email
SMS
```

They do not add enough value to the main learning goals of this project.

------------------------------------------------------------------------

# Database Architecture

Use:

``` text
Database per Service
```

For example:

``` text
Room Service
     │
     ▼
room_db
PostgreSQL
```

And:

``` text
Booking Service
     │
     ▼
booking_db
PostgreSQL
```

Do not use:

``` text
Room Service ─┐
              ├── Shared Database ❌
Booking Svc ──┘
```

Each service owns its own data.

------------------------------------------------------------------------

# Main Booking Flow

The user sends:

``` http
POST /api/bookings
```

Request body:

``` json
{
  "roomId": 15,
  "startTime": "2026-09-01T10:00:00",
  "endTime": "2026-09-01T11:00:00",
  "purpose": "Backend Team Meeting"
}
```

The request enters through:

``` text
Client
  │
  ▼
API Gateway
  │
  ├── Validate JWT
  ├── Rate Limit
  └── Route Request
  ▼
Booking Service
```

The Booking Service extracts:

``` text
userId
```

from the JWT.

It then makes a synchronous call using OpenFeign:

``` text
Booking Service
      │
      │ OpenFeign
      ▼
Room Service
```

The request asks:

``` text
Is room 15 available
between
10:00 and 11:00?
```

The Room Service validates:

``` text
Room exists?
     ↓
Room ACTIVE?
     ↓
No conflicting reservation?
     ↓
available = true
```

If the room is available:

``` text
Room Service
      │
      │ available = true
      ▼
Booking Service
      │
      ├── Save Booking
      │
      └── Publish Event
              │
              ▼
      BookingCreatedEvent
              │
              ▼
            Kafka
              │
              ▼
      Notification Service
              │
              ▼
      "Booking confirmed"
```

This applies two communication styles for clear reasons.

Synchronous communication:

``` text
Booking → Room
Synchronous
OpenFeign
Request / Response
```

Asynchronous communication:

``` text
Booking → Notification
Asynchronous
Kafka
Event Driven
```

This is better than using Kafka for every interaction simply because it
is available.

------------------------------------------------------------------------

# Technology Map

  -----------------------------------------------------------------------
  Technology                          Purpose
  ----------------------------------- -----------------------------------
  Spring Boot                         All services

  REST API                            Room and Booking APIs

  DTO                                 Requests and responses

  Bean Validation                     Validate booking times and request
                                      fields

  Exception Handling                  Room unavailable, booking not
                                      found, and similar errors

  Spring Data JPA                     Room and Booking persistence

  PostgreSQL                          Database per service

  Auditing                            `createdAt`, `createdBy`, and
                                      related fields

  OpenAPI / Swagger                   API documentation

  Config Server                       Centralized configuration

  Eureka                              Service discovery

  OpenFeign                           Booking → Room communication

  API Gateway                         Single entry point

  Gateway Filters                     Logging and Correlation ID

  Resilience4j Circuit Breaker        Protect Booking → Room calls

  Retry                               Handle temporary Room Service
                                      failures

  Timeout                             Prevent indefinite waiting

  Bulkhead                            Isolate Room Service calls

  Redis Rate Limiter                  Gateway rate limiting

  OAuth2 / OIDC                       Authentication through Keycloak

  Spring Security                     JWT resource servers and
                                      authorization

  Kafka                               Booking Created / Cancelled events

  Actuator                            Health and metrics endpoints

  Micrometer                          Application metrics

  Prometheus                          Metrics collection

  Loki                                Centralized logs

  Tempo                               Distributed tracing

  Grafana                             Dashboards

  Docker                              Image for each service

  Docker Compose                      Local environment

  GitHub                              Source control

  GitHub Actions                      Continuous Integration

  JaCoCo                              Code coverage

  Testcontainers                      Integration testing

  WireMock                            Mock Room Service calls

  Trivy                               Container security scanning
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# APIs

Do not build full CRUD for every entity just for practice.

## Room Service APIs

The following endpoints are enough:

``` http
POST /api/rooms

GET /api/rooms

GET /api/rooms/{id}

PATCH /api/rooms/{id}/status

GET /api/rooms/{id}/availability
```

Availability example:

``` http
GET /api/rooms/15/availability
    ?start=2026-09-01T10:00:00
    &end=2026-09-01T11:00:00
```

Example response:

``` json
{
  "roomId": 15,
  "available": true
}
```

Authorization:

``` text
POST /api/rooms
ADMIN only

PATCH /api/rooms/{id}/status
ADMIN only
```

The following endpoints can be used by both `USER` and `ADMIN`:

``` text
GET rooms
GET room
GET availability
```

------------------------------------------------------------------------

# Booking Service APIs

Use:

``` http
POST /api/bookings

GET /api/bookings/me

GET /api/bookings/{id}

DELETE /api/bookings/{id}
```

For administrators:

``` http
GET /api/bookings
```

This allows an administrator to see all bookings.

Treat:

``` http
DELETE /api/bookings/{id}
```

as cancellation rather than a physical database delete.

Update:

``` text
status = CANCELLED
```

instead of deleting the record.

------------------------------------------------------------------------

# Core Business Rules

These rules are among the most important parts of the project.

The project should not become nothing more than:

``` java
repository.save(entity);
```

## Rule 1 --- Valid Time Range

``` text
startTime < endTime
```

Invalid example:

``` text
Start: 12:00
End:   11:00
```

## Rule 2 --- No Booking in the Past

``` text
startTime > currentTime
```

## Rule 3 --- Maximum Meeting Duration

Meeting duration must be:

``` text
<= 4 Hours
```

Examples:

``` text
10:00 → 13:00 ✅
10:00 → 15:30 ❌
```

## Rule 4 --- Active Room

The room must be:

``` text
ACTIVE
```

If it is:

``` text
INACTIVE
```

the booking must be rejected.

## Rule 5 --- No Overlapping Bookings

This is the most important booking rule.

Existing booking:

``` text
10:00 ─────────────── 11:00
```

New request:

``` text
10:30 ─────────────── 11:30

        ❌ Conflict
```

Examples:

``` text
Existing:
10:00 ───────── 11:00

Requests:
09:00 ── 10:00      ✅
09:30 ───── 10:30   ❌
10:00 ───── 11:00   ❌
10:30 ───── 11:30   ❌
11:00 ───── 12:00   ✅
```

This gives you a real business query instead of simple CRUD.

## Rule 6 --- Booking Ownership

A user can cancel:

``` text
Only their own booking
```

User A must not be allowed to cancel User B's booking.

## Rule 7 --- Booking Visibility

An `ADMIN` can:

``` text
See all bookings
```

A `USER` can see:

``` text
Only their own bookings
```

------------------------------------------------------------------------

# Security

Use:

``` text
Keycloak
```

Do not create:

``` text
User Service
```

Do not build an authentication system from scratch.

Use only two roles:

``` text
ROLE_USER
ROLE_ADMIN
```

## USER Permissions

``` text
View rooms
Search rooms
Check availability
Create booking
See own bookings
Cancel own booking
```

## ADMIN Permissions

``` text
Add Room
Disable Room
Enable Room
View Rooms
View All Bookings
```

## Authentication Flow

``` text
User
  │
  ▼
Keycloak
  │
  │ Login
  ▼
JWT Access Token
  │
  ▼
API Gateway
  │
  │ Validate JWT
  ▼
Microservices
```

The services must also validate JWTs themselves. Do not rely only on
gateway security.

Use:

``` text
Gateway
   +
Services
```

as resource servers.

This covers:

``` text
OAuth2
OpenID Connect
JWT
Authentication
Authorization
Spring Security
```

without building a custom authentication system.

------------------------------------------------------------------------

# Resilience4j

A critical synchronous dependency is:

``` text
Booking Service
      │
      │ OpenFeign
      ▼
Room Service
```

If the Room Service fails, the Booking Service must not wait
indefinitely.

Use:

``` text
Timeout
Retry
Circuit Breaker
Bulkhead
```

## Timeout

If the Room Service does not respond within the configured time:

``` text
Fail Fast
```

## Retry

For a temporary failure:

``` text
Booking
   │
   ▼
Room Request
   │
   X
   │
 Retry
   │
   ▼
Room Request
```

Do not retry dozens of times.

## Circuit Breaker

Stop the Room Service during testing and observe:

``` text
CLOSED
   │
   │ failures
   ▼
OPEN
   │
   │ wait duration
   ▼
HALF_OPEN
   │
   │ success
   ▼
CLOSED
```

If the Room Service is unavailable, the Booking Service can return:

``` http
503 Service Unavailable
```

``` json
{
  "code": "ROOM_SERVICE_UNAVAILABLE",
  "message": "Room availability cannot be checked currently"
}
```

## Bulkhead

Apply a bulkhead to:

``` text
Booking → Room
```

If the Room Service becomes slow or unstable, its calls should not
consume all Booking Service resources.

------------------------------------------------------------------------

# Redis Rate Limiting

Apply rate limiting at:

``` text
API Gateway
```

especially for:

``` http
POST /api/bookings
```

For example:

``` text
10 booking requests / minute / user
```

The exact limit can be adjusted.

Flow:

``` text
User
 │
 ▼
Gateway
 │
 ▼
Redis Rate Limiter
 │
 ├── Allowed → Booking Service
 │
 └── Exceeded → 429 Too Many Requests
```

------------------------------------------------------------------------

# Kafka

Do not use Kafka for everything.

Use one topic such as:

``` text
booking-events
```

Main events:

``` text
BookingCreatedEvent
BookingCancelledEvent
```

## BookingCreatedEvent

Example:

``` json
{
  "eventId": "9496c45f...",
  "eventType": "BOOKING_CREATED",
  "bookingId": 812,
  "userId": "c65c...",
  "roomId": 15,
  "startTime": "2026-09-01T10:00:00",
  "endTime": "2026-09-01T11:00:00",
  "occurredAt": "2026-08-29T12:30:00"
}
```

## BookingCancelledEvent

Example:

``` json
{
  "eventId": "1c6a...",
  "eventType": "BOOKING_CANCELLED",
  "bookingId": 812,
  "userId": "c65c...",
  "roomId": 15,
  "occurredAt": "2026-08-29T14:00:00"
}
```

Notification Service:

``` text
Kafka Consumer
       │
       ▼
booking-events
       │
       ├── BookingCreated
       └── BookingCancelled
```

This makes the difference clear:

``` text
Feign
Synchronous
Request / Response
```

versus:

``` text
Kafka
Asynchronous
Event Driven
```

------------------------------------------------------------------------

# Testing Strategy

Testing is important because it becomes the foundation of the CI
pipeline.

Use three main levels.

## Unit Tests

Focus on business logic, especially in Booking Service.

Test:

``` text
startTime before endTime
cannot book in past
duration <= 4 hours
inactive room cannot be booked
user can cancel own booking
user cannot cancel another user's booking
```

Test all overlap cases.

## Repository / Integration Tests

Use:

``` text
Testcontainers
```

with PostgreSQL.

Prefer this over:

``` text
H2
```

because integration tests should run against a real PostgreSQL instance
inside a container.

``` text
JUnit
  │
  ▼
Testcontainers
  │
  ▼
PostgreSQL Container
```

Test:

``` text
Repository Queries
Overlap Query
REST + Database Integration
```

## Feign Integration Testing

Booking Service depends on Room Service, but the real Room Service does
not need to run in every integration test.

Use:

``` text
WireMock
```

Flow:

``` text
Booking Service
      │
      │ HTTP
      ▼
WireMock
      │
      └── Mock Room Service
```

One test can return:

``` json
{
  "available": true
}
```

and verify:

``` text
Booking created successfully
```

Another can return:

``` json
{
  "available": false
}
```

and verify that Booking Service returns:

``` text
409 Conflict
```

## Kafka Integration Testing

When Kafka is introduced, use:

``` text
Kafka Testcontainer
```

Test:

``` text
Create Booking
     │
     ▼
Booking Saved
     │
     ▼
BookingCreatedEvent
     │
     ▼
Kafka Topic
```

Verify:

``` text
event published successfully
```

In Notification Service, verify that the consumer receives the event.

------------------------------------------------------------------------

# Continuous Integration --- CI

Do not wait until the end of the project to add CI.

As soon as you have:

``` text
Room Service
Booking Service
Tests
```

add GitHub Actions.

Every:

``` text
Push
or
Pull Request
```

should automatically verify that the project is healthy.

Example pipeline:

``` text
Developer
    │
    ▼
Git Push
    │
    ▼
GitHub
    │
    ▼
GitHub Actions
    │
    ├── Checkout
    ├── Setup Java
    ├── Compile
    ├── Unit Tests
    ├── Integration Tests
    ├── JaCoCo
    ├── Package
    └── Quality Checks
    │
    ▼
CI PASS ✅
```

If any step fails:

``` text
CI FAIL ❌
```

and merging should be blocked.

## CI Command

The main project verification command should be:

``` bash
./mvnw clean verify
```

not only:

``` bash
mvn test
```

The desired lifecycle is approximately:

``` text
compile
   ↓
unit tests
   ↓
package
   ↓
integration tests
   ↓
verify
```

Use the same command:

``` text
Locally
   +
GitHub Actions
```

------------------------------------------------------------------------

# Monorepo Structure

A monorepo is a good fit for this learning project:

``` text
meeting-room-booking/
│
├── room-service/
│   ├── src/
│   ├── pom.xml
│   ├── mvnw
│   └── Dockerfile
│
├── booking-service/
│   ├── src/
│   ├── pom.xml
│   ├── mvnw
│   └── Dockerfile
│
├── notification-service/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── api-gateway/
│
├── config-server/
│
├── discovery-server/
│
├── docker/
│   └── docker-compose.yml
│
├── observability/
│   ├── prometheus/
│   ├── loki/
│   ├── tempo/
│   └── grafana/
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── docker.yml
│
└── README.md
```

Separate repositories for every microservice are unnecessary here and
would add overhead without much learning value.

------------------------------------------------------------------------

# GitHub Actions CI

Start with a simple pipeline:

``` text
Push / PR
   │
   ▼
Build Services
   │
   ▼
Tests
   │
   ▼
Verify
```

As the number of services grows, use a:

``` text
Matrix Strategy
```

Example:

``` text
                 GitHub Actions
                       │
           ┌───────────┼────────────┐
           ▼           ▼            ▼
       Room CI     Booking CI   Gateway CI
           │           │            │
        verify       verify       verify
           │           │            │
           └───────────┼────────────┘
                       ▼
                    CI PASS
```

After adding Notification Service, verify:

``` text
Room
Booking
Notification
Gateway
Config Server
Discovery Server
```

independently where appropriate.

------------------------------------------------------------------------

# JaCoCo

Add:

``` text
JaCoCo
```

for code coverage.

Do not turn the project into a competition for:

``` text
100% Coverage
```

Prioritize:

``` text
Booking Business Rules
Overlap Logic
Cancellation Authorization
Room Availability
Kafka Event Publishing
```

You can use a threshold such as:

``` text
70%
```

so:

``` text
Coverage >= 70%
       │
       ▼
CI PASS
```

Otherwise:

``` text
CI FAIL
```

------------------------------------------------------------------------

# Docker in CI

When the project reaches the Docker phase, extend CI.

Instead of:

``` text
Build
Tests
Verify
```

use:

``` text
Build
   ↓
Tests
   ↓
Verify
   ↓
Docker Image Build
```

The initial goal is to verify:

``` text
Dockerfile works
Image builds successfully
Application packages correctly
```

Pushing images is not required from the first day.

------------------------------------------------------------------------

# Security Scan

After Docker image creation, add:

``` text
Trivy
```

Example:

``` text
Docker Image
    │
    ▼
Trivy Scan
    │
    ├── LOW
    ├── MEDIUM
    ├── HIGH
    └── CRITICAL
```

You can configure the pipeline to fail only for:

``` text
HIGH
or
CRITICAL
```

This adds a useful DevSecOps practice without overcomplicating the
project.

------------------------------------------------------------------------

# Docker Image Tags

Do not use only:

``` text
latest
```

Prefer the Git commit SHA.

Example:

``` text
room-service:8d65caa
booking-service:8d65caa
notification-service:8d65caa
```

This gives traceability:

``` text
Git Commit
     ↓
Docker Image
```

------------------------------------------------------------------------

# Pull Request Workflow

Use branches such as:

``` text
main
develop
feature/*
```

Example:

``` text
feature/booking-overlap-validation
```

Flow:

``` text
Developer
    │
    ▼
feature branch
    │
    ▼
Pull Request
    │
    ▼
GitHub Actions
    │
    ├── Tests
    ├── Integration Tests
    ├── Coverage
    ├── Build
    └── Security Checks
    │
    ▼
PASS
    │
    ▼
Merge
```

Enable branch protection on:

``` text
main
```

Rules:

``` text
❌ Direct Push
✅ Pull Request
✅ CI must pass
✅ Then Merge
```

This demonstrates that CI is genuinely part of the development process
rather than just a YAML file stored in the repository.

------------------------------------------------------------------------

# Config Server

Add:

``` text
Spring Cloud Config Server
```

for:

``` text
Centralized Configuration
```

For example:

``` text
room-service.yml
booking-service.yml
notification-service.yml
api-gateway.yml
```

This avoids hardcoding all configuration inside individual services.

------------------------------------------------------------------------

# Eureka

Use:

``` text
Eureka Server
```

Services register themselves as:

``` text
ROOM-SERVICE
BOOKING-SERVICE
NOTIFICATION-SERVICE
API-GATEWAY
```

Booking Service should not need to know a fixed address such as:

``` text
http://localhost:8081
```

It should use service discovery instead.

------------------------------------------------------------------------

# API Gateway

All external requests should enter through:

``` text
API Gateway
```

not:

``` text
Client → Room Service directly
```

Gateway responsibilities:

``` text
Routing
Authentication
Rate Limiting
Correlation ID
Logging
```

------------------------------------------------------------------------

# Correlation ID

Add a gateway filter that creates:

``` text
X-Correlation-Id
```

Example:

``` text
8f97a3a...
```

The same identifier should follow the request through:

``` text
Gateway
   ↓
Booking
   ↓
Room
```

It should also appear in:

``` text
Logs
Tracing
```

This is especially useful for observability.

------------------------------------------------------------------------

# Docker

Every Spring application should have a:

``` text
Dockerfile
```

including:

``` text
room-service
booking-service
notification-service
gateway
config-server
discovery-server
```

Each application should produce an independent Docker image.

------------------------------------------------------------------------

# Docker Compose

Create a complete local environment with:

``` text
docker-compose.yml
```

It should run:

``` text
PostgreSQL Room DB
PostgreSQL Booking DB
Redis
Kafka
Keycloak
Config Server
Eureka Server
Room Service
Booking Service
Notification Service
API Gateway
```

Then add observability components:

``` text
Prometheus
Loki
Tempo
Grafana
```

The goal is to start the entire local system with one command.

------------------------------------------------------------------------

# Observability

Use:

``` text
Spring Boot Actuator
Micrometer
Prometheus
Loki
Tempo
Grafana
```

## Metrics

Actuator and Micrometer expose metrics.

Prometheus collects them.

Create one useful Grafana dashboard containing metrics such as:

``` text
Request Count
Request Duration
Error Rate
JVM Memory
CPU
HTTP Status Codes
```

## Logging

Use structured logs where practical.

Include:

``` text
serviceName
timestamp
level
correlationId
traceId
message
```

Loki collects the logs and Grafana displays them.

## Distributed Tracing

Use Tempo to trace a request such as:

``` http
POST /api/bookings
```

across:

``` text
Gateway
   ↓
Booking Service
   ↓
Room Service
```

This allows you to inspect the complete distributed trace.

## Main Observability Scenario

Send a booking request, then use Grafana to inspect:

``` text
HTTP Request
       │
       ▼
Gateway Span
       │
       ▼
Booking Span
       │
       ▼
Feign Call
       │
       ▼
Room Span
```

Connect:

``` text
Metrics
Logs
Traces
```

to understand the request end to end.

------------------------------------------------------------------------

# Final Runtime Architecture

``` text
                           Keycloak
                              │
                              │ JWT
                              ▼
Client ───────────────────► API Gateway
                              │
                      ┌───────┴─────────┐
                      │                 │
                      ▼                 ▼
                 Room Service ◄──── Booking Service
                                        │
                                        │ Kafka
                                        ▼
                              Notification Service
```

Infrastructure:

``` text
Config Server
Eureka
Redis
Kafka
PostgreSQL
Keycloak
```

Observability:

``` text
Actuator
   │
   ▼
Prometheus ──────► Grafana

Logs
 │
 ▼
Loki ────────────► Grafana

Tracing
  │
  ▼
Tempo ───────────► Grafana
```

Development pipeline:

``` text
Developer
   │
   ▼
Git
   │
   ▼
GitHub
   │
   ▼
Pull Request
   │
   ▼
GitHub Actions
   │
   ├── Build
   ├── Unit Tests
   ├── Integration Tests
   ├── Testcontainers
   ├── JaCoCo
   ├── Docker Build
   └── Trivy
   │
   ▼
Merge
```

------------------------------------------------------------------------

# Rules to Prevent Overengineering

Keep these constraints:

``` text
❌ No Frontend initially
❌ No Payment
❌ No User Service
❌ No Custom Authentication System
❌ No Real Email
❌ No 8+ Microservices
❌ No Full CRUD for every entity
❌ No Shared Database
❌ No Kafka for every communication
❌ No unnecessary DevOps tools before useful tests exist
❌ No complicated multi-environment setup
```

In return, focus on:

``` text
✅ 3 Business Services
✅ Database per Service
✅ REST APIs
✅ Business Rules
✅ Validation
✅ Sync Communication
✅ Async Communication
✅ OAuth2 / OIDC
✅ JWT Security
✅ Resilience
✅ Rate Limiting
✅ Unit Testing
✅ Integration Testing
✅ Testcontainers
✅ CI
✅ Code Coverage
✅ Docker Image Validation
✅ Security Scanning
✅ Observability
✅ Containers
```

This is a strong scope for the project.

------------------------------------------------------------------------

# Project Implementation Order

Do not introduce every technology at once.

Build the project in layers.

## Phase 1 --- Core Business

Start only with:

``` text
Room Service
Booking Service
PostgreSQL
REST APIs
DTOs
Validation
Exception Handling
Spring Data JPA
Auditing
OpenAPI
```

Implement the business rules:

``` text
Time validation
Max duration
Active room
Overlapping bookings
Ownership cancellation
```

At the end of this phase, you should have:

``` text
Room Service
      +
Booking Service
      +
PostgreSQL
```

working without complex microservices infrastructure.

------------------------------------------------------------------------

## Phase 2 --- Testing

Before expanding the architecture, add solid testing.

Use:

``` text
JUnit
Mockito
Spring Boot Integration Tests
Testcontainers PostgreSQL
WireMock
```

Test:

``` text
Booking Rules
Overlap Logic
Room Availability
Repositories
REST Controllers
Feign scenarios
```

Goal:

``` text
./mvnw clean verify
```

must run locally without problems.

------------------------------------------------------------------------

## Phase 3 --- Continuous Integration

Add:

``` text
GitHub
GitHub Actions
Pull Requests
Branch Protection
JaCoCo
```

Pipeline:

``` text
Push / Pull Request
       │
       ▼
Compile
       │
       ▼
Unit Tests
       │
       ▼
Integration Tests
       │
       ▼
JaCoCo
       │
       ▼
Package
       │
       ▼
Verify
```

Do not allow:

``` text
merge
```

when CI fails.

From this phase onward, whenever you add a technology, ask:

``` text
How will CI verify this?
```

------------------------------------------------------------------------

## Phase 4 --- Spring Cloud

Add:

``` text
Config Server
Eureka Server
OpenFeign
API Gateway
```

Architecture:

``` text
Client
  │
  ▼
Gateway
  │
  ▼
Booking
  │
  │ Feign
  ▼
Room
```

Services use Eureka for discovery.

------------------------------------------------------------------------

## Phase 5 --- Resilience + Rate Limiting

Add:

``` text
Timeout
Retry
Circuit Breaker
Bulkhead
Redis Rate Limiter
```

Test failure scenarios manually.

Example:

``` text
Stop Room Service
       │
       ▼
Call Booking API
       │
       ▼
Observe Circuit Breaker
```

------------------------------------------------------------------------

## Phase 6 --- Security

Add:

``` text
Keycloak
OAuth2
OIDC
JWT
Spring Security
```

Create:

``` text
ROLE_USER
ROLE_ADMIN
```

Configure endpoint authorization.

Add security integration tests.

CI should now also verify:

``` text
401 Unauthorized
403 Forbidden
USER permissions
ADMIN permissions
```

------------------------------------------------------------------------

## Phase 7 --- Kafka

Add:

``` text
Kafka
BookingCreatedEvent
BookingCancelledEvent
Notification Service
```

Add Kafka integration tests with Testcontainers.

CI should verify:

``` text
Event Publishing
Event Consumption
```

------------------------------------------------------------------------

## Phase 8 --- Docker

Create a:

``` text
Dockerfile
```

for every service.

Then create:

``` text
Docker Compose
```

for the complete local system.

Extend CI:

``` text
Tests
   ↓
Docker Build
```

If a Dockerfile is broken:

``` text
CI FAIL
```

------------------------------------------------------------------------

## Phase 9 --- Security Scanning

Add:

``` text
Trivy
```

after the Docker build.

Pipeline:

``` text
Build
   ↓
Test
   ↓
Docker Image
   ↓
Trivy Scan
```

------------------------------------------------------------------------

## Phase 10 --- Observability

Add:

``` text
Actuator
Micrometer
Prometheus
Loki
Tempo
Grafana
```

Build one useful dashboard instead of many unnecessary dashboards.

Monitor:

``` text
Requests
Latency
Errors
JVM
Logs
Distributed Traces
```

------------------------------------------------------------------------

# Final Short Roadmap

``` text
Phase 1
Core Business
Room + Booking + PostgreSQL
REST + JPA + Validation + OpenAPI

            ↓

Phase 2
Testing
JUnit + Mockito
Testcontainers
WireMock

            ↓

Phase 3
CI
GitHub Actions
mvn clean verify
JaCoCo
Pull Requests
Branch Protection

            ↓

Phase 4
Spring Cloud
Eureka
Config Server
OpenFeign
API Gateway

            ↓

Phase 5
Resilience
Circuit Breaker
Retry
Timeout
Bulkhead
Redis Rate Limiter

            ↓

Phase 6
Security
Keycloak
OAuth2 / OIDC
JWT
Spring Security

            ↓

Phase 7
Messaging
Kafka
Notification Service

            ↓

Phase 8
Docker
Dockerfiles
Docker Compose
CI Docker Build

            ↓

Phase 9
DevSecOps
Trivy

            ↓

Phase 10
Observability
Prometheus
Loki
Tempo
Grafana
```

------------------------------------------------------------------------

# Final Result

At the end of the project, you will have built a system with:

``` text
3 Business Microservices
API Gateway
Service Discovery
Centralized Configuration
Database per Service
REST Communication
Feign Communication
Kafka Event-Driven Communication
OAuth2 / OIDC
JWT Authentication
Role-Based Authorization
Circuit Breaker
Retry
Timeout
Bulkhead
Redis Rate Limiting
Business Validation
Unit Tests
Integration Tests
Testcontainers
WireMock
Continuous Integration
Pull Request Checks
Code Coverage
Docker Image Validation
Container Security Scanning
Centralized Metrics
Centralized Logging
Distributed Tracing
Docker
Docker Compose
```

The important point is that every technology has a natural use case in
the project.

You should be able to explain every major choice:

``` text
Why Feign?
Because checking room availability needs an immediate response.

Why Kafka?
Because notification does not need to block booking creation.

Why Circuit Breaker?
Because Booking depends synchronously on Room.

Why Redis?
Because the Gateway needs distributed rate limiting.

Why Keycloak?
Because authentication is infrastructure, not the business domain.

Why Testcontainers?
Because integration tests should run against real infrastructure.

Why CI?
Because every change should automatically prove that business rules,
integration tests, packaging, and Docker images are still valid.
```

That is what makes the project valuable after a Microservices course: it
demonstrates intentional architecture and engineering decisions instead
of being a collection of unrelated technologies.
