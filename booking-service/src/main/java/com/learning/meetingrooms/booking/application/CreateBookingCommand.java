package com.learning.meetingrooms.booking.application;

import java.time.Instant;

public record CreateBookingCommand(
        Long roomId,
        String userId,
        Instant startTime,
        Instant endTime,
        String purpose
) {
}
