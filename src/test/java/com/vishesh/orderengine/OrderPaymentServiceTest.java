package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/*
 * Fast unit tests for the extracted payment workflow. The recording notifier
 * proves that a retry does not send a second notification.
 */
public class OrderPaymentServiceTest {

    @Test
    public void marksOrderPaidAndSendsNotification() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        Order order = new Order("order-101");
        inMemoryOrderRepository.save(order);
        RecordingPaymentNotifier recordingPaymentNotifier = new RecordingPaymentNotifier("TEST0");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(
                recordingPaymentNotifier);
        OrderPaymentService orderPaymentService = new OrderPaymentService(inMemoryOrderRepository,
                orderPaidNotificationService);
        Order returnedOrder = orderPaymentService.pay(order);
        assertEquals(OrderStatus.PAID, returnedOrder.getStatus());
        assertEquals("Order is paid orderID:" + returnedOrder.getId(), recordingPaymentNotifier.getMessage());
    }

    @Test 
    public void returnsAlreadyPaidOrderWithoutSendingSecondNotification() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        Order order1 = new Order("order-101");
        inMemoryOrderRepository.save(order1);
        RecordingPaymentNotifier notifier = new RecordingPaymentNotifier("TEST1");
        OrderPaidNotificationService notificationService = new OrderPaidNotificationService(notifier);
        OrderPaymentService paymentService = new OrderPaymentService(inMemoryOrderRepository, notificationService);
        Order firstTry = paymentService.pay(order1);
        // A second request would load a different Java object, even though it refers
        // to the same stored order. This catches stale-object duplicate notifications.
        Order order2 = new Order(order1.getId());
        Order secondTry = paymentService.pay(order2);

        assertEquals(OrderStatus.PAID,firstTry.getStatus());
        assertEquals(OrderStatus.PAID,secondTry.getStatus());
        assertEquals(1,notifier.getDeliveryCount());
    }

    private static class RecordingPaymentNotifier extends AbstractNotifier {
        private String message;
        private int trackDeliveryCount;

        public RecordingPaymentNotifier(String senderName) {
            super(senderName);

        }

        @Override
        protected void deliver(String message) {
            this.message = message;
            trackDeliveryCount++;
        }

        private String getMessage() {
            return this.message;
        }

        private int getDeliveryCount() {
            return this.trackDeliveryCount;
        }

    }
}
