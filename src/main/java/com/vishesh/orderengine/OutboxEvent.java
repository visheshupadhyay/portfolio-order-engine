package com.vishesh.orderengine;

import java.time.LocalDateTime;

/*
 * Immutable durable-notification snapshot. The database is the source of truth;
 * repositories replace/update the whole state rather than mutating this record.
 * nextAttemptAt controls eligibility, while sentAt is populated only after delivery.
 * claimedAt is the temporary lease timestamp; claimToken identifies that specific
 * lease and stops an earlier worker from completing a later worker's claim.
 */
public record OutboxEvent(Long id,
        String claimToken,
        String orderId,
        String eventType,
        OutboxEventStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime claimedAt,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        String lastError) {

}
