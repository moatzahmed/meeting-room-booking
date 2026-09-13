package com.learning.meetingrooms.notification;

import com.learning.meetingrooms.notification.consumer.BookingNotificationConsumer;
import com.learning.meetingrooms.notification.event.BookingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BookingNotificationConsumerIT {

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    private BookingNotificationConsumer consumer;

    @Test
    void consumesBookingCreatedEvent() {
        var event = new BookingEvent(
                UUID.randomUUID().toString(),
                "BOOKING_CREATED",
                42L,
                "user-123",
                15L,
                Instant.parse("2099-06-16T10:00:00Z"),
                Instant.parse("2099-06-16T11:00:00Z"),
                Instant.now()
        );

        rabbitTemplate.convertAndSend("booking.exchange", "booking.created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(consumer).handleBookingEvent(any(BookingEvent.class))
        );
    }

    @Test
    void consumesBookingCancelledEvent() {
        var event = new BookingEvent(
                UUID.randomUUID().toString(),
                "BOOKING_CANCELLED",
                42L,
                "user-123",
                15L,
                null,
                null,
                Instant.now()
        );

        rabbitTemplate.convertAndSend("booking.exchange", "booking.cancelled", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(consumer).handleBookingEvent(any(BookingEvent.class))
        );
    }

    @Test
    void routesToDeadLetterQueueWhenConsumerFails() {
        var event = new BookingEvent(
                UUID.randomUUID().toString(),
                "BOOKING_CREATED",
                999L,
                "failing-user",
                15L,
                Instant.parse("2099-06-16T10:00:00Z"),
                Instant.parse("2099-06-16T11:00:00Z"),
                Instant.now()
        );

        doThrow(new RuntimeException("Simulated notification processing error"))
                .when(consumer).handleBookingEvent(argThat(e -> e != null && "failing-user".equals(e.userId())));

        rabbitTemplate.convertAndSend("booking.exchange", "booking.created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("notification.booking.dlq", 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(BookingEvent.class);
            BookingEvent dlqEvent = (BookingEvent) dlqMessage;
            assertThat(dlqEvent.userId()).isEqualTo("failing-user");
        });
    }
}
