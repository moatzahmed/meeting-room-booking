package com.learning.meetingrooms.booking.domain;

import org.springframework.stereotype.Component;

@Component
public class BookingOverlapPolicy {

    public boolean overlaps(BookingTimeRange requested, BookingTimeRange existing) {
        return requested.startTime().isBefore(existing.endTime())
                && requested.endTime().isAfter(existing.startTime());
    }
}
