package com.learning.meetingrooms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesAnIncomingIdAndGeneratesOneWhenMissing() {
        assertCorrelationId(MockServerHttpRequest.get("/api/rooms")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "client-request-123")
                .build(), "client-request-123");

        String generated = assertCorrelationId(
                MockServerHttpRequest.get("/api/rooms").build(), null
        );
        assertThat(generated).isNotBlank();
    }

    private String assertCorrelationId(MockServerHttpRequest request, String expected) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            forwarded.set(filteredExchange);
            return filteredExchange.getResponse().setComplete();
        }).block();

        String requestId = forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String responseId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(requestId).isEqualTo(responseId);
        if (expected != null) {
            assertThat(requestId).isEqualTo(expected);
        }
        return requestId;
    }
}
