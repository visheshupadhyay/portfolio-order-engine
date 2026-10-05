package com.vishesh.orderengine.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/*
 * Explains the basic idempotency rule without PostgreSQL: add reserves an event
 * once; removing that reservation after a failed send allows a later retry.
 */
public class InMemoryProcessedNotificationEventRepositoryTest {

    @Test 
    public void allowsAnEventToBeRecordedAgainAfterItIsRemoved() {
        InMemoryProcessedNotificationEventRepository repository = new InMemoryProcessedNotificationEventRepository();
        assertTrue(repository.markProcessedIfFirstTime(42L));
        assertFalse(repository.markProcessedIfFirstTime(42L));
        repository.removeProcessedEvent(42L);
        assertTrue(repository.markProcessedIfFirstTime(42L));
    }
}
