package com.vishesh.orderengine.notification;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Spring service that sends paid-order messages. Its qualifier deliberately
 * selects the SMS notifier, overriding the email notifier's default
 * (@Primary) choice.
 */

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vishesh.orderengine.order.Order;

@Service
public class OrderPaidNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(OrderPaidNotificationService.class);
    private final AbstractNotifier notifier;
    private final ProcessedNotificationEventRepository notificationRepository;
    public OrderPaidNotificationService(@Qualifier("smsNotifier") AbstractNotifier notifier, ProcessedNotificationEventRepository notificationRepository) {
        this.notifier = notifier;
        this.notificationRepository = notificationRepository;
    }

    public void notifyOrderPaid(Order order, long eventId) {
        // Reserve this permanent event ID before calling the external notifier.
        // A duplicate Kafka delivery then becomes a safe no-op instead of a
        // duplicate customer message.
        if (!notificationRepository.markProcessedIfFirstTime(eventId)) {
            logger.info(
                "Skipping duplicate paid-order notification: eventId={}, orderId={}",
                eventId,
                order.getId());

            return;
        }

        try {
            logger.debug(
                    "Sending paid-order notification: eventId={}, orderId={}, notifier={}",
                    eventId,
                    order.getId(),
                    notifier.getSenderName());

            notifier.send("Order is paid orderID:" + order.getId(), String.valueOf(eventId));

            logger.info(
                    "Paid-order notification sent: eventId={}, orderId={}",
                    eventId,
                    order.getId());
        } catch (RuntimeException e) {
            logger.warn(
                    "Paid-order notification failed; allowing retry: eventId={}, orderId={}, exceptionType={}",
                    eventId,
                    order.getId(),
                    e.getClass().getSimpleName());
            // We only keep the idempotency record after a successful send. Remove
            // it now so Spring Kafka's retry is allowed to attempt delivery again.
            notificationRepository.removeProcessedEvent(eventId);
            throw e;

        }
    }
}
