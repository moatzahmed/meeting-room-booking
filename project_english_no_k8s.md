# 🎯 Meeting Room Booking System

The project is a meeting room booking system for use inside a company.

The company has multiple meeting rooms, and employees can:

* View available rooms.
* Book a room for a specific time.
* View their own bookings.
* Cancel their own bookings.

Administrators can:

* Add rooms.
* Enable or disable rooms.
* View all bookings.

The business domain is intentionally small, but the project is designed to apply the most important concepts in:

* Spring Boot
* Microservices
* Security
* Resilience
* Messaging
* Observability
* Docker
* Continuous Integration

The goal is to use every technology for a real reason instead of adding technologies only for the sake of using them.

---

# Architecture

Use only **3 business microservices**:

```text
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
                                        │ RabbitMQ
                                        ▼
                              ┌────────────────────┐
                              │ Notification Svc   │
                              │                    │
                              │ RabbitMQ Consumer  │
                              │ Mock Notification  │
                              └────────────────────┘
```

Supporting infrastructure:

```text
Config Server
Eureka Server
Redis
RabbitMQ
Keycloak

Prometheus
Loki
Tempo
Grafana
```

Development and CI tooling:

```text
GitHub
GitHub Actions
Docker Registry / GHCR
Docker
Docker Compose
```

---

# Business Services

## 1. Room Service

Responsibilities:

```text
Rooms
Room Status
Room Information
Availability
```

A room can contain:

```text
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

```text
ACTIVE
INACTIVE
```

---

# 2. Booking Service

This is the most important service in the project.

Responsibilities:

```text
Reservations
Booking Business Rules
Room Availability Check
Booking Cancellation
RabbitMQ Event Publishing
```

A booking can contain:

```text
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

```text
CONFIRMED
CANCELLED
```

---

# 3. Notification Service

This is a very small service.

It does not need a public CRUD API.

It is simply a RabbitMQ consumer that listens for:

```text
BookingCreatedEvent
BookingCancelledEvent
```

Initially, it can only log a notification:

```text
Booking confirmed for user c65c...
Room: 15
Time: 10:00 - 11:00
```

Storing notifications in a small database is optional.

The following are intentionally out of scope:

```text
SendGrid
SMTP
Twilio
Real Email
SMS
```

They do not add enough value to the main learning goals of this project.

---

# Database Architecture

Use:

```text
Database per Service
```

For example:

```text
Room Service
     │
     ▼
room_db
PostgreSQL
```

And:

```text
Booking Service
     │
     ▼
booking_db
PostgreSQL
```

Do not use:

```text
Room Service ─┐
              ├── Shared Database ❌
Booking Svc ──┘
```

Each service owns its own data.

---

# Main Booking Flow

The user sends:

```text
POST /api/bookings
```

Request body:

```json
{
  "roomId": 15,
  "startTime": "2026-09-10T10:00:00",
  "endTime": "2026-09-10T11:00:00",
  "purpose": "Backend Team Meeting"
}
```

The request enters through:

```text
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

```text
userId
```

from the JWT.

It then makes a synchronous call using OpenFeign:

```text
Booking Service
      │
      │ OpenFeign
      ▼
Room Service
```

The request asks:

```text
Is room 15 available
between
10:00 and 11:00?
```

The Room Service validates:

```text
Room exists?
     ↓
Room ACTIVE?
     ↓
No conflicting reservation?
     ↓
available = true
```

If the room is available:

```text
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
      booking.exchange
              │
              │ booking.created
              ▼
 notification.booking.queue
              │
              ▼
     Notification Service
              │
              ▼
      "Booking confirmed"
```

This applies two communication styles for clear reasons.

Synchronous communication:

```text
Booking → Room
Synchronous
OpenFeign
Request / Response
```

Asynchronous communication:

```text
Booking → Notification
Asynchronous
RabbitMQ
Event Driven
```

This is better than using RabbitMQ for every interaction simply because it is available.

---

# Technology Map

| Technology                   | Purpose                                                 |
| ---------------------------- | ------------------------------------------------------- |
| Spring Boot                  | All services                                            |
| REST API                     | Room and Booking APIs                                   |
| DTO                          | Requests and responses                                  |
| Bean Validation              | Validate booking times and request fields               |
| Exception Handling           | Room unavailable, booking not found, and similar errors |
| Spring Data JPA              | Room and Booking persistence                            |
| PostgreSQL                   | Database per service                                    |
| Auditing                     | `createdAt`, `createdBy`, and related fields            |
| OpenAPI / Swagger            | API documentation                                       |
| Config Server                | Centralized configuration                               |
| Eureka                       | Service discovery                                       |
| OpenFeign                    | Booking → Room communication                            |
| API Gateway                  | Single entry point                                      |
| Gateway Filters              | Logging and Correlation ID                              |
| Resilience4j Circuit Breaker | Protect Booking → Room calls                            |
| Retry                        | Handle temporary Room Service failures                  |
| Timeout                      | Prevent indefinite waiting                              |
| Bulkhead                     | Isolate Room Service calls                              |
| Redis Rate Limiter           | Gateway rate limiting                                   |
| OAuth2 / OIDC                | Authentication through Keycloak                         |
| Spring Security              | JWT resource servers and authorization                  |
| RabbitMQ                     | Booking Created / Cancelled events                      |
| Spring AMQP                  | RabbitMQ integration in Spring Boot                     |
| Actuator                     | Health and metrics endpoints                            |
| Micrometer                   | Application metrics                                     |
| Prometheus                   | Metrics collection                                      |
| Loki                         | Centralized logs                                        |
| Tempo                        | Distributed tracing                                     |
| Grafana                      | Dashboards                                              |
| Docker                       | Image for each service                                  |
| Docker Compose               | Local environment                                       |
| GitHub                       | Source control                                          |
| GitHub Actions               | Continuous Integration                                  |
| JaCoCo                       | Code coverage                                           |
| Testcontainers               | Integration testing                                     |
| WireMock                     | Mock Room Service calls                                 |
| Trivy                        | Container security scanning                             |

---

# APIs

Do not build full CRUD for every entity just for practice.

## Room Service APIs

The following endpoints are enough:

```text
POST /api/rooms

GET /api/rooms

GET /api/rooms/{id}

PATCH /api/rooms/{id}/status

GET /api/rooms/{id}/availability
```

Availability example:

```text
GET /api/rooms/15/availability
    ?start=2026-09-10T10:00:00
    &end=2026-09-10T11:00:00
```

Example response:

```json
{
  "roomId": 15,
  "available": true
}
```

Authorization:

```text
POST /api/rooms
ADMIN only

PATCH /api/rooms/{id}/status
ADMIN only
```

The following endpoints can be used by both `USER` and `ADMIN`:

```text
GET rooms
GET room
GET availability
```

---

# Booking Service APIs

Use:

```text
POST /api/bookings

GET /api/bookings/me

GET /api/bookings/{id}

DELETE /api/bookings/{id}
```

For administrators:

```text
GET /api/bookings
```

This allows an administrator to see all bookings.

Treat:

```text
DELETE /api/bookings/{id}
```

as cancellation rather than a physical database delete.

Update:

```text
status = CANCELLED
```

instead of deleting the record.

---

# Core Business Rules

These rules are among the most important parts of the project.

The project should not become nothing more than:

```java
repository.save(entity);
```

## Rule 1 — Valid Time Range

```text
startTime < endTime
```

Invalid example:

```text
Start: 12:00
End:   11:00
```

## Rule 2 — No Booking in the Past

```text
startTime > currentTime
```

## Rule 3 — Maximum Meeting Duration

Meeting duration must be:

```text
<= 4 Hours
```

Examples:

```text
10:00 → 13:00 ✅
10:00 → 15:30 ❌
```

## Rule 4 — Active Room

The room must be:

```text
ACTIVE
```

If it is:

```text
INACTIVE
```

the booking must be rejected.

## Rule 5 — No Overlapping Bookings

This is the most important booking rule.

Existing booking:

```text
10:00 ─────────────── 11:00
```

New request:

```text
10:30 ─────────────── 11:30

        ❌ Conflict
```

Examples:

```text
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

## Rule 6 — Booking Ownership

A user can cancel:

```text
Only their own booking
```

User A must not be allowed to cancel User B’s booking.

## Rule 7 — Booking Visibility

An `ADMIN` can:

```text
See all bookings
```

A `USER` can see:

```text
Only their own bookings
```

---

# Security

Use:

```text
Keycloak
```

Do not create:

```text
User Service
```

Do not build an authentication system from scratch.

Use only two roles:

```text
ROLE_USER
ROLE_ADMIN
```

## USER Permissions

```text
View rooms
Search rooms
Check availability
Create booking
See own bookings
Cancel own booking
```

## ADMIN Permissions

```text
Add Room
Disable Room
Enable Room
View Rooms
View All Bookings
```

## Authentication Flow

```text
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

The services must also validate JWTs themselves. Do not rely only on gateway security.

Use:

```text
Gateway
   +
Services
```

as resource servers.

This covers:

```text
OAuth2
OpenID Connect
JWT
Authentication
Authorization
Spring Security
```

without building a custom authentication system.

---

# Resilience4j

A critical synchronous dependency is:

```text
Booking Service
      │
      │ OpenFeign
      ▼
Room Service
```

If the Room Service fails, the Booking Service must not wait indefinitely.

Use:

```text
Timeout
Retry
Circuit Breaker
Bulkhead
```

## Timeout

If the Room Service does not respond within the configured time:

```text
Fail Fast
```

## Retry

For a temporary failure:

```text
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

```text
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

```text
503 Service Unavailable
```

```json
{
  "code": "ROOM_SERVICE_UNAVAILABLE",
  "message": "Room availability cannot be checked currently"
}
```

## Bulkhead

Apply a bulkhead to:

```text
Booking → Room
```

If the Room Service becomes slow or unstable, its calls should not consume all Booking Service resources.

---

# Redis Rate Limiting

Apply rate limiting at:

```text
API Gateway
```

especially for:

```text
POST /api/bookings
```

For example:

```text
10 booking requests / minute / user
```

The exact limit can be adjusted.

Flow:

```text
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

---

# RabbitMQ

Do not use RabbitMQ for everything.

Use RabbitMQ specifically for communication where the producer does not need an immediate response from the consumer.

For this project:

```text
Booking Service
      │
      ▼
RabbitMQ
      │
      ▼
Notification Service
```

Use one topic exchange:

```text
booking.exchange
```

Exchange type:

```text
topic
```

Main routing keys:

```text
booking.created
booking.cancelled
```

Use a notification queue:

```text
notification.booking.queue
```

Bindings:

```text
booking.exchange
      │
      ├── booking.created
      │          │
      │          ▼
      │   notification.booking.queue
      │
      └── booking.cancelled
                 │
                 ▼
          notification.booking.queue
```

This lets the Notification Service consume both event types from one queue.

---

## RabbitMQ Topology

```text
                   Booking Service
                         │
                         │ publish event
                         ▼
                  booking.exchange
                    type: topic
                         │
             ┌───────────┴────────────┐
             │                        │
             │ booking.created        │ booking.cancelled
             │                        │
             └───────────┬────────────┘
                         ▼
              notification.booking.queue
                         │
                         ▼
               Notification Service
```

The exchange routes messages.

The queue stores messages until they are consumed.

The routing key determines how a message is routed from the exchange to the queue.

---

# BookingCreatedEvent

Example:

```json
{
  "eventId": "9496c45f...",
  "eventType": "BOOKING_CREATED",
  "bookingId": 812,
  "userId": "c65c...",
  "roomId": 15,
  "startTime": "2026-09-10T10:00:00",
  "endTime": "2026-09-10T11:00:00",
  "occurredAt": "2026-09-04T12:30:00"
}
```

Publish using routing key:

```text
booking.created
```

Flow:

```text
Booking Service
      │
      ▼
BookingCreatedEvent
      │
      ▼
booking.exchange
      │
      │ routing key = booking.created
      ▼
notification.booking.queue
      │
      ▼
Notification Service
```

---

# BookingCancelledEvent

Example:

```json
{
  "eventId": "1c6a...",
  "eventType": "BOOKING_CANCELLED",
  "bookingId": 812,
  "userId": "c65c...",
  "roomId": 15,
  "occurredAt": "2026-09-04T14:00:00"
}
```

Publish using:

```text
booking.cancelled
```

Flow:

```text
Booking Service
      │
      ▼
BookingCancelledEvent
      │
      ▼
booking.exchange
      │
      │ routing key = booking.cancelled
      ▼
notification.booking.queue
      │
      ▼
Notification Service
```

---

# RabbitMQ Producer

Booking Service is the producer.

Use:

```text
Spring AMQP
RabbitTemplate
```

Conceptually:

```java
rabbitTemplate.convertAndSend(
        "booking.exchange",
        "booking.created",
        bookingCreatedEvent
);
```

For cancellation:

```java
rabbitTemplate.convertAndSend(
        "booking.exchange",
        "booking.cancelled",
        bookingCancelledEvent
);
```

The controller should not publish directly.

Prefer:

```text
Controller
    │
    ▼
Booking Service
    │
    ├── Business Logic
    ├── Save Booking
    └── Event Publisher
             │
             ▼
          RabbitMQ
```

---

# RabbitMQ Consumer

Notification Service consumes events.

Use:

```text
@RabbitListener
```

Conceptually:

```java
@RabbitListener(queues = "notification.booking.queue")
public void consumeBookingEvent(BookingEvent event) {
    // process notification
}
```

The consumer can inspect:

```text
eventType
```

and decide whether the event represents:

```text
BOOKING_CREATED
BOOKING_CANCELLED
```

Alternatively, separate queues or listeners can be introduced later if necessary.

For this project, one notification queue is enough.

---

# Durable Messaging

Configure:

```text
Durable Exchange
Durable Queue
Persistent Messages
```

The goal is that temporary service restarts should not automatically cause notifications to disappear.

Conceptually:

```text
Booking Service
      │
      ▼
RabbitMQ
      │
      │ Notification Service temporarily stopped
      │
      ▼
Message remains in queue
      │
      │ Notification Service starts again
      ▼
Message consumed
```

This demonstrates an important benefit of asynchronous messaging.

---

# Message Acknowledgement

The Notification Service should acknowledge a message after it has been processed successfully.

Conceptually:

```text
RabbitMQ
   │
   ▼
Notification Service
   │
   ├── Processing succeeds
   │        ↓
   │       ACK
   │
   └── Processing fails
            ↓
          Retry / Reject
```

Do not acknowledge a message before important processing has completed.

---

# RabbitMQ Retry

Temporary consumer failures can happen.

For example:

```text
Notification Service
       │
       ▼
Process Message
       │
       X
       │
      Retry
       │
       ▼
Process Again
```

Retries must be bounded.

Do not retry forever.

Example strategy:

```text
Initial attempt
     ↓
Retry 1
     ↓
Retry 2
     ↓
Retry 3
     ↓
Dead Letter Queue
```

---

# Dead Letter Queue

Add a dead-letter queue for messages that cannot be processed successfully.

Use:

```text
notification.booking.dlq
```

Conceptual topology:

```text
Booking Service
      │
      ▼
booking.exchange
      │
      ▼
notification.booking.queue
      │
      ▼
Notification Service
      │
      ├── Success → ACK
      │
      └── Repeated Failure
                   │
                   ▼
         notification.booking.dlq
```

The DLQ prevents repeatedly failing messages from blocking normal processing.

It also gives you a realistic messaging failure scenario to inspect during development.

---

# Idempotent Consumers

RabbitMQ can redeliver a message in some failure scenarios.

Therefore Notification Service should be designed so that processing the same event twice does not create harmful duplicate behavior.

Each event already contains:

```text
eventId
```

The Notification Service can optionally track processed event IDs.

Conceptually:

```text
Receive event
     │
     ▼
eventId already processed?
     │
 ┌───┴────┐
 │        │
Yes       No
 │        │
 ▼        ▼
ACK     Process
          │
          ▼
       Store eventId
          │
          ▼
         ACK
```

For the initial project, logging the notification makes duplicates harmless.

If notification persistence is introduced later, idempotency becomes more important.

---

# Messaging Communication Difference

This project deliberately demonstrates two communication models.

## Synchronous

```text
Booking → Room
OpenFeign
Request / Response
Immediate result required
```

Booking cannot continue without knowing whether the room is available.

## Asynchronous

```text
Booking → RabbitMQ → Notification
Event Driven
No immediate response required
```

Booking creation should not wait for notification processing.

This is the architectural reason RabbitMQ exists in this project.

---

# Testing Strategy

Testing is important because it becomes the foundation of the CI pipeline.

Use three main levels.

## Unit Tests

Focus on business logic, especially in Booking Service.

Test:

```text
startTime before endTime
cannot book in past
duration <= 4 hours
inactive room cannot be booked
user can cancel own booking
user cannot cancel another user's booking
```

Test all overlap cases.

---

# Repository / Integration Tests

Use:

```text
Testcontainers
```

with PostgreSQL.

Prefer this over:

```text
H2
```

because integration tests should run against a real PostgreSQL instance inside a container.

```text
JUnit
  │
  ▼
Testcontainers
  │
  ▼
PostgreSQL Container
```

Test:

```text
Repository Queries
Overlap Query
REST + Database Integration
```

---

# Feign Integration Testing

Booking Service depends on Room Service, but the real Room Service does not need to run in every integration test.

Use:

```text
WireMock
```

Flow:

```text
Booking Service
      │
      │ HTTP
      ▼
WireMock
      │
      └── Mock Room Service
```

One test can return:

```json
{
  "available": true
}
```

and verify:

```text
Booking created successfully
```

Another can return:

```json
{
  "available": false
}
```

and verify that Booking Service returns:

```text
409 Conflict
```

---

# RabbitMQ Integration Testing

When RabbitMQ is introduced, use:

```text
RabbitMQ Testcontainer
```

Test:

```text
Create Booking
     │
     ▼
Booking Saved
     │
     ▼
BookingCreatedEvent
     │
     ▼
RabbitMQ Exchange
     │
     ▼
RabbitMQ Queue
```

Verify:

```text
event published successfully
```

In Notification Service, verify that the consumer receives the event.

Example:

```text
JUnit
   │
   ▼
RabbitMQ Testcontainer
   │
   ▼
Start Booking Service
   │
   ▼
Publish BookingCreatedEvent
   │
   ▼
notification.booking.queue
   │
   ▼
Notification Consumer
   │
   ▼
Assertion
```

Also test:

```text
BookingCreatedEvent
BookingCancelledEvent
Correct routing key
Correct queue binding
Consumer receives message
```

Optionally test:

```text
Repeated failure
      ↓
Dead Letter Queue
```

---

# Continuous Integration — CI

Do not wait until the end of the project to add CI.

As soon as you have:

```text
Room Service
Booking Service
Tests
```

add GitHub Actions.

Every:

```text
Push
or
Pull Request
```

should automatically verify that the project is healthy.

Example pipeline:

```text
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

```text
CI FAIL ❌
```

and merging should be blocked.

---

# CI Command

The main project verification command should be:

```bash
./mvnw clean verify
```

not only:

```bash
mvn test
```

The desired lifecycle is approximately:

```text
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

```text
Locally
   +
GitHub Actions
```

---

# Monorepo Structure

A monorepo is a good fit for this learning project:

```text
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

Separate repositories for every microservice are unnecessary here and would add overhead without much learning value.

---

# GitHub Actions CI

Start with a simple pipeline:

```text
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

```text
Matrix Strategy
```

Example:

```text
                 GitHub Actions
                       │
            ┌──────────┼────────────┐
            ▼          ▼            ▼
        Room CI    Booking CI   Gateway CI
            │          │            │
         verify      verify       verify
            │          │            │
            └──────────┼────────────┘
                       ▼
                    CI PASS
```

After adding Notification Service, verify:

```text
Room
Booking
Notification
Gateway
Config Server
Discovery Server
```

independently where appropriate.

---

# JaCoCo

Add:

```text
JaCoCo
```

for code coverage.

Do not turn the project into a competition for:

```text
100% Coverage
```

Prioritize:

```text
Booking Business Rules
Overlap Logic
Cancellation Authorization
Room Availability
RabbitMQ Event Publishing
RabbitMQ Event Consumption
```

You can use a threshold such as:

```text
70%
```

so:

```text
Coverage >= 70%
       │
       ▼
CI PASS
```

Otherwise:

```text
CI FAIL
```

---

# Docker in CI

When the project reaches the Docker phase, extend CI.

Instead of:

```text
Build
Tests
Verify
```

use:

```text
Build
   ↓
Tests
   ↓
Verify
   ↓
Docker Image Build
```

The initial goal is to verify:

```text
Dockerfile works
Image builds successfully
Application packages correctly
```

Pushing images is not required from the first day.

---

# Security Scan

After Docker image creation, add:

```text
Trivy
```

Example:

```text
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

```text
HIGH
or
CRITICAL
```

This adds a useful DevSecOps practice without overcomplicating the project.

---

# Docker Image Tags

Do not use only:

```text
latest
```

Prefer the Git commit SHA.

Example:

```text
room-service:8d65caa
booking-service:8d65caa
notification-service:8d65caa
```

This gives traceability:

```text
Git Commit
     ↓
Docker Image
```

---

# Pull Request Workflow

Use branches such as:

```text
main
develop
feature/*
```

Example:

```text
feature/booking-overlap-validation
```

Flow:

```text
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

```text
main
```

Rules:

```text
❌ Direct Push
✅ Pull Request
✅ CI must pass
✅ Then Merge
```

This demonstrates that CI is genuinely part of the development process rather than just a YAML file stored in the repository.

---

# Config Server

Add:

```text
Spring Cloud Config Server
```

for:

```text
Centralized Configuration
```

For example:

```text
room-service.yml
booking-service.yml
notification-service.yml
api-gateway.yml
```

This avoids hardcoding all configuration inside individual services.

RabbitMQ configuration can also be centralized.

For example:

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
```

Environment-specific secrets should not be committed directly into configuration files.

---

# Eureka

Use:

```text
Eureka Server
```

Services register themselves as:

```text
ROOM-SERVICE
BOOKING-SERVICE
NOTIFICATION-SERVICE
API-GATEWAY
```

Booking Service should not need to know a fixed address such as:

```text
http://localhost:8081
```

It should use service discovery instead.

RabbitMQ itself does not need Eureka discovery.

Its connection information comes from configuration.

---

# API Gateway

All external requests should enter through:

```text
API Gateway
```

not:

```text
Client → Room Service directly
```

Gateway responsibilities:

```text
Routing
Authentication
Rate Limiting
Correlation ID
Logging
```

RabbitMQ does not go through the API Gateway.

Internal asynchronous messaging remains:

```text
Booking Service
      │
      ▼
RabbitMQ
      │
      ▼
Notification Service
```

---

# Correlation ID

Add a gateway filter that creates:

```text
X-Correlation-Id
```

Example:

```text
8f97a3a...
```

The same identifier should follow the request through:

```text
Gateway
   ↓
Booking
   ↓
Room
```

It should also appear in:

```text
Logs
Tracing
```

When Booking Service publishes a RabbitMQ event, the correlation ID can optionally be placed in:

```text
RabbitMQ Message Headers
```

For example:

```text
X-Correlation-Id
```

Flow:

```text
HTTP Request
     │
     ▼
Gateway
     │
     ▼
Booking Service
     │
     │ RabbitMQ Message Header
     ▼
RabbitMQ
     │
     ▼
Notification Service
```

This makes it easier to connect synchronous request logs with asynchronous event processing.

---

# Docker

Every Spring application should have a:

```text
Dockerfile
```

including:

```text
room-service
booking-service
notification-service
gateway
config-server
discovery-server
```

Each application should produce an independent Docker image.

---

# Docker Compose

Create a complete local environment with:

```text
docker-compose.yml
```

It should run:

```text
PostgreSQL Room DB
PostgreSQL Booking DB
Redis
RabbitMQ
Keycloak
Config Server
Eureka Server
Room Service
Booking Service
Notification Service
API Gateway
```

Use the RabbitMQ management image during development so that the management UI is available.

For example:

```text
RabbitMQ
AMQP Port:       5672
Management Port: 15672
```

The management interface allows you to inspect:

```text
Exchanges
Queues
Bindings
Consumers
Message Rates
Acknowledgements
Dead Letter Queues
```

Then add observability components:

```text
Prometheus
Loki
Tempo
Grafana
```

The goal is to start the entire local system with one command.

```bash
docker compose up -d
```

---

# Observability

Use:

```text
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

```text
Request Count
Request Duration
Error Rate
JVM Memory
CPU
HTTP Status Codes
```

RabbitMQ can also provide messaging metrics such as:

```text
Queue Depth
Published Messages
Delivered Messages
Acknowledged Messages
Consumer Count
Unacknowledged Messages
Dead Letter Messages
```

These can be introduced when useful.

---

# Logging

Use structured logs where practical.

Include:

```text
serviceName
timestamp
level
correlationId
traceId
message
```

For RabbitMQ event consumers, useful additional fields include:

```text
eventId
eventType
bookingId
routingKey
```

Loki collects the logs and Grafana displays them.

---

# Distributed Tracing

Use Tempo to trace a request such as:

```text
POST /api/bookings
```

across:

```text
Gateway
   ↓
Booking Service
   ↓
Room Service
```

This allows you to inspect the complete synchronous distributed trace.

The messaging flow can additionally be correlated:

```text
Booking Service
     │
     ▼
RabbitMQ Publish
     │
     ▼
Notification Service
```

depending on the tracing instrumentation available.

---

# Main Observability Scenario

Send a booking request, then use Grafana to inspect:

```text
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

Then inspect the asynchronous side:

```text
Booking Event Published
         │
         ▼
RabbitMQ
         │
         ▼
Notification Consumed
```

Connect:

```text
Metrics
Logs
Traces
```

to understand the request end to end.

---

# Final Runtime Architecture

```text
                           Keycloak
                              │
                              │ JWT
                              ▼
Client ───────────────────► API Gateway
                              │
                       ┌──────┴──────────┐
                       │                 │
                       ▼                 ▼
                  Room Service ◄──── Booking Service
                                        │
                                        │
                                        │ RabbitMQ Publish
                                        ▼
                                booking.exchange
                                        │
                     ┌──────────────────┴────────────────┐
                     │                                   │
             booking.created                    booking.cancelled
                     │                                   │
                     └──────────────────┬────────────────┘
                                        ▼
                            notification.booking.queue
                                        │
                                        ▼
                               Notification Service
```

Infrastructure:

```text
Config Server
Eureka
Redis
RabbitMQ
PostgreSQL
Keycloak
```

Observability:

```text
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

```text
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
   ├── PostgreSQL Testcontainers
   ├── RabbitMQ Testcontainers
   ├── JaCoCo
   ├── Docker Build
   └── Trivy
   │
   ▼
Merge
```

---

# RabbitMQ Failure Scenario

A useful messaging scenario to demonstrate is a failed notification.

```text
Booking Service
      │
      ▼
BookingCreatedEvent
      │
      ▼
RabbitMQ
      │
      ▼
Notification Service
      │
      X Processing Failure
      │
      ▼
Retry
      │
      X
      ▼
Retry
      │
      X
      ▼
notification.booking.dlq
```

You can then inspect the message using RabbitMQ Management UI.

This demonstrates:

```text
Reliable Messaging
Acknowledgements
Retries
Dead Letter Queues
Failure Handling
```

without adding another microservice.

---

# RabbitMQ Configuration Strategy

Keep RabbitMQ topology simple.

Use:

```text
1 Topic Exchange
1 Notification Queue
1 Dead Letter Queue
2 Routing Keys
```

Specifically:

```text
Exchange:
booking.exchange

Queue:
notification.booking.queue

Dead Letter Queue:
notification.booking.dlq

Routing Keys:
booking.created
booking.cancelled
```

Do not create:

```text
❌ 10 exchanges
❌ Separate exchange for every event
❌ Dozens of queues
❌ Complex routing topology
```

unless the business requirements eventually justify them.

---

# Rules to Prevent Overengineering

Keep these constraints:

```text
❌ No Frontend initially
❌ No Payment
❌ No User Service
❌ No Custom Authentication System
❌ No Real Email
❌ No 8+ Microservices
❌ No Full CRUD for every entity
❌ No Shared Database
❌ No RabbitMQ for every communication
❌ No complicated messaging topology
❌ No unnecessary DevOps tools before useful tests exist
❌ No complicated multi-environment setup
```

In return, focus on:

```text
✅ 3 Business Services
✅ Database per Service
✅ REST APIs
✅ Business Rules
✅ Validation
✅ Sync Communication
✅ Async Communication
✅ RabbitMQ
✅ Exchanges
✅ Routing Keys
✅ Queues
✅ Message Consumers
✅ Acknowledgements
✅ Dead Letter Queue
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

---

# Project Implementation Order

Do not introduce every technology at once.

Build the project in layers.

---

# Phase 1 — Core Business

Start only with:

```text
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

```text
Time validation
Max duration
Active room
Overlapping bookings
Ownership cancellation
```

At the end of this phase, you should have:

```text
Room Service
      +
Booking Service
      +
PostgreSQL
```

working without complex microservices infrastructure.

---

# Phase 2 — Testing

Before expanding the architecture, add solid testing.

Use:

```text
JUnit
Mockito
Spring Boot Integration Tests
Testcontainers PostgreSQL
WireMock
```

Test:

```text
Booking Rules
Overlap Logic
Room Availability
Repositories
REST Controllers
Feign scenarios
```

Goal:

```bash
./mvnw clean verify
```

must run locally without problems.

---

# Phase 3 — Continuous Integration

Add:

```text
GitHub
GitHub Actions
Pull Requests
Branch Protection
JaCoCo
```

Pipeline:

```text
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

```text
merge
```

when CI fails.

From this phase onward, whenever you add a technology, ask:

```text
How will CI verify this?
```

---

# Phase 4 — Spring Cloud

Add:

```text
Config Server
Eureka Server
OpenFeign
API Gateway
```

Architecture:

```text
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

---

# Phase 5 — Resilience + Rate Limiting

Add:

```text
Timeout
Retry
Circuit Breaker
Bulkhead
Redis Rate Limiter
```

Test failure scenarios manually.

Example:

```text
Stop Room Service
       │
       ▼
Call Booking API
       │
       ▼
Observe Circuit Breaker
```

---

# Phase 6 — Security

Add:

```text
Keycloak
OAuth2
OIDC
JWT
Spring Security
```

Create:

```text
ROLE_USER
ROLE_ADMIN
```

Configure endpoint authorization.

Add security integration tests.

CI should now also verify:

```text
401 Unauthorized
403 Forbidden
USER permissions
ADMIN permissions
```

---

# Phase 7 — RabbitMQ Messaging

Add:

```text
RabbitMQ
Spring AMQP
BookingCreatedEvent
BookingCancelledEvent
Notification Service
```

Configure:

```text
booking.exchange
notification.booking.queue

booking.created
booking.cancelled
```

Architecture:

```text
Booking Service
      │
      ▼
RabbitMQ Exchange
      │
      ▼
Notification Queue
      │
      ▼
Notification Service
```

Add RabbitMQ integration tests using:

```text
RabbitMQ Testcontainers
```

CI should verify:

```text
Event Publishing
Exchange Routing
Queue Delivery
Event Consumption
```

Then add:

```text
Retry
Acknowledgement
Dead Letter Queue
```

and test one failure scenario.

Do not overcomplicate the topology.

---

# Phase 8 — Docker

Create a:

```text
Dockerfile
```

for every service.

Then create:

```text
Docker Compose
```

for the complete local system.

Include:

```text
PostgreSQL
Redis
RabbitMQ
Keycloak
Config Server
Eureka
Room Service
Booking Service
Notification Service
Gateway
```

Extend CI:

```text
Tests
   ↓
Docker Build
```

If a Dockerfile is broken:

```text
CI FAIL
```

---

# Phase 9 — Security Scanning

Add:

```text
Trivy
```

after the Docker build.

Pipeline:

```text
Build
   ↓
Test
   ↓
Docker Image
   ↓
Trivy Scan
```

---

# Phase 10 — Observability

Add:

```text
Actuator
Micrometer
Prometheus
Loki
Tempo
Grafana
```

Build one useful dashboard instead of many unnecessary dashboards.

Monitor:

```text
Requests
Latency
Errors
JVM
Logs
Distributed Traces
```

Optionally add RabbitMQ metrics:

```text
Queue Depth
Consumer Count
Message Publish Rate
Message Delivery Rate
Unacknowledged Messages
Dead Letter Messages
```

---

# Final Short Roadmap

```text
Phase 1
Core Business
Room + Booking + PostgreSQL
REST + JPA + Validation + OpenAPI

            ↓

Phase 2
Testing
JUnit + Mockito
Testcontainers PostgreSQL
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
RabbitMQ
Spring AMQP
Topic Exchange
Routing Keys
Queue
Notification Service
DLQ
RabbitMQ Testcontainers

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

---

# Final Result

At the end of the project, you will have built a system with:

```text
3 Business Microservices
API Gateway
Service Discovery
Centralized Configuration
Database per Service
REST Communication
Feign Communication
RabbitMQ Event-Driven Communication
Spring AMQP
Topic Exchange
Routing Keys
Durable Queue
Message Acknowledgements
Retries
Dead Letter Queue
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
PostgreSQL Testcontainers
RabbitMQ Testcontainers
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

The important point is that every technology has a natural use case in the project.

You should be able to explain every major choice:

```text
Why Feign?

Because checking room availability needs an immediate response.


Why RabbitMQ?

Because notification processing does not need to block booking creation.

Booking Service can publish an event and continue without waiting for
Notification Service to complete its work.


Why a topic exchange?

Because booking events have different types and routing keys while still
belonging to the same booking messaging domain.


Why routing keys?

Because they allow messages such as booking.created and booking.cancelled
to be routed according to their event type.


Why a queue?

Because Notification Service should be able to process events asynchronously,
and messages can remain available while the consumer is temporarily offline.


Why acknowledgements?

Because RabbitMQ needs to know whether a consumer successfully processed
a message.


Why a Dead Letter Queue?

Because messages that repeatedly fail should be isolated instead of being
retried forever or silently lost.


Why Circuit Breaker?

Because Booking depends synchronously on Room.


Why Redis?

Because the Gateway needs distributed rate limiting.


Why Keycloak?

Because authentication is infrastructure, not the business domain.


Why Testcontainers?

Because integration tests should run against real infrastructure such as
PostgreSQL and RabbitMQ.


Why CI?

Because every change should automatically prove that business rules,
integration tests, RabbitMQ messaging, packaging, and Docker images
are still valid.
```

That is what makes the project valuable after a Microservices course: it demonstrates intentional architecture and engineering decisions instead of being a collection of unrelated technologies.

The final communication model is:

```text
                     ┌─────────────────────┐
                     │       Client        │
                     └──────────┬──────────┘
                                │
                                ▼
                     ┌─────────────────────┐
                     │     API Gateway     │
                     └──────────┬──────────┘
                                │
              ┌─────────────────┴──────────────────┐
              │                                    │
              ▼                                    ▼
     ┌─────────────────┐                ┌────────────────────┐
     │  Room Service   │◄──── Feign ────│  Booking Service   │
     └─────────────────┘                └─────────┬──────────┘
                                                  │
                                                  │ Event
                                                  ▼
                                         ┌─────────────────┐
                                         │    RabbitMQ     │
                                         │                 │
                                         │ booking.exchange│
                                         └────────┬────────┘
                                                  │
                              ┌───────────────────┴──────────────────┐
                              │                                      │
                       booking.created                       booking.cancelled
                              │                                      │
                              └───────────────────┬──────────────────┘
                                                  │
                                                  ▼
                                  ┌───────────────────────────┐
                                  │ notification.booking.queue│
                                  └─────────────┬─────────────┘
                                                │
                                                ▼
                                   ┌────────────────────────┐
                                   │ Notification Service   │
                                   │                        │
                                   │   @RabbitListener      │
                                   └────────────────────────┘
```

This keeps RabbitMQ focused on the exact problem it solves well:

```text
Booking succeeds
      │
      ▼
Save booking
      │
      ▼
Publish event
      │
      ▼
Return response to user
      │
      │
      └──────── Notification processing happens asynchronously
```

The Booking Service therefore does not need to wait for Notification Service, while the Room availability check remains synchronous because its result is required before the booking can be created.
