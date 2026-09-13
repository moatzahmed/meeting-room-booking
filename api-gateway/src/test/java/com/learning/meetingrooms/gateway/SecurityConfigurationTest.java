package com.learning.meetingrooms.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.gateway.server.webflux.routes="
})
@ActiveProfiles("test")
class SecurityConfigurationTest {
    @LocalServerPort int port;
    @MockitoBean ReactiveJwtDecoder jwtDecoder;
    WebTestClient webTestClient;

    @BeforeEach void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test void rejectsAnonymousApiRequests() {
        webTestClient.get().uri("/api/bookings/me").exchange().expectStatus().isUnauthorized();
    }

    @Test void forbidsAdminFromUserBookingEndpoints() {
        decodeAs("admin-token", "ROLE_ADMIN");
        webTestClient.get().uri("/api/bookings/me").headers(h -> h.setBearerAuth("admin-token"))
                .exchange().expectStatus().isForbidden();
    }

    @Test void forbidsUserFromRoomAdministration() {
        decodeAs("user-token", "ROLE_USER");
        webTestClient.post().uri("/api/rooms").headers(h -> h.setBearerAuth("user-token"))
                .exchange().expectStatus().isForbidden();
    }

    private void decodeAs(String tokenValue, String role) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "none").subject("subject-123")
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        when(jwtDecoder.decode(tokenValue)).thenReturn(Mono.just(jwt));
    }
}