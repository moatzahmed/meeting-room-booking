package com.learning.meetingrooms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigurationTest {

    private final KeyResolver keyResolver = new RateLimitConfiguration().clientIpKeyResolver();

    @Test
    void usesTheRemoteClientIpAsTheRateLimitKey() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 54321))
                .build();

        String key = keyResolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("192.0.2.10");
    }

    @Test
    void usesAStableFallbackWhenTheRemoteAddressIsUnavailable() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings").build();

        String key = keyResolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo(RateLimitConfiguration.UNKNOWN_CLIENT);
    }
}
