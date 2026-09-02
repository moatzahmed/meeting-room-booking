package com.learning.meetingrooms.booking.domain;

public enum BookingRuleCode {
    INVALID_TIME_RANGE,
    BOOKING_IN_PAST,
    DURATION_EXCEEDS_LIMIT,
    ROOM_NOT_FOUND,
    ROOM_INACTIVE,
    BOOKING_OVERLAP
}
