package com.vishesh.orderengine.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderPaymentMetricsListener {

    private final OrderPaymentMetrics orderPaymentMetrics;

    public OrderPaymentMetricsListener(OrderPaymentMetrics orderPaymentMetrics) {
        this.orderPaymentMetrics = orderPaymentMetrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid (OrderPaid orderPaid) {
        // AFTER_COMMIT is essential: the event was published inside pay(), but
        // the counter changes only after PAID and the outbox row are durable.
        orderPaymentMetrics.recordCompleted();
    }

}
