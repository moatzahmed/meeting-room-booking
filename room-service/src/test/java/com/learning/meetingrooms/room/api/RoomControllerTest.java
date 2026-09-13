package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.config.SecurityConfiguration;
import com.learning.meetingrooms.room.domain.RoomStatus;
import com.learning.meetingrooms.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
@Import(SecurityConfiguration.class)
class RoomControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RoomService roomService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test void userCanViewRooms() throws Exception {
        when(roomService.findAll()).thenReturn(List.of(room()));
        mockMvc.perform(get("/api/rooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Nile Room"));
    }

    @Test void userCannotCreateRooms() throws Exception {
        mockMvc.perform(post("/api/rooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test void adminCreatesRoom() throws Exception {
        when(roomService.create(any())).thenReturn(room());
        mockMvc.perform(post("/api/rooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/rooms/42"));
    }

    private RoomResponse room() {
        return new RoomResponse(42L, "Nile Room", "Floor 2", 12, RoomStatus.ACTIVE, null, "system", null);
    }

    private String validRequest() {
        return "{\"name\":\"Nile Room\",\"location\":\"Floor 2\",\"capacity\":12}";
    }
}