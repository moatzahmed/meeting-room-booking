package com.learning.meetingrooms.room.service;

public class DuplicateRoomNameException extends RuntimeException {

    public DuplicateRoomNameException(String name) {
        super("A room named '" + name + "' already exists");
    }
}
