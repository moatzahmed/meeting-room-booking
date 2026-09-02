package com.learning.meetingrooms.booking.domain;

import java.time.Instant;

public record BookingTimeRange(Instant startTime, Instant endTime) {
}
