package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.*;

/*
 * Test-double revision: a recording notifier verifies the service message
 * without sending a real email or SMS.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OrderPaidNotificationServiceTest {
    @Test
    public void sendsPaidMessage() {
        Order order = new Order("order-101");
        TestNotifier testNotifier = new TestNotifier("");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(testNotifier);
        orderPaidNotificationService.notifyOrderPaid(order, 42L);
        orderPaidNotificationService.notifyOrderPaid(order, 42L);
        assertEquals(1, testNotifier.getSendCount());
        assertEquals("42", testNotifier.getReceivedIdempotencyKey());
        orderPaidNotificationService.notifyOrderPaid(order, 43L);
        assertEquals(2, testNotifier.getSendCount());
        assertEquals("Order is paid orderID:order-101", testNotifier.getMessage());
        assertEquals("43", testNotifier.getReceivedIdempotencyKey());
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
