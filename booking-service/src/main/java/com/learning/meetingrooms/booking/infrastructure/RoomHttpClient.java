package com.learning.meetingrooms.booking.infrastructure;

import com.learning.meetingrooms.booking.application.RoomCatalog;
import com.learning.meetingrooms.booking.application.RoomServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

@Component
@RefreshScope
public class RoomHttpClient implements RoomCatalog {

    private final RestClient restClient;

    public RoomHttpClient(
            @LoadBalanced RestClient.Builder loadBalancedBuilder,
            @Qualifier("plainRestClientBuilder") RestClient.Builder plainBuilder,
            @Value("${clients.room-service.base-url}") String roomServiceBaseUrl,
            @Value("${clients.room-service.discovery-enabled}") boolean discoveryEnabled,
            @Value("${clients.room-service.connect-timeout}") Duration connectTimeout,
            @Value("${clients.room-service.read-timeout}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder selectedBuilder = discoveryEnabled
                ? loadBalancedBuilder.clone()
                : plainBuilder.clone();
        this.restClient = selectedBuilder
                .baseUrl(roomServiceBaseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (StringUtils.hasText(correlationId)) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    @Bulkhead(name = "roomService", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "roomService")
    @Retry(name = "roomService")
    public Optional<RoomSummary> findById(Long roomId) {
        try {
            RoomResponse response = restClient.get()
                    .uri("/api/rooms/{id}", roomId)
                    .retrieve()
                    .body(RoomResponse.class);
            return Optional.ofNullable(response)
                    .map(room -> new RoomSummary(room.id(), room.status()));
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private RoomServiceUnavailableException unavailable(RestClientException cause) {
        return new RoomServiceUnavailableException("Room Service is currently unavailable", cause);
    }

    private record RoomResponse(Long id, String status) {
    }
}
