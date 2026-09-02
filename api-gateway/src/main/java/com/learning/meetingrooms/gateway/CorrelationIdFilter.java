package com.learning.meetingrooms.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingCorrelationId = exchange.getRequest().getHeaders()
                .getFirst(CORRELATION_ID_HEADER);
        String correlationId = StringUtils.hasText(incomingCorrelationId)
                ? incomingCorrelationId
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();
        ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();

        correlatedExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        return chain.filter(correlatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
