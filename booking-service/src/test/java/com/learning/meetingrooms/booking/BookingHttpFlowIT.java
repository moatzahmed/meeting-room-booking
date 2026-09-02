package com.learning.meetingrooms.booking;

import com.learning.meetingrooms.booking.repository.BookingRepository;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class BookingHttpFlowIT {

    private static final AtomicInteger ROOM_REQUEST_COUNT = new AtomicInteger();
    private static final AtomicInteger ROOM_RESPONSE_CODE = new AtomicInteger(200);
    private static final AtomicInteger ROOM_FAILURES_BEFORE_SUCCESS = new AtomicInteger();
    private static final AtomicLong ROOM_RESPONSE_DELAY_MILLIS = new AtomicLong();
    private static final AtomicReference<String> ROOM_STATUS = new AtomicReference<>("ACTIVE");
    private static final AtomicReference<String> ROOM_CORRELATION_ID = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> ROOM_CALLS_ENTERED = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> ROOM_CALLS_RELEASE = new AtomicReference<>();
    private static final HttpServer ROOM_SERVER = startRoomServer();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void roomServiceAddress(DynamicPropertyRegistry registry) {
        registry.add("clients.room-service.base-url",
                () -> "http://localhost:" + ROOM_SERVER.getAddress().getPort());
        registry.add("clients.room-service.discovery-enabled", () -> "false");
        registry.add("clients.room-service.connect-timeout", () -> "200ms");
        registry.add("clients.room-service.read-timeout", () -> "100ms");
        registry.add("resilience4j.bulkhead.instances.roomService.maxConcurrentCalls", () -> "2");
    }

    @LocalServerPort
    private int bookingPort;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        ROOM_REQUEST_COUNT.set(0);
        ROOM_RESPONSE_CODE.set(200);
        ROOM_FAILURES_BEFORE_SUCCESS.set(0);
        ROOM_RESPONSE_DELAY_MILLIS.set(0);
        ROOM_STATUS.set("ACTIVE");
        ROOM_CORRELATION_ID.set(null);
        ROOM_CALLS_ENTERED.set(null);
        ROOM_CALLS_RELEASE.set(null);
        circuitBreakerRegistry.circuitBreaker("roomService").reset();
    }

    @AfterAll
    static void stopRoomServer() {
        ROOM_SERVER.stop(0);
    }

    @Test
    void createsRetrievesAndCancelsBookingAcrossRealHttpAndPostgres() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        HttpResponse<String> createResponse = send("POST", "/api/bookings", "user-123", """
                {
                  "roomId": 15,
                  "startTime": "%s",
                  "endTime": "%s",
                  "purpose": "End-to-end learning test"
                }
                """.formatted(start, end));

        assertThat(createResponse.statusCode()).isEqualTo(201);
        assertThat(createResponse.headers().firstValue("Location")).isPresent();
        JsonNode created = objectMapper.readTree(createResponse.body());
        long bookingId = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(ROOM_REQUEST_COUNT).hasValue(1);

        HttpResponse<String> mineResponse = send("GET", "/api/bookings/me", "user-123", null);
        assertThat(mineResponse.statusCode()).isEqualTo(200);
        JsonNode mine = objectMapper.readTree(mineResponse.body());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("id").asLong()).isEqualTo(bookingId);

        HttpResponse<String> foreignResponse = send(
                "GET", "/api/bookings/" + bookingId, "another-user", null
        );
        assertThat(foreignResponse.statusCode()).isEqualTo(404);

        HttpResponse<String> cancelResponse = send(
                "DELETE", "/api/bookings/" + bookingId, "user-123", null
        );
        assertThat(cancelResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> cancelledResponse = send(
                "GET", "/api/bookings/" + bookingId, "user-123", null
        );
        assertThat(cancelledResponse.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(cancelledResponse.body()).get("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void propagatesCorrelationIdFromBookingRequestToRoomService() throws Exception {
        String correlationId = "learning-request-123";
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + bookingPort + "/api/bookings"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", "user-123")
                .header("X-Correlation-Id", correlationId)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "roomId": 15,
                          "startTime": "%s",
                          "endTime": "%s",
                          "purpose": "Correlation learning test"
                        }
                        """.formatted(start, start.plus(1, ChronoUnit.HOURS))))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("X-Correlation-Id"))
                .contains(correlationId);
        assertThat(ROOM_CORRELATION_ID).hasValue(correlationId);
    }

    @Test
    void publishesOpenApiDescriptionForBookingEndpoints() throws Exception {
        HttpResponse<String> response = send("GET", "/v3/api-docs", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode document = objectMapper.readTree(response.body());
        assertThat(document.at("/info/title").asText()).isEqualTo("Booking Service API");
        assertThat(document.at("/paths/~1api~1bookings/post").isObject()).isTrue();
        assertThat(document.at("/paths/~1api~1bookings~1me/get").isObject()).isTrue();
    }

    @Test
    void exposesCircuitBreakerStateForOperationsLearning() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/circuitbreakers", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("roomService");
    }

    @Test
    void exposesRetryMetricsForOperationsLearning() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/retries", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("roomService");
    }

    @Test
    void exposesBulkheadMetricsForOperationsLearning() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/bulkheads", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("roomService");
    }

    @Test
    void rejectsExcessConcurrentRoomCallWithoutInvokingTheDependency() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ROOM_CALLS_ENTERED.set(entered);
        ROOM_CALLS_RELEASE.set(release);

        assertThat(bulkheadRegistry.bulkhead("roomService").getBulkheadConfig()
                .getMaxConcurrentCalls()).isEqualTo(2);

        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(
                () -> createBookingUnchecked(15L)
        );
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(
                () -> createBookingUnchecked(15L)
        );

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        HttpResponse<String> rejected = createBookingForRoom(15L);

        assertThat(rejected.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(rejected.body()).get("code").asText())
                .isEqualTo("ROOM_SERVICE_BUSY");
        assertThat(ROOM_REQUEST_COUNT).hasValue(2);

        release.countDown();
        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
    }

    @Test
    void returnsNotFoundWhenRoomDoesNotExist() throws Exception {
        HttpResponse<String> response = createBookingForRoom(999L);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("ROOM_NOT_FOUND");
        assertThat(ROOM_REQUEST_COUNT).hasValue(1);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void returnsConflictWhenRoomIsInactive() throws Exception {
        ROOM_STATUS.set("INACTIVE");

        HttpResponse<String> response = createBookingForRoom(15L);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("ROOM_INACTIVE");
        assertThat(ROOM_REQUEST_COUNT).hasValue(1);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void returnsServiceUnavailableWhenRoomServiceFails() throws Exception {
        ROOM_RESPONSE_CODE.set(500);

        HttpResponse<String> response = createBookingForRoom(15L);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("ROOM_SERVICE_UNAVAILABLE");
        assertThat(ROOM_REQUEST_COUNT).hasValue(2);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void retriesOneTemporaryFailureAndThenCreatesTheBooking() throws Exception {
        ROOM_FAILURES_BEFORE_SUCCESS.set(1);

        assertThat(retryRegistry.retry("roomService").getRetryConfig().getMaxAttempts())
                .isEqualTo(2);

        HttpResponse<String> response = createBookingForRoom(15L);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(ROOM_REQUEST_COUNT).hasValue(2);
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void stopsWaitingAndReturnsServiceUnavailableWhenRoomServiceIsSlow() throws Exception {
        ROOM_RESPONSE_DELAY_MILLIS.set(500);
        long startedAt = System.nanoTime();

        HttpResponse<String> response = createBookingForRoom(15L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("ROOM_SERVICE_UNAVAILABLE");
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void opensCircuitAndRejectsFurtherCallsWithoutContactingUnhealthyRoomService() throws Exception {
        ROOM_RESPONSE_CODE.set(500);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(createBookingForRoom(15L).statusCode()).isEqualTo(503);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("roomService");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(4);
        assertThat(ROOM_REQUEST_COUNT).hasValue(8);

        HttpResponse<String> shortCircuited = createBookingForRoom(15L);

        assertThat(shortCircuited.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(shortCircuited.body()).get("code").asText())
                .isEqualTo("ROOM_SERVICE_UNAVAILABLE");
        assertThat(ROOM_REQUEST_COUNT).hasValue(8);
        assertThat(bookingRepository.count()).isZero();
    }

    private HttpResponse<String> createBookingForRoom(long roomId) throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        return send("POST", "/api/bookings", "user-123", """
                {
                  "roomId": %d,
                  "startTime": "%s",
                  "endTime": "%s",
                  "purpose": "Dependency failure learning test"
                }
                """.formatted(roomId, start, end));
    }

    private HttpResponse<String> createBookingUnchecked(long roomId) {
        try {
            return createBookingForRoom(roomId);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResponse<String> send(String method, String path, String userId, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + bookingPort + path));
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startRoomServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(runnable ->
                    Thread.ofPlatform().daemon().name("fake-room-service-").unstarted(runnable)
            ));
            server.createContext("/api/rooms", exchange -> {
                ROOM_REQUEST_COUNT.incrementAndGet();
                ROOM_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                awaitRoomCallRelease();
                delayRoomResponse();
                if (!exchange.getRequestURI().getPath().equals("/api/rooms/15")) {
                    respond(exchange, 404, """
                            {"code":"ROOM_NOT_FOUND"}
                            """);
                    return;
                }
                int responseCode = ROOM_FAILURES_BEFORE_SUCCESS.getAndUpdate(
                        failures -> Math.max(0, failures - 1)
                ) > 0 ? 500 : ROOM_RESPONSE_CODE.get();
                respond(exchange, responseCode, """
                        {"id":15,"status":"%s"}
                        """.formatted(ROOM_STATUS.get()));
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start Room Service test double", exception);
        }
    }

    private static void delayRoomResponse() {
        try {
            Thread.sleep(ROOM_RESPONSE_DELAY_MILLIS.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitRoomCallRelease() {
        CountDownLatch entered = ROOM_CALLS_ENTERED.get();
        CountDownLatch release = ROOM_CALLS_RELEASE.get();
        if (entered == null || release == null) {
            return;
        }
        entered.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
