package com.vishesh.orderengine;

/*
 * Test-double revision: a recording notifier verifies the service message
 * without sending a real email or SMS.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.AbstractNotifier;
import com.vishesh.orderengine.Order;
import com.vishesh.orderengine.OrderPaidNotificationService;

public class OrderPaidNotificationServiceTest {
    @Test
    public void sendsPaidMessage() {
        Order order = new Order("order-101");
        TestNotifier testNotifier = new TestNotifier("");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(testNotifier);
        orderPaidNotificationService.notifyOrderPaid(order);

        assertEquals("Order is paid orderID:order-101",testNotifier.getMessage());
    }
}

class TestNotifier extends AbstractNotifier {
    private String m;
    public TestNotifier (String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        m = message;
    }

    public String getMessage() {
        return this.m;
    }
}
