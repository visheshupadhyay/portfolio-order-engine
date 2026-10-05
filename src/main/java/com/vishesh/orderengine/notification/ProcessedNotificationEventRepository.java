package com.vishesh.orderengine.notification;

/**
 * Records which Kafka event IDs have completed notification delivery.
 *
 * Kafka can redeliver after a crash or retry. The stable outbox event ID is the
 * idempotency key: only the first successful attempt may send the notification.
 */
public interface ProcessedNotificationEventRepository {
    boolean markProcessedIfFirstTime(long eventId);
    void removeProcessedEvent(long eventId);
}
