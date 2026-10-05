package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.order.*;

/*
 * Test-double revision: a recording notifier verifies the service message
 * without sending a real email or SMS.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * Unit tests for notification idempotency: send once for a new event ID, skip
 * an already-completed event, but allow a retry if the first send failed.
 */
public class OrderPaidNotificationServiceTest {
    @Test
    public void sendsPaidMessage() {
        Order order = new Order("order-101");
        TestNotifier testNotifier = new TestNotifier("");
        ProcessedNotificationEventRepository processedNotificationEventRepository = new InMemoryProcessedNotificationEventRepository();
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(testNotifier,
                processedNotificationEventRepository);
        orderPaidNotificationService.notifyOrderPaid(order, 42L);
        orderPaidNotificationService.notifyOrderPaid(order, 42L);
        assertEquals(1, testNotifier.getSendCount());
        assertEquals("42", testNotifier.getReceivedIdempotencyKey());
        orderPaidNotificationService.notifyOrderPaid(order, 43L);
        assertEquals(2, testNotifier.getSendCount());
        assertEquals("Order is paid orderID:order-101", testNotifier.getMessage());
        assertEquals("43", testNotifier.getReceivedIdempotencyKey());
    }

    @Test
    public void allowsRetryWhenNotificationDeliveryFails() {
        Order order = new Order("order-101");
        ErrorNotifier errorNotifier = new ErrorNotifier("");
        ProcessedNotificationEventRepository processedNotificationEventRepository = new InMemoryProcessedNotificationEventRepository();
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(errorNotifier,
                processedNotificationEventRepository);
        assertThrows(RuntimeException.class, ()->orderPaidNotificationService.notifyOrderPaid(order, 99L));
        orderPaidNotificationService.notifyOrderPaid(order, 99L);
        assertEquals(1, errorNotifier.getSendCount());
        assertEquals("99", errorNotifier.getReceivedIdempotencyKey());
    }
}

class TestNotifier extends AbstractNotifier {
    private String message;
    private int sendCount;
    private String receivedIdempotencyKey;

    public TestNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        this.message = message;
        sendCount++;
    }

    @Override
    protected void deliver(String message, String idempotencyKey) {
        this.message = message;
        this.receivedIdempotencyKey = idempotencyKey;

        sendCount++;
    }

    public String getMessage() {
        return this.message;
    }

    public int getSendCount() {
        return this.sendCount;
    }

    public String getReceivedIdempotencyKey() {
        return this.receivedIdempotencyKey;
    }
}

class ErrorNotifier extends AbstractNotifier {
    private String message;
    private int sendCount;
    private String receivedIdempotencyKey;
    private boolean throwError = true;

    public ErrorNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        this.message = message;
        sendCount++;
    }

    @Override
    protected void deliver(String message, String idempotencyKey) {
        if (throwError) {
            throwError = false;
            throw new RuntimeException("Runtime Error");
        }
        this.message = message;
        this.receivedIdempotencyKey = idempotencyKey;

        sendCount++;

    }

    public String getMessage() {
        return this.message;
    }

    public int getSendCount() {
        return this.sendCount;
    }

    public String getReceivedIdempotencyKey() {
        return this.receivedIdempotencyKey;
    }
}
