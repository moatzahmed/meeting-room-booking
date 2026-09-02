package com.learning.meetingrooms.booking.application;

public class RoomServiceUnavailableException extends RuntimeException {

    public RoomServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
