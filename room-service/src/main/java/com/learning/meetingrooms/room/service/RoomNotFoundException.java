package com.learning.meetingrooms.room.service;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(long id) {
        super("Room " + id + " was not found");
    }
}
