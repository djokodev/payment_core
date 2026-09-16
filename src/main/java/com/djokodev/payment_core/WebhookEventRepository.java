package com.djokodev.payment_core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEvent, Long> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Query(
            value = """
            INSERT INTO webhook_events (event_id, received_at)
            VALUES (:eventId, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """,
            nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId);
}