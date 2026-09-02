package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse created = roomService.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + created.id())).body(created);
    }

    @GetMapping
    public List<RoomResponse> findAll() {
        return roomService.findAll();
    }

    @GetMapping("/{id}")
    public RoomResponse findById(@PathVariable long id) {
        return roomService.findById(id);
    }

    @PatchMapping("/{id}/status")
    public RoomResponse changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeRoomStatusRequest request
    ) {
        return roomService.changeStatus(id, request.status());
    }
}
