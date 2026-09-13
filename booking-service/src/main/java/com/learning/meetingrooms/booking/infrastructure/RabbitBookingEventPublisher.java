package com.learning.meetingrooms.booking.infrastructure;

import com.learning.meetingrooms.booking.application.BookingEventPublisher;
import com.learning.meetingrooms.booking.config.RabbitMQTopologyConfig;
import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.event.BookingCancelledEvent;
import com.learning.meetingrooms.booking.event.BookingCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RabbitBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitBookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitBookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCreated(Booking booking) {
        var event = new BookingCreatedEvent(
                UUID.randomUUID().toString(),
                "BOOKING_CREATED",
                booking.getId(),
                booking.getUserId(),
                booking.getRoomId(),
                booking.getStartTime(),
                booking.getEndTime(),
                Instant.now()
        );

        log.info("Publishing BookingCreatedEvent: bookingId={}, eventId={}",
                event.bookingId(), event.eventId());

        rabbitTemplate.convertAndSend(
                RabbitMQTopologyConfig.EXCHANGE_NAME,
                RabbitMQTopologyConfig.ROUTING_KEY_CREATED,
                event
        );
    }

    @Override
    public void publishCancelled(Booking booking) {
        var event = new BookingCancelledEvent(
                UUID.randomUUID().toString(),
                "BOOKING_CANCELLED",
                booking.getId(),
                booking.getUserId(),
                booking.getRoomId(),
                Instant.now()
        );

        log.info("Publishing BookingCancelledEvent: bookingId={}, eventId={}",
                event.bookingId(), event.eventId());

        rabbitTemplate.convertAndSend(
                RabbitMQTopologyConfig.EXCHANGE_NAME,
                RabbitMQTopologyConfig.ROUTING_KEY_CANCELLED,
                event
        );
    }
}
