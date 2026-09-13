package com.learning.meetingrooms.booking.application;

import com.learning.meetingrooms.booking.domain.Booking;

public interface BookingEventPublisher {

    void publishCreated(Booking booking);

    void publishCancelled(Booking booking);
}
