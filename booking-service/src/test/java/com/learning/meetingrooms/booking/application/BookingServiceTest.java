package com.learning.meetingrooms.booking.application;

import com.learning.meetingrooms.booking.domain.Booking;
import com.learning.meetingrooms.booking.domain.BookingRuleCode;
import com.learning.meetingrooms.booking.domain.BookingRuleViolationException;
import com.learning.meetingrooms.booking.domain.BookingStatus;
import com.learning.meetingrooms.booking.domain.BookingTimePolicy;
import com.learning.meetingrooms.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final Instant START = Instant.parse("2030-01-02T10:00:00Z");
    private static final Instant END = Instant.parse("2030-01-02T11:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomCatalog roomCatalog;
    
    @Mock
    private BookingEventPublisher eventPublisher;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        BookingTimePolicy timePolicy = new BookingTimePolicy(Clock.fixed(NOW, ZoneOffset.UTC));
        bookingService = new BookingService(bookingRepository, timePolicy, roomCatalog, eventPublisher);
    }

    @Test
    void createsConfirmedBookingAfterAllChecksPass() {
        when(roomCatalog.findById(15L))
                .thenReturn(Optional.of(new RoomCatalog.RoomSummary(15L, "ACTIVE")));
        when(bookingRepository.existsOverlappingBooking(15L, BookingStatus.CONFIRMED, START, END))
                .thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.create(command("  Architecture discussion  "));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getPurpose()).isEqualTo("Architecture discussion");
        verify(bookingRepository).saveAndFlush(any(Booking.class));
        verify(eventPublisher).publishCreated(any(Booking.class));
    }

    @Test
    void rejectsUnknownRoomBeforeCheckingOverlap() {
        when(roomCatalog.findById(15L)).thenReturn(Optional.empty());

        assertRuleViolation(BookingRuleCode.ROOM_NOT_FOUND);

        verify(bookingRepository, never()).existsOverlappingBooking(any(), any(), any(), any());
    }

    @Test
    void rejectsInactiveRoomBeforeCheckingOverlap() {
        when(roomCatalog.findById(15L))
                .thenReturn(Optional.of(new RoomCatalog.RoomSummary(15L, "INACTIVE")));

        assertRuleViolation(BookingRuleCode.ROOM_INACTIVE);

        verify(bookingRepository, never()).existsOverlappingBooking(any(), any(), any(), any());
    }

    @Test
    void rejectsOverlapFoundByFriendlyPreCheck() {
        when(roomCatalog.findById(15L))
                .thenReturn(Optional.of(new RoomCatalog.RoomSummary(15L, "ACTIVE")));
        when(bookingRepository.existsOverlappingBooking(15L, BookingStatus.CONFIRMED, START, END))
                .thenReturn(true);

        assertRuleViolation(BookingRuleCode.BOOKING_OVERLAP);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesDatabaseRaceIntoOverlapRuleViolation() {
        when(roomCatalog.findById(15L))
                .thenReturn(Optional.of(new RoomCatalog.RoomSummary(15L, "ACTIVE")));
        when(bookingRepository.existsOverlappingBooking(15L, BookingStatus.CONFIRMED, START, END))
                .thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("exclusion constraint"));

        assertRuleViolation(BookingRuleCode.BOOKING_OVERLAP);
    }

    @Test
    void returnsOnlyBookingsSelectedForCurrentUser() {
        Booking booking = new Booking(
                15L, "user-123",
                new com.learning.meetingrooms.booking.domain.BookingTimeRange(START, END),
                "Architecture discussion"
        );
        when(bookingRepository.findAllByUserIdOrderByStartTimeAsc("user-123"))
                .thenReturn(List.of(booking));

        List<Booking> result = bookingService.findMine("user-123");

        assertThat(result).containsExactly(booking);
        verify(bookingRepository).findAllByUserIdOrderByStartTimeAsc("user-123");
    }

    @Test
    void returnsOwnedBooking() {
        Booking booking = anyConfirmedBooking();
        when(bookingRepository.findByIdAndUserId(42L, "user-123"))
                .thenReturn(Optional.of(booking));

        assertThat(bookingService.findOwn(42L, "user-123")).isSameAs(booking);
    }

    @Test
    void hidesBookingThatIsMissingOrOwnedBySomeoneElse() {
        when(bookingRepository.findByIdAndUserId(42L, "user-123"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findOwn(42L, "user-123"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessage("Booking 42 was not found");
    }

    @Test
    void cancelsOwnedBookingThroughDomainTransition() {
        Booking booking = anyConfirmedBooking();
        when(bookingRepository.findByIdAndUserId(42L, "user-123"))
                .thenReturn(Optional.of(booking));

        bookingService.cancelOwn(42L, "user-123");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository, never()).delete(any());
        verify(bookingRepository).flush();
        verify(eventPublisher).publishCancelled(any(Booking.class));
    }

    @Test
    void cancellingAlreadyCancelledBookingIsIdempotent() {
        Booking booking = anyConfirmedBooking();
        booking.cancel();
        when(bookingRepository.findByIdAndUserId(42L, "user-123"))
                .thenReturn(Optional.of(booking));

        bookingService.cancelOwn(42L, "user-123");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).flush();
        verify(eventPublisher).publishCancelled(any(Booking.class));
    }

    private void assertRuleViolation(BookingRuleCode expectedCode) {
        assertThatThrownBy(() -> bookingService.create(command("Architecture discussion")))
                .isInstanceOf(BookingRuleViolationException.class)
                .extracting(exception -> ((BookingRuleViolationException) exception).getCode())
                .isEqualTo(expectedCode);
        verify(eventPublisher, never()).publishCreated(any());
    }

    private CreateBookingCommand command(String purpose) {
        return new CreateBookingCommand(15L, "user-123", START, END, purpose);
    }

    private Booking anyConfirmedBooking() {
        return new Booking(
                15L, "user-123",
                new com.learning.meetingrooms.booking.domain.BookingTimeRange(START, END),
                "Architecture discussion"
        );
    }
}
