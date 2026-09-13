package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.application.BookingService;
import com.learning.meetingrooms.booking.application.CreateBookingCommand;
import com.learning.meetingrooms.booking.domain.Booking;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "User-owned meeting-room reservations")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(summary = "Create a confirmed booking")
    ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.create(new CreateBookingCommand(
                request.roomId(), jwt.getSubject(), request.startTime(), request.endTime(), request.purpose()
        ));
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.getId()))
                .body(BookingResponse.from(booking));
    }

    @GetMapping("/me")
    @Operation(summary = "List the current user's bookings")
    List<BookingResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.findMine(jwt.getSubject()).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/all")
    @Operation(summary = "List every booking (administrators only)")
    List<BookingResponse> findAll() {
        return bookingService.findAll().stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Retrieve one booking owned by the current user")
    BookingResponse findOwn(@PathVariable Long bookingId, @AuthenticationPrincipal Jwt jwt) {
        return BookingResponse.from(bookingService.findOwn(bookingId, jwt.getSubject()));
    }

    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Cancel a booking owned by the current user")
    ResponseEntity<Void> cancelOwn(@PathVariable Long bookingId, @AuthenticationPrincipal Jwt jwt) {
        bookingService.cancelOwn(bookingId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}