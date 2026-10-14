package com.vishesh.notificationservice.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

public class OrderPaidNotificationServiceTest {
    private ProcessedNotificationEventRepository repository = mock(ProcessedNotificationEventRepository.class);
    private NotificationSender notificationSender = mock(NotificationSender.class);

    @Test
    public void sendsPaidOrderNotificationForFirstEvent() {
        String orderId = "microservice-order-101";
        long eventId = 101L;
        when(repository.markProcessedIfFirstTime(eventId)).thenReturn(true);
        OrderPaidNotificationService service = new OrderPaidNotificationService(repository, notificationSender);
        service.notifyOrderPaid(orderId, eventId);
        verify(notificationSender).send("Order is paid orderID:microservice-order-101", "101");
        verify(repository, times(0)).removeProcessedEvent(eventId);
    }

    @Test
    public void doesNotSendDuplicateNotification() {
        String orderId = "microservice-order-101";
        long eventId = 101L;
        when(repository.markProcessedIfFirstTime(eventId)).thenReturn(false);
        OrderPaidNotificationService service = new OrderPaidNotificationService(repository, notificationSender);
        service.notifyOrderPaid(orderId, eventId);
        verify(notificationSender, times(0)).send("Order is paid orderID:microservice-order-101", "101");
        verify(repository, times(0)).removeProcessedEvent(eventId);
    }

    @Test
    public void removesEventReservationWhenNotificationSendingFails() {
        String orderId = "microservice-order-101";
        long eventId = 101L;
        when(repository.markProcessedIfFirstTime(eventId)).thenReturn(true);
        doThrow(new RuntimeException("microservice-exception-test")).when(notificationSender)
                .send("Order is paid orderID:microservice-order-101", "101");
        OrderPaidNotificationService service = new OrderPaidNotificationService(repository, notificationSender);
        assertThrows(RuntimeException.class,()->service.notifyOrderPaid(orderId, eventId));
        verify(repository, times(1)).removeProcessedEvent(eventId);
        verify(notificationSender, times(1)).send("Order is paid orderID:microservice-order-101", "101");
        
    }

}
