package com.learning.meetingrooms.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull @Positive Long roomId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank @Size(max = 500) String purpose
) {
}
