package com.learning.meetingrooms.room.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 150) String location,
        @Min(1) @Max(1_000) int capacity
) {
}
