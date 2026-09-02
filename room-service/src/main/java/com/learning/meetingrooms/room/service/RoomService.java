package com.learning.meetingrooms.room.service;

import com.learning.meetingrooms.room.api.CreateRoomRequest;
import com.learning.meetingrooms.room.api.RoomResponse;
import com.learning.meetingrooms.room.domain.Room;
import com.learning.meetingrooms.room.domain.RoomStatus;
import com.learning.meetingrooms.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        String normalizedName = request.name().trim();
        if (roomRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateRoomNameException(normalizedName);
        }

        Room room = new Room(normalizedName, request.location().trim(), request.capacity());
        return toResponse(roomRepository.save(room));
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomResponse findById(long id) {
        return toResponse(findRoom(id));
    }

    @Transactional
    public RoomResponse changeStatus(long id, RoomStatus status) {
        Room room = findRoom(id);
        room.changeStatus(status);
        return toResponse(roomRepository.saveAndFlush(room));
    }

    private Room findRoom(long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(), room.getName(), room.getLocation(), room.getCapacity(),
                room.getStatus(), room.getCreatedAt(), room.getCreatedBy(), room.getUpdatedAt()
        );
    }
}
