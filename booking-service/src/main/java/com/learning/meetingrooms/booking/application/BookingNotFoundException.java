package com.learning.meetingrooms.booking.application;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long bookingId) {
        super("Booking %d was not found".formatted(bookingId));
    }
}
