package com.learning.meetingrooms.room.repository;

import com.learning.meetingrooms.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByNameIgnoreCase(String name);
}
