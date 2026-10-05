package com.vishesh.orderengine.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;
import com.vishesh.orderengine.order.Order;

@SpringBootTest
@ActiveProfiles("postgres")
/*
 * The important restart-safety test: two separately created service instances
 * share PostgreSQL's processed-event table, so the second instance cannot send
 * a duplicate notification for the same Kafka/outbox event ID.
 */
public class OrderPaidNotificationServicePostgresIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Long savedEventId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedEventId != null) {
            jdbcTemplate.update("DELETE FROM processed_notification_events where event_id=?", savedEventId);
        }
    }

    @Test
    public void doesNotSendDuplicateNotificationAfterNewServiceInstanceIsCreated() {
        savedEventId = UUID.randomUUID().getMostSignificantBits();
        Order order = new Order("order-911");
        JdbcProcessedNotificationEventRepository firstJdbcRepository = new JdbcProcessedNotificationEventRepository(
                jdbcTemplate);
        RecordingNotifier firstNotifier = new RecordingNotifier("first");
        OrderPaidNotificationService firstNotificationService = new OrderPaidNotificationService(firstNotifier,
                firstJdbcRepository);

        firstNotificationService.notifyOrderPaid(order, savedEventId);
        assertEquals(1, firstNotifier.getSendCount());

        RecordingNotifier secondNotifier = new RecordingNotifier("second");
        JdbcProcessedNotificationEventRepository secondJdbcRepository = new JdbcProcessedNotificationEventRepository(
                jdbcTemplate);
        OrderPaidNotificationService secondNotificationService = new OrderPaidNotificationService(secondNotifier,
                secondJdbcRepository);
        secondNotificationService.notifyOrderPaid(order, savedEventId);
        assertEquals(0, secondNotifier.getSendCount());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "Select Count(*) FROM processed_notification_events where event_id=?", Long.class, savedEventId));

    }
}

class RecordingNotifier extends AbstractNotifier {
    private int sendCount = 0;

    public RecordingNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        sendCount++;
    }

    public int getSendCount() {
        return this.sendCount;
    }

}
