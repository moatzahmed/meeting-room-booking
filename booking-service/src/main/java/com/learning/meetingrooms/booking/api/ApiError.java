package com.learning.meetingrooms.booking.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
}
