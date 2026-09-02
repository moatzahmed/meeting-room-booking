package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.domain.RoomStatus;

import java.time.Instant;

public record RoomResponse(
        Long id,
        String name,
        String location,
        int capacity,
        RoomStatus status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt
) {
}
