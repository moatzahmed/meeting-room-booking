package com.learning.meetingrooms.booking.event;

import java.time.Instant;

public record BookingCancelledEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String userId,
        Long roomId,
        Instant occurredAt
) {
}
