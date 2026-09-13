package com.learning.meetingrooms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.config.enabled=false",
                "spring.cloud.gateway.server.webflux.routes[0].id=room-service",
                "spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8081",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/rooms/**",
                "spring.cloud.gateway.server.webflux.routes[1].id=booking-service-rate-limited",
                "spring.cloud.gateway.server.webflux.routes[1].uri=http://localhost:8082",
                "spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/bookings/**",
                "spring.cloud.gateway.server.webflux.routes[1].predicates[1]=Method=POST",
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].name=RequestRateLimiter",
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args[key-resolver]=#{@authenticatedUserKeyResolver}",
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args[redis-rate-limiter.replenishRate]=1",
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args[redis-rate-limiter.burstCapacity]=60",
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args[redis-rate-limiter.requestedTokens]=6",
                "spring.cloud.gateway.server.webflux.routes[2].id=booking-service",
                "spring.cloud.gateway.server.webflux.routes[2].uri=http://localhost:8082",
                "spring.cloud.gateway.server.webflux.routes[2].predicates[0]=Path=/api/bookings/**"
        }
)
@ActiveProfiles("test")
class GatewayRoutesTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void loadsTheExplicitServiceRoutesIncludingTheRateLimitedBookingRoute() {
        Set<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
                .map(route -> route.getId())
                .collectList()
                .map(Set::copyOf)
                .block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "room-service", "booking-service-rate-limited", "booking-service"
        );
    }
}
