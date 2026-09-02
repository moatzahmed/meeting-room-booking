package com.learning.meetingrooms.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    public Booking(
            Long roomId,
            String userId,
            BookingTimeRange timeRange,
            String purpose
    ) {
        this.roomId = roomId;
        this.userId = userId;
        this.startTime = timeRange.startTime();
        this.endTime = timeRange.endTime();
        this.purpose = purpose;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public String getUserId() { return userId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getPurpose() { return purpose; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
