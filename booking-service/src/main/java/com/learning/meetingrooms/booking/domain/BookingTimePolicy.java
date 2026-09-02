package com.learning.meetingrooms.booking.domain;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class BookingTimePolicy {

    static final Duration MAXIMUM_DURATION = Duration.ofHours(4);

    private final Clock clock;

    public BookingTimePolicy(Clock clock) {
        this.clock = clock;
    }

    public BookingTimeRange validate(Instant startTime, Instant endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BookingRuleViolationException(
                    BookingRuleCode.INVALID_TIME_RANGE,
                    "Booking start time must be before end time"
            );
        }

        if (!startTime.isAfter(clock.instant())) {
            throw new BookingRuleViolationException(
                    BookingRuleCode.BOOKING_IN_PAST,
                    "Booking start time must be in the future"
            );
        }

        Duration requestedDuration = Duration.between(startTime, endTime);
        if (requestedDuration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new BookingRuleViolationException(
                    BookingRuleCode.DURATION_EXCEEDS_LIMIT,
                    "Booking duration must not exceed 4 hours"
            );
        }

        return new BookingTimeRange(startTime, endTime);
    }
}
