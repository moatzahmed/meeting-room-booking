package com.learning.meetingrooms.booking.application;

import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.domain.BookingRuleCode;
import com.learning.meetingrooms.booking.domain.BookingRuleViolationException;
import com.learning.meetingrooms.booking.domain.BookingStatus;
import com.learning.meetingrooms.booking.domain.BookingTimePolicy;
import com.learning.meetingrooms.booking.domain.BookingTimeRange;
import com.learning.meetingrooms.booking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingTimePolicy timePolicy;
    private final RoomCatalog roomCatalog;

    public BookingService(
            BookingRepository bookingRepository,
            BookingTimePolicy timePolicy,
            RoomCatalog roomCatalog
    ) {
        this.bookingRepository = bookingRepository;
        this.timePolicy = timePolicy;
        this.roomCatalog = roomCatalog;
    }

    @Transactional
    public Booking create(CreateBookingCommand command) {
        BookingTimeRange timeRange = timePolicy.validate(command.startTime(), command.endTime());

        RoomCatalog.RoomSummary room = roomCatalog.findById(command.roomId())
                .orElseThrow(() -> violation(
                        BookingRuleCode.ROOM_NOT_FOUND,
                        "Room %d does not exist".formatted(command.roomId())
                ));

        if (!room.isActive()) {
            throw violation(
                    BookingRuleCode.ROOM_INACTIVE,
                    "Room %d is not active".formatted(command.roomId())
            );
        }

        boolean overlaps = bookingRepository.existsOverlappingBooking(
                command.roomId(),
                BookingStatus.CONFIRMED,
                timeRange.startTime(),
                timeRange.endTime()
        );
        if (overlaps) {
            throw overlapViolation();
        }

        Booking booking = new Booking(
                command.roomId(),
                command.userId(),
                timeRange,
                command.purpose().trim()
        );

        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException exception) {
            // A simultaneous request may pass the query above first. PostgreSQL is
            // the final authority and atomically rejects that race here.
            throw overlapViolation();
        }
    }

    @Transactional(readOnly = true)
    public List<Booking> findMine(String userId) {
        return bookingRepository.findAllByUserIdOrderByStartTimeAsc(userId);
    }

    @Transactional(readOnly = true)
    public Booking findOwn(Long bookingId, String userId) {
        return findOwnedBooking(bookingId, userId);
    }

    @Transactional
    public void cancelOwn(Long bookingId, String userId) {
        Booking booking = findOwnedBooking(bookingId, userId);
        booking.cancel();
        // No explicit save is needed: JPA dirty checking detects the status change
        // and writes it when this transaction commits.
    }

    private Booking findOwnedBooking(Long bookingId, String userId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private BookingRuleViolationException overlapViolation() {
        return violation(
                BookingRuleCode.BOOKING_OVERLAP,
                "The room is already booked during the requested time"
        );
    }

    private BookingRuleViolationException violation(BookingRuleCode code, String message) {
        return new BookingRuleViolationException(code, message);
    }
}
