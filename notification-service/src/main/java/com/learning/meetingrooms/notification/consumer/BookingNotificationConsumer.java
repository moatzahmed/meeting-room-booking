package com.learning.meetingrooms.notification.consumer;

import com.learning.meetingrooms.notification.event.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

    @RabbitListener(queues = "notification.booking.queue")
    public void handleBookingEvent(BookingEvent event) {
        log.info("Received {} event [eventId={}, bookingId={}, userId={}, roomId={}]",
                event.eventType(), event.eventId(), event.bookingId(),
                event.userId(), event.roomId());

        switch (event.eventType()) {
            case "BOOKING_CREATED" -> log.info(
                    "Notification: Booking confirmed for user {} — room {} from {} to {}",
                    event.userId(), event.roomId(), event.startTime(), event.endTime()
            );
            case "BOOKING_CANCELLED" -> log.info(
                    "Notification: Booking {} cancelled by user {}",
                    event.bookingId(), event.userId()
            );
            default -> log.warn("Unknown event type: {}", event.eventType());
        }
    }
}
