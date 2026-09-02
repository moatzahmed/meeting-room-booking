package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.domain.RoomStatus;
import com.learning.meetingrooms.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @Test
    void returnsRooms() throws Exception {
        RoomResponse room = new RoomResponse(1L, "Nile Room", "Floor 2", 12,
                RoomStatus.ACTIVE, null, "system", null);
        when(roomService.findAll()).thenReturn(List.of(room));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nile Room"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void createsRoomAndReturnsLocationHeader() throws Exception {
        RoomResponse room = new RoomResponse(42L, "Nile Room", "Floor 2", 12,
                RoomStatus.ACTIVE, null, "system", null);
        when(roomService.create(any(CreateRoomRequest.class))).thenReturn(room);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nile Room","location":"Floor 2","capacity":12}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rooms/42"))
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void rejectsInvalidCreateRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","location":"","capacity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists());
    }
}
