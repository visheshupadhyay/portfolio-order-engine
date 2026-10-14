package com.vishesh.notificationservice.notification;

import org.springframework.stereotype.Service;

@Service 
public class OrderPaidNotificationService {
    private final ProcessedNotificationEventRepository repository;
    private final NotificationSender notificationSender;

    public OrderPaidNotificationService(ProcessedNotificationEventRepository repository,
        NotificationSender notificationSender) {

        this.repository = repository;
        this.notificationSender = notificationSender;
    }

    public void notifyOrderPaid(String orderId, long eventId) {
        boolean firstTime = repository.markProcessedIfFirstTime(eventId);
        if (!firstTime) {
            return;
        }

        try{
            notificationSender.send("Order is paid orderID:"+ orderId, String.valueOf(eventId));
        }
        catch(RuntimeException e) {
            repository.removeProcessedEvent(eventId);
            throw e;
        }
    }
}
