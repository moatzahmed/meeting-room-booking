package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.application.BookingNotFoundException;
import com.learning.meetingrooms.booking.application.BookingService;
import com.learning.meetingrooms.booking.config.SecurityConfiguration;
import com.learning.meetingrooms.booking.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import(SecurityConfiguration.class)
class BookingControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean BookingService bookingService;
    @MockitoBean Clock clock;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test void forbidsAdminFromUserOwnedEndpoints() throws Exception {
        mockMvc.perform(get("/api/bookings/me").with(adminJwt())).andExpect(status().isForbidden());
    }

    @Test void createsBookingUsingJwtSubject() throws Exception {
        Booking booking = booking(42L, "user-123");
        when(bookingService.create(any())).thenReturn(booking);
        mockMvc.perform(post("/api/bookings").with(userJwt()).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/bookings/42"));
        verify(bookingService).create(argThat(command -> command.userId().equals("user-123")));
    }

    @Test void rejectsInvalidRequestAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/bookings").with(userJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":0,\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void returnsConflictForOverlappingBooking() throws Exception {
        when(bookingService.create(any())).thenThrow(new BookingRuleViolationException(
                BookingRuleCode.BOOKING_OVERLAP, "overlap"));
        mockMvc.perform(post("/api/bookings").with(userJwt()).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOKING_OVERLAP"));
    }

    @Test void listsOnlyJwtUsersBookings() throws Exception {
        Booking owned = booking(42L, "user-123");
        when(bookingService.findMine("user-123")).thenReturn(List.of(owned));
        mockMvc.perform(get("/api/bookings/me").with(userJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value("user-123"));
        verify(bookingService).findMine("user-123");
    }

    @Test void adminListsAllBookings() throws Exception {
        Booking existing = booking(42L, "user-123");
        when(bookingService.findAll()).thenReturn(List.of(existing));
        mockMvc.perform(get("/api/bookings/all").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(42));
    }

    @Test void userCannotListAllBookings() throws Exception {
        mockMvc.perform(get("/api/bookings/all").with(userJwt())).andExpect(status().isForbidden());
    }

    @Test void hidesForeignBookingAndCancelsOwnedBooking() throws Exception {
        when(bookingService.findOwn(42L, "user-123")).thenThrow(new BookingNotFoundException(42L));
        mockMvc.perform(get("/api/bookings/42").with(userJwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        mockMvc.perform(delete("/api/bookings/7").with(userJwt())).andExpect(status().isNoContent());
        verify(bookingService).cancelOwn(7L, "user-123");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor userJwt() {
        return jwt().jwt(token -> token.subject("user-123"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("admin-123"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Booking booking(Long id, String userId) {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(id);
        when(booking.getRoomId()).thenReturn(15L);
        when(booking.getUserId()).thenReturn(userId);
        when(booking.getPurpose()).thenReturn("Backend Team Meeting");
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        return booking;
    }

    private String validRequest() {
        return """
                {"roomId":15,"startTime":"2030-01-02T10:00:00Z",
                 "endTime":"2030-01-02T11:00:00Z","purpose":"Backend Team Meeting"}
                """;
    }
}