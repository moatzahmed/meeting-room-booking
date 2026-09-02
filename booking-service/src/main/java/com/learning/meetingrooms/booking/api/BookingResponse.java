package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.domain.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long roomId,
        String userId,
        Instant startTime,
        Instant endTime,
        String purpose,
        BookingStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getRoomId(), booking.getUserId(),
                booking.getStartTime(), booking.getEndTime(), booking.getPurpose(),
                booking.getStatus(), booking.getCreatedAt(), booking.getUpdatedAt()
        );
    }
}
