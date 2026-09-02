package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.application.BookingService;
import com.learning.meetingrooms.booking.application.BookingNotFoundException;
import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.domain.BookingRuleCode;
import com.learning.meetingrooms.booking.domain.BookingRuleViolationException;
import com.learning.meetingrooms.booking.domain.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createsBookingUsingTemporaryUserHeader() throws Exception {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(42L);
        when(booking.getRoomId()).thenReturn(15L);
        when(booking.getUserId()).thenReturn("user-123");
        when(booking.getPurpose()).thenReturn("Backend Team Meeting");
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(bookingService.create(any())).thenReturn(booking);

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/bookings/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectsInvalidRequestAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":0,"purpose":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.roomId").exists())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists())
                .andExpect(jsonPath("$.fieldErrors.endTime").exists())
                .andExpect(jsonPath("$.fieldErrors.purpose").exists());
    }

    @Test
    void returnsConflictForOverlappingBooking() throws Exception {
        when(bookingService.create(any())).thenThrow(new BookingRuleViolationException(
                BookingRuleCode.BOOKING_OVERLAP,
                "The room is already booked during the requested time"
        ));

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_OVERLAP"));
    }

    @Test
    void explainsTemporaryIdentityHeaderWhenMissing() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_USER_ID"));
    }

    @Test
    void rejectsBlankTemporaryIdentity() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"));
    }

    @Test
    void listsOnlyCurrentUsersBookings() throws Exception {
        Booking booking = booking(42L, "user-123");
        when(bookingService.findMine("user-123"))
                .thenReturn(List.of(booking));

        mockMvc.perform(get("/api/bookings/me")
                        .header("X-User-Id", " user-123 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].userId").value("user-123"));

        verify(bookingService).findMine("user-123");
    }

    @Test
    void returnsOwnedBooking() throws Exception {
        Booking booking = booking(42L, "user-123");
        when(bookingService.findOwn(42L, "user-123"))
                .thenReturn(booking);

        mockMvc.perform(get("/api/bookings/42")
                        .header("X-User-Id", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void doesNotRevealMissingOrForeignBooking() throws Exception {
        when(bookingService.findOwn(42L, "user-123"))
                .thenThrow(new BookingNotFoundException(42L));

        mockMvc.perform(get("/api/bookings/42")
                        .header("X-User-Id", "user-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancelsOwnedBookingWithoutDeletingResourceHistory() throws Exception {
        mockMvc.perform(delete("/api/bookings/42")
                        .header("X-User-Id", "user-123"))
                .andExpect(status().isNoContent());

        verify(bookingService).cancelOwn(42L, "user-123");
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
                {
                  "roomId":15,
                  "startTime":"2030-01-02T10:00:00Z",
                  "endTime":"2030-01-02T11:00:00Z",
                  "purpose":"Backend Team Meeting"
                }
                """;
    }
}
