package com.vishesh.orderengine.notification;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!postgres & !jpa")
public class InMemoryProcessedNotificationEventRepository implements ProcessedNotificationEventRepository {

    // Useful for the lightweight profile and unit tests. It is intentionally
    // not durable: restarting this JVM forgets the recorded event IDs.
    private final Set<Long> deliveredEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markProcessedIfFirstTime(long eventId) {
        // Set.add is atomic here: true means this call won the right to send;
        // false means another delivery of the same event already succeeded.
        return deliveredEventIds.add(eventId);
    }

    @Override
    public void removeProcessedEvent(long eventId) {
        deliveredEventIds.remove(eventId);
    }

}
