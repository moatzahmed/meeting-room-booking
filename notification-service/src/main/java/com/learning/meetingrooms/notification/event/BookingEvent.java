package com.learning.meetingrooms.notification.event;

import java.time.Instant;

public record BookingEvent(
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
