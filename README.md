# Meeting Room Booking — Learning Journey

This repository builds a meeting room booking system incrementally. The goal is to understand each architectural decision, not to introduce the entire microservices stack at once.

## Current milestone: Phase 6 security foundation complete

The repository currently contains:

- A root Maven aggregator.
- A GitHub Actions pull-request build that runs the complete Maven verification lifecycle.
- JaCoCo reports and per-module enforcement of at least 70% line coverage.
- A layered Spring Boot `room-service`.
- Room creation, listing, lookup, and status changes.
- PostgreSQL persistence managed by Flyway.
- Request validation and consistent API errors.
- Focused MVC and service-layer tests.
- A separate `booking-service` and `booking_db`, preserving service-owned data.
- Deterministic policies for valid ranges, future starts, and a four-hour maximum.
- Half-open booking intervals (`[start, end)`) so adjacent bookings are allowed.
- JPA persistence, Flyway migrations, auditing, and repository overlap queries.
- A PostgreSQL exclusion constraint that prevents concurrent overlapping confirmed bookings.
- A `POST /api/bookings` endpoint with validation and consistent API errors.
- A synchronous Room Service HTTP adapter behind a replaceable `RoomCatalog` port.
- Active-room, overlap, and database-race checks coordinated by an application service.
- Keycloak-issued JWT authentication at the gateway and both business services.
- Role-based authorization with `ROLE_USER` and `ROLE_ADMIN`.
- Booking ownership derived from the JWT `sub` claim; the temporary `X-User-Id` boundary is removed.
- Owner-filtered `GET /api/bookings/me` and `GET /api/bookings/{id}` endpoints.
- Idempotent `DELETE /api/bookings/{id}` cancellation that preserves booking history.
- Foreign booking IDs are hidden behind the same `404` response as missing bookings.
- OpenAPI metadata and operation descriptions for every Booking endpoint.
- A full HTTP test using real Tomcat, a Room Service HTTP test double, and PostgreSQL.
- Explicit connection and read timeouts for Booking-to-Room HTTP calls.
- End-to-end scenarios for missing, inactive, failing, and slow rooms.
- Correct failure classification: `404`, `409`, or `503` depending on cause.
- Resilience4j 2.4.0 circuit breaker with Spring Boot 4 AOP integration.
- A two-attempt, 100 ms retry policy limited to temporary Room Service failures.
- Explicit aspect ordering: the circuit breaker measures one complete retried operation.
- A semaphore bulkhead limiting concurrent Booking-to-Room calls without adding another executor.
- Fail-fast bulkhead rejection with a distinct `503 ROOM_SERVICE_BUSY` response.
- Explicit resilience ordering: bulkhead → circuit breaker → retry → HTTP call.
- Actuator visibility for bulkhead, circuit-breaker, and retry state/events.
- Tests proving `404` and inactive-room outcomes are not retried.
- A concurrent test proving excess work is rejected before reaching Room Service.
- 65 Booking Service tests: 35 fast tests and 30 integration-test cases.
- A reactive Spring Cloud Gateway on port `8080` as the client-facing entry point.
- Explicit `/api/rooms/**` and `/api/bookings/**` routes with environment-configurable targets.
- Read-only Actuator gateway visibility and one focused route-configuration test.
- A highest-precedence gateway filter that preserves or generates `X-Correlation-Id`.
- The correlation ID is forwarded to the selected service and echoed in the client response.
- One compact correlation test covers both preservation and generation behavior.
- Booking and Room place `X-Correlation-Id` in logging MDC and echo it in responses.
- Booking propagates that same ID on its outgoing Room Service HTTP request.
- MDC is always cleared so reused servlet threads cannot leak an earlier request ID.
- One focused HTTP test proves the ID reaches Room Service unchanged.
- A standalone Eureka discovery server on port `8761`.
- Room, Booking, and Gateway register under their `spring.application.name` values.
- Gateway routes resolve `lb://room-service` and `lb://booking-service` dynamically.
- Direct service URL environment overrides remain available for troubleshooting.
- Booking resolves `http://room-service` through Spring Cloud LoadBalancer and Eureka.
- Separate normal and load-balanced `RestClient.Builder` beans keep Eureka's own HTTP client isolated.
- `ROOM_SERVICE_DISCOVERY_ENABLED=false` enables an explicit direct URL for tests or diagnostics.
- A Spring Cloud Config Server runs on port `8888`.
- A Git-backed configuration repository holds settings shared by all Config Clients.
- Service-named repository files hold Room, Booking, Gateway, and Discovery settings.
- Room, Booking, and Gateway import remote configuration during startup.
- Discovery Server also imports its service-specific configuration from Config Server.
- Config Server is a required startup dependency, so clients fail fast when centralized configuration is unavailable.
- Client `application.yml` files contain only the application name and Config Server location.
- Database settings are centralized, but credentials are environment placeholders rather than hardcoded production secrets.
- Small `dev` and `test` profile overlays customize shared settings without copying base files.
- A Booking-specific `dev` overlay demonstrates service-and-profile configuration precedence.
- RabbitMQ carries Spring Cloud Bus refresh events between Config Server and every client.
- `POST /actuator/busrefresh` broadcasts a refresh instead of restarting each service manually.
- Booking's Room HTTP adapter is refresh-scoped so changed client settings rebuild that bean safely.

## Verify the project

From the repository root:

```powershell
mvn clean verify
```

This command compiles every service, runs unit and Testcontainers integration
tests, creates JaCoCo HTML/XML reports, enforces the coverage threshold, and
packages every application. GitHub Actions runs the same command for pushes and
pull requests targeting `main`. See [CONTRIBUTING.md](CONTRIBUTING.md) for the
repository model, pull-request workflow, and required branch ruleset.

## Start PostgreSQL

Start Docker Desktop, then run both service databases and RabbitMQ:

```powershell
docker compose -f docker/compose.yml up -d room-db booking-db rabbitmq
```

Room PostgreSQL is exposed on host port `5433`; Booking PostgreSQL is exposed on
`5434`. The fallback credentials in Compose and the Git-backed configuration repository
are local learning credentials, not production secrets. Environment variables override
them.

RabbitMQ accepts AMQP connections on `5672`. Its local management UI is available
at `http://localhost:15672` with the learning-only `guest` / `guest` credentials.

## Run the Room Service

```powershell
mvn -pl room-service spring-boot:run
```

Then call:

```powershell
Invoke-RestMethod http://localhost:8081/api/rooms
```

## Run the system

Start the Config Server before the applications that consume its configuration:

```powershell
mvn -pl config-server spring-boot:run
```

You can inspect the configuration resolved for Booking Service at
`http://localhost:8888/booking-service/default`. Then start the discovery server:

To inspect the development overlay, use
`http://localhost:8888/booking-service/dev`. Activate it in a client process with:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
```

After committing and pushing a configuration change, ask Config Server to broadcast
the refresh event:

```powershell
Invoke-RestMethod -Method Post http://localhost:8888/actuator/busrefresh
```

The endpoint is an internal administrative operation and must not be exposed through
the public API Gateway in production.

The default Git URI points to the GitHub `microservice-project-configs` repository on
its `main` branch. Set `CONFIG_GIT_USERNAME` and `CONFIG_GIT_TOKEN` outside source
control when the private repository requires authentication. `CONFIG_GIT_URI` and
`CONFIG_GIT_DEFAULT_LABEL` can select another repository or branch.

```powershell
mvn -pl discovery-server spring-boot:run
```

Its registry dashboard is available at `http://localhost:8761`. Then start Room
Service, Booking Service, and finally the gateway. This order lets each client fetch
central configuration before it registers with Eureka:

```powershell
mvn -pl api-gateway spring-boot:run
```

Clients can then use the single gateway address:

```powershell
Invoke-RestMethod http://localhost:8080/api/rooms
```

The gateway preserves the original paths and uses Eureka to select the appropriate
service instance. `ROOM_SERVICE_URL` and `BOOKING_SERVICE_URL` can still override
the logical `lb://` defaults without rebuilding the application.

## Try the APIs

Create a room:

```powershell
$body = @{
    name = "Nile Room"
    location = "Floor 2"
    capacity = 12
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri http://localhost:8081/api/rooms `
    -ContentType application/json `
    -Body $body
```

List and retrieve rooms:

```powershell
Invoke-RestMethod http://localhost:8081/api/rooms
Invoke-RestMethod http://localhost:8081/api/rooms/1
```

Disable a room:

```powershell
Invoke-RestMethod -Method Patch `
    -Uri http://localhost:8081/api/rooms/1/status `
    -ContentType application/json `
    -Body '{"status":"INACTIVE"}'
```

## Explore the API documentation

With Room Service running, open:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

Swagger UI is an interactive view generated from the same controller and DTO definitions used by the application.

With Booking Service running on port `8082`, its documentation is available at:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

## Integration tests

`mvn clean verify` runs fast tests first and PostgreSQL integration tests afterward. The integration tests use a temporary Testcontainers PostgreSQL instance and require a working Docker engine. If Docker is unavailable locally, they are reported as skipped rather than replaced with a different database.

The Booking integration tests intentionally use real PostgreSQL because its range
types and exclusion constraints are part of the correctness design. An in-memory
database would not prove that concurrent overlap protection works.

## Redis-backed booking rate limiting

Start Redis with `docker compose -f docker/compose.yml up -d redis`, then start the
API Gateway normally. `POST /api/bookings/**` is limited per client IP with a burst
capacity of 10 requests and an average refill rate of 10 requests per minute. When
the bucket is empty, the gateway responds with `429 Too Many Requests`; room routes
and non-POST booking requests are not rate limited.

The Redis connection defaults to `localhost:6379`. Override it with `REDIS_HOST` and
`REDIS_PORT` when running the gateway in another environment. The client-IP key is
an interim choice until Keycloak/JWT support lets the gateway key limits by the
authenticated user.

## Phase 6 security

Start Keycloak with `docker compose -f docker/compose.yml up -d keycloak`. The imported
`meeting-room` realm contains the public `meeting-room-api` client, `ROLE_USER`,
`ROLE_ADMIN`, and learning-only `user/user` and `admin/admin` accounts. Production
credentials must be supplied outside source control.

The gateway, Room Service, and Booking Service validate bearer JWTs independently.
The gateway rate limiter now keys booking creation by the authenticated JWT subject.
Users can view rooms and manage only their own bookings; administrators can manage
rooms and use `GET /api/bookings/all`.

## Next milestone

Complete a live Keycloak smoke test and continue Phase 6 security hardening.
