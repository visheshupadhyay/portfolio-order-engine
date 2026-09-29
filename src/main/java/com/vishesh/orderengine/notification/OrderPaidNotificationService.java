package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final AbstractNotifier notifier;
    private final Set<Long> deliveredEventIds = ConcurrentHashMap.newKeySet();
    public OrderPaidNotificationService(@Qualifier("smsNotifier") AbstractNotifier notifier) {
        this.notifier=notifier;
    }

    public void notifyOrderPaid(Order order, long eventId) {
        // The outbox worker calls this only after a PENDING event has committed.
        if (!deliveredEventIds.add(eventId)) {
            return;
        }

        try {
            notifier.send("Order is paid orderID:" + order.getId(),String.valueOf(eventId));
        } catch (RuntimeException e) {
            deliveredEventIds.remove(eventId);
            throw e;

        }
    }
}
