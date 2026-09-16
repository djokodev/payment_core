package com.djokodev.payment_core;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_event_id",
                columnNames = "eventId"
        )
)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String eventId) {
        this.eventId = eventId;
        this.receivedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}