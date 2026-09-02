package com.learning.meetingrooms.booking.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BookingOverlapPolicyTest {

    private static final BookingTimeRange EXISTING = range("10:00", "11:00");

    private BookingOverlapPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BookingOverlapPolicy();
    }

    @Test
    void acceptsBookingEndingExactlyWhenExistingBookingStarts() {
        assertThat(policy.overlaps(range("09:00", "10:00"), EXISTING)).isFalse();
    }

    @Test
    void rejectsBookingOverlappingExistingStart() {
        assertThat(policy.overlaps(range("09:30", "10:30"), EXISTING)).isTrue();
    }

    @Test
    void rejectsIdenticalBooking() {
        assertThat(policy.overlaps(range("10:00", "11:00"), EXISTING)).isTrue();
    }

    @Test
    void rejectsBookingInsideExistingBooking() {
        assertThat(policy.overlaps(range("10:15", "10:45"), EXISTING)).isTrue();
    }

    @Test
    void rejectsBookingContainingExistingBooking() {
        assertThat(policy.overlaps(range("09:00", "12:00"), EXISTING)).isTrue();
    }

    @Test
    void rejectsBookingOverlappingExistingEnd() {
        assertThat(policy.overlaps(range("10:30", "11:30"), EXISTING)).isTrue();
    }

    @Test
    void acceptsBookingStartingExactlyWhenExistingBookingEnds() {
        assertThat(policy.overlaps(range("11:00", "12:00"), EXISTING)).isFalse();
    }

    @Test
    void acceptsBookingCompletelyBeforeExistingBooking() {
        assertThat(policy.overlaps(range("08:00", "09:00"), EXISTING)).isFalse();
    }

    @Test
    void acceptsBookingCompletelyAfterExistingBooking() {
        assertThat(policy.overlaps(range("12:00", "13:00"), EXISTING)).isFalse();
    }

    private static BookingTimeRange range(String start, String end) {
        return new BookingTimeRange(at(start), at(end));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-01T" + time + ":00Z");
    }
}
