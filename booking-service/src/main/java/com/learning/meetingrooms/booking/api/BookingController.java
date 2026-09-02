package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.application.BookingService;
import com.learning.meetingrooms.booking.application.CreateBookingCommand;
import com.learning.meetingrooms.booking.domain.Booking;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    ResponseEntity<BookingResponse> create(
            @Parameter(description = "Temporary development identity; replaced by JWT later", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        String normalizedUserId = normalizeUserId(userId);
        Booking booking = bookingService.create(new CreateBookingCommand(
                request.roomId(), normalizedUserId, request.startTime(), request.endTime(), request.purpose()
        ));
        return ResponseEntity
                .created(URI.create("/api/bookings/" + booking.getId()))
                .body(BookingResponse.from(booking));
    }

    @GetMapping("/me")
    @Operation(summary = "List the current user's bookings")
    List<BookingResponse> findMine(
            @Parameter(description = "Temporary development identity; replaced by JWT later", required = true)
            @RequestHeader("X-User-Id") String userId
    ) {
        return bookingService.findMine(normalizeUserId(userId)).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Retrieve one booking owned by the current user")
    BookingResponse findOwn(
            @PathVariable Long bookingId,
            @Parameter(description = "Temporary development identity; replaced by JWT later", required = true)
            @RequestHeader("X-User-Id") String userId
    ) {
        return BookingResponse.from(bookingService.findOwn(bookingId, normalizeUserId(userId)));
    }

    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Cancel a booking owned by the current user")
    ResponseEntity<Void> cancelOwn(
            @PathVariable Long bookingId,
            @Parameter(description = "Temporary development identity; replaced by JWT later", required = true)
            @RequestHeader("X-User-Id") String userId
    ) {
        bookingService.cancelOwn(bookingId, normalizeUserId(userId));
        return ResponseEntity.noContent().build();
    }

    private String normalizeUserId(String userId) {
        String normalizedUserId = userId.trim();
        if (normalizedUserId.isEmpty() || normalizedUserId.length() > 100) {
            throw new InvalidDevelopmentIdentityException(
                    "X-User-Id must contain between 1 and 100 non-whitespace characters"
            );
        }
        return normalizedUserId;
    }
}
