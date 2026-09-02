package com.learning.meetingrooms.room.service;

import com.learning.meetingrooms.room.api.CreateRoomRequest;
import com.learning.meetingrooms.room.api.RoomResponse;
import com.learning.meetingrooms.room.domain.Room;
import com.learning.meetingrooms.room.domain.RoomStatus;
import com.learning.meetingrooms.room.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createsActiveRoomAndTrimsText() {
        CreateRoomRequest request = new CreateRoomRequest("  Nile Room  ", "  Floor 2  ", 12);
        when(roomRepository.existsByNameIgnoreCase("Nile Room")).thenReturn(false);
        when(roomRepository.save(ArgumentMatchers.any(Room.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.create(request);

        assertThat(response.name()).isEqualTo("Nile Room");
        assertThat(response.location()).isEqualTo("Floor 2");
        assertThat(response.status()).isEqualTo(RoomStatus.ACTIVE);
        verify(roomRepository).save(ArgumentMatchers.any(Room.class));
    }

    @Test
    void rejectsDuplicateRoomNameIgnoringCase() {
        CreateRoomRequest request = new CreateRoomRequest("Nile Room", "Floor 2", 12);
        when(roomRepository.existsByNameIgnoreCase("Nile Room")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(request))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageContaining("Nile Room");
    }

    @Test
    void changesExistingRoomStatus() {
        Room room = new Room("Nile Room", "Floor 2", 12);
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.changeStatus(7L, RoomStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(RoomStatus.INACTIVE);
    }

    @Test
    void reportsMissingRoom() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findById(99L))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessage("Room 99 was not found");
    }
}
