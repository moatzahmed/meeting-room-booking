package com.learning.meetingrooms.room.api;

import com.learning.meetingrooms.room.service.DuplicateRoomNameException;
import com.learning.meetingrooms.room.service.RoomNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleNotFound(RoomNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(DuplicateRoomNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleDuplicateName(DuplicateRoomNameException exception) {
        return error(HttpStatus.CONFLICT, "ROOM_NAME_ALREADY_EXISTS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleDatabaseConflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "ROOM_CONFLICT", "Room data conflicts with an existing record", Map.of());
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

    private ApiError error(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), code, message, fieldErrors);
    }
}
