package com.learning.meetingrooms.booking.repository;

import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUserIdOrderByStartTimeAsc(String userId);

    Optional<Booking> findByIdAndUserId(Long id, String userId);

    @Query("""
            SELECT (COUNT(booking) > 0)
            FROM Booking booking
            WHERE booking.roomId = :roomId
              AND booking.status = :status
              AND booking.startTime < :requestedEnd
              AND booking.endTime > :requestedStart
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") Long roomId,
            @Param("status") BookingStatus status,
            @Param("requestedStart") Instant requestedStart,
            @Param("requestedEnd") Instant requestedEnd
    );
}
