package com.learning.meetingrooms.booking.domain;

public class BookingRuleViolationException extends RuntimeException {

    private final BookingRuleCode code;

    public BookingRuleViolationException(BookingRuleCode code, String message) {
        super(message);
        this.code = code;
    }

    public BookingRuleCode getCode() {
        return code;
    }
}
