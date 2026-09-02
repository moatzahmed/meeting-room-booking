package com.learning.meetingrooms.booking.application;

import java.util.Optional;

public interface RoomCatalog {

    Optional<RoomSummary> findById(Long roomId);

    record RoomSummary(Long id, String status) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }
}
