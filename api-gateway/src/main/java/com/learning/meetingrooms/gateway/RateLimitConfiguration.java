package com.learning.meetingrooms.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfiguration {

    static final String UNKNOWN_CLIENT = "unknown-client";

    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }

        String hostAddress = remoteAddress.getAddress().getHostAddress();
        return StringUtils.hasText(hostAddress) ? hostAddress : UNKNOWN_CLIENT;
    }
}