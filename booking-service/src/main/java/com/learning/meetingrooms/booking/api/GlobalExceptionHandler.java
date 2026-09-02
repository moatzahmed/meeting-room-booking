package com.learning.meetingrooms.booking.api;

import com.learning.meetingrooms.booking.application.RoomServiceUnavailableException;
import com.learning.meetingrooms.booking.application.BookingNotFoundException;
import com.learning.meetingrooms.booking.domain.BookingRuleViolationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BookingRuleViolationException.class)
    ResponseEntity<ApiError> handleBusinessRule(BookingRuleViolationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ROOM_INACTIVE, BOOKING_OVERLAP -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(error(status, exception.getCode().name(), exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(RoomServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleRoomServiceUnavailable(RoomServiceUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_SERVICE_UNAVAILABLE", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleOpenRoomServiceCircuit(CallNotPermittedException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ROOM_SERVICE_UNAVAILABLE",
                "Room Service is temporarily unavailable because its circuit breaker is open",
                Map.of()
        );
    }

    @ExceptionHandler(BulkheadFullException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleFullRoomServiceBulkhead(BulkheadFullException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ROOM_SERVICE_BUSY",
                "Room Service dependency capacity is temporarily full",
                Map.of()
        );
    }

    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleBookingNotFound(BookingNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleMissingHeader(MissingRequestHeaderException exception) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_USER_ID", "X-User-Id header is required during development", Map.of());
    }

    @ExceptionHandler(InvalidDevelopmentIdentityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleInvalidDevelopmentIdentity(InvalidDevelopmentIdentityException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", exception.getMessage(), Map.of());
    }

    private ApiError error(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(clock), status.value(), code, message, fieldErrors);
    }
}
