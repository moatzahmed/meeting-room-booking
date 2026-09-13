package com.learning.meetingrooms.booking.event;

import java.time.Instant;

public record BookingCreatedEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String userId,
        Long roomId,
        Instant startTime,
        Instant endTime,
        Instant occurredAt
) {
}
