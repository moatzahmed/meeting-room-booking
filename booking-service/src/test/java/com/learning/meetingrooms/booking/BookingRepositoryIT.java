package com.learning.meetingrooms.booking;

import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.application.BookingService;
import com.learning.meetingrooms.booking.domain.BookingStatus;
import com.learning.meetingrooms.booking.domain.BookingTimeRange;
import com.learning.meetingrooms.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BookingRepositoryIT {

    private static final Long ROOM_ID = 15L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpExistingBooking() {
        bookingRepository.deleteAll();
        bookingRepository.saveAndFlush(booking("10:00", "11:00"));
    }

    @Test
    void flywayCreatesBookingsTable() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'bookings'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @ParameterizedTest(name = "request {0}-{1} has conflict: {2}")
    @CsvSource({
            "08:00, 09:00, false",
            "09:00, 10:00, false",
            "09:30, 10:30, true",
            "10:00, 11:00, true",
            "10:15, 10:45, true",
            "09:00, 12:00, true",
            "10:30, 11:30, true",
            "11:00, 12:00, false",
            "12:00, 13:00, false"
    })
    void detectsOverlapUsingPostgres(String start, String end, boolean expectedConflict) {
        boolean conflict = hasConflict(ROOM_ID, start, end);

        assertThat(conflict).isEqualTo(expectedConflict);
    }

    @Test
    void bookingForAnotherRoomDoesNotConflict() {
        assertThat(hasConflict(99L, "10:00", "11:00")).isFalse();
    }

    @Test
    void cancelledBookingNoLongerConflicts() {
        Booking existing = bookingRepository.findAll().getFirst();
        existing.cancel();
        bookingRepository.saveAndFlush(existing);

        assertThat(hasConflict(ROOM_ID, "10:00", "11:00")).isFalse();
    }

    @Test
    void persistencePopulatesIdentityStatusAndAuditTimestamps() {
        Booking saved = bookingRepository.findAll().getFirst();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("user-123");
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void databaseConstraintRejectsOverlappingConfirmedBooking() {
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(booking("10:30", "11:30")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseConstraintAllowsAdjacentConfirmedBooking() {
        Booking adjacent = bookingRepository.saveAndFlush(booking("11:00", "12:00"));

        assertThat(adjacent.getId()).isNotNull();
        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    void ownershipQueriesDoNotReturnAnotherUsersBooking() {
        Booking existing = bookingRepository.findAll().getFirst();

        assertThat(bookingRepository.findAllByUserIdOrderByStartTimeAsc("user-123"))
                .extracting(Booking::getId)
                .containsExactly(existing.getId());
        assertThat(bookingRepository.findAllByUserIdOrderByStartTimeAsc("another-user"))
                .isEmpty();
        assertThat(bookingRepository.findByIdAndUserId(existing.getId(), "another-user"))
                .isEmpty();
    }

    @Test
    void transactionalCancellationPersistsStatusWithoutDeletingRow() {
        Booking existing = bookingRepository.findAll().getFirst();

        bookingService.cancelOwn(existing.getId(), "user-123");

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE id = ?",
                String.class,
                existing.getId()
        );
        assertThat(storedStatus).isEqualTo("CANCELLED");
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    private boolean hasConflict(Long roomId, String start, String end) {
        return bookingRepository.existsOverlappingBooking(
                roomId,
                BookingStatus.CONFIRMED,
                at(start),
                at(end)
        );
    }

    private static Booking booking(String start, String end) {
        return new Booking(
                ROOM_ID,
                "user-123",
                new BookingTimeRange(at(start), at(end)),
                "Backend Team Meeting"
        );
    }

    private static java.time.Instant at(String time) {
        return java.time.Instant.parse("2026-09-01T" + time + ":00Z");
    }
}
