package com.vishesh.orderengine;

import java.time.LocalDateTime;

/*
 * Immutable durable-notification snapshot. The database is the source of truth;
 * repositories replace/update the whole state rather than mutating this record.
 * nextAttemptAt controls eligibility, while sentAt is populated only after delivery.
 */
public record OutboxEvent(Long id,
        String orderId,
        String eventType,
        OutboxEventStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        String lastError) {

}
