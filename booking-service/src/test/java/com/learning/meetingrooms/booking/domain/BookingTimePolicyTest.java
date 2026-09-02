package com.learning.meetingrooms.booking.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTimePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-01T06:00:00Z");

    private BookingTimePolicy policy;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        policy = new BookingTimePolicy(fixedClock);
    }

    @Test
    void acceptsValidFutureBooking() {
        Instant start = NOW.plusSeconds(60 * 60);
        Instant end = start.plusSeconds(2 * 60 * 60);

        BookingTimeRange range = policy.validate(start, end);

        assertThat(range.startTime()).isEqualTo(start);
        assertThat(range.endTime()).isEqualTo(end);
    }

    @Test
    void rejectsEndBeforeStart() {
        Instant start = NOW.plusSeconds(2 * 60 * 60);
        Instant end = NOW.plusSeconds(60 * 60);

        assertViolation(start, end, BookingRuleCode.INVALID_TIME_RANGE);
    }

    @Test
    void rejectsEqualStartAndEnd() {
        Instant time = NOW.plusSeconds(60 * 60);

        assertViolation(time, time, BookingRuleCode.INVALID_TIME_RANGE);
    }

    @Test
    void rejectsBookingInPast() {
        Instant start = NOW.minusSeconds(60);
        Instant end = NOW.plusSeconds(60 * 60);

        assertViolation(start, end, BookingRuleCode.BOOKING_IN_PAST);
    }

    @Test
    void rejectsBookingStartingExactlyNow() {
        Instant end = NOW.plusSeconds(60 * 60);

        assertViolation(NOW, end, BookingRuleCode.BOOKING_IN_PAST);
    }

    @Test
    void acceptsExactlyFourHours() {
        Instant start = NOW.plusSeconds(60 * 60);
        Instant end = start.plusSeconds(4 * 60 * 60);

        BookingTimeRange range = policy.validate(start, end);

        assertThat(range.endTime()).isEqualTo(end);
    }

    @Test
    void rejectsMoreThanFourHours() {
        Instant start = NOW.plusSeconds(60 * 60);
        Instant end = start.plusSeconds((4 * 60 * 60) + 1);

        assertViolation(start, end, BookingRuleCode.DURATION_EXCEEDS_LIMIT);
    }

    private void assertViolation(Instant start, Instant end, BookingRuleCode expectedCode) {
        assertThatThrownBy(() -> policy.validate(start, end))
                .isInstanceOfSatisfying(BookingRuleViolationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
    }
}
