package com.vishesh.orderengine;

/*
 * Spring service that sends paid-order messages. Its qualifier deliberately
 * selects the SMS notifier, overriding the email notifier's default
 * (@Primary) choice.
 */

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service 
public class OrderPaidNotificationService {
    private final AbstractNotifier notifier;

    public OrderPaidNotificationService(@Qualifier("smsNotifier") AbstractNotifier notifier) {
        this.notifier=notifier;
    }

    public void notifyOrderPaid(Order order) {
        // The outbox worker calls this only after a PENDING event has committed.
        notifier.send("Order is paid orderID:" + order.getId());
    }
}
