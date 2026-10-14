package com.vishesh.notificationservice.notification;

public interface ProcessedNotificationEventRepository {
    boolean markProcessedIfFirstTime(long eventId);

    void removeProcessedEvent(long eventId);
}
