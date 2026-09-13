package com.learning.meetingrooms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigurationTest {
    private final KeyResolver keyResolver = new RateLimitConfiguration().authenticatedUserKeyResolver();

    @Test
    void usesAuthenticatedSubjectAsRateLimitKey() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/bookings").build());
        exchange = exchange.mutate().principal(Mono.just((Principal) () -> "user-123")).build();

        assertThat(keyResolver.resolve(exchange).block()).isEqualTo("user-123");
    }
}