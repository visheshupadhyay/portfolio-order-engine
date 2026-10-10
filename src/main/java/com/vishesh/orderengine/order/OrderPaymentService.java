package com.vishesh.orderengine.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vishesh.orderengine.outbox.OutboxEventRepository;

/*
 * Business workflow extracted from the controller so REST, jobs, or messages
 * can all pay an order through one place. The repository performs the atomic
 * transition; only the request that changes CREATED -> PAID enqueues one notification event.
 */
@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderPaymentService(
        OrderRepository orderRepository,
        OutboxEventRepository outboxEventRepository,
        ApplicationEventPublisher applicationEventPublisher) {

        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // The PAID transition and PENDING outbox insert belong to one transaction:
    // commit makes both durable; an escaping unchecked exception rolls both back.
    // Actual external delivery is deliberately deferred to
    // OutboxEventDeliveryWorker.
    @Transactional
    public Order pay(Order order) {
        boolean transitioned = orderRepository.markPaidIfCreated(order.getId());
        Order storedOrder = orderRepository.findOrderById(order.getId()).orElseThrow();
        if (transitioned) {
            // Only the request that won CREATED -> PAID creates notification work.
            // Repeated payment requests must not create duplicate outbox events.
            outboxEventRepository.enqueueOrderPaid(order.getId());
            // The metrics listener deliberately waits until this surrounding
            // transaction commits. A rolled-back payment must never appear as a
            // completed payment on the dashboard.
            applicationEventPublisher.publishEvent(new OrderPaid(order.getId()));
        }
        return storedOrder;
    }
}
