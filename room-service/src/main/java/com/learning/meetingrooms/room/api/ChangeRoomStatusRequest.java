package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.domain.RoomStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeRoomStatusRequest(@NotNull RoomStatus status) {
}
