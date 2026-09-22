package com.vishesh.orderengine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Business workflow extracted from the controller so REST, jobs, or messages
 * can all pay an order through one place. The repository performs the atomic
 * transition; only the request that changes CREATED -> PAID sends a notification.
 */
@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderPaidNotificationService orderPaidNotificationService;

    public OrderPaymentService(
            OrderRepository orderRepository,
            OrderPaidNotificationService orderPaidNotificationService) {

        this.orderRepository = orderRepository;
        this.orderPaidNotificationService = orderPaidNotificationService;
    }

    // Spring commits the database transition/reload together, or rolls both back if
    // an unchecked exception escapes this Spring-managed service call.
    @Transactional
    public Order pay(Order order) {
        boolean transitioned = orderRepository.markPaidIfCreated(order.getId());
        Order storedOrder = orderRepository.findOrderById(order.getId()).orElseThrow();
        if (transitioned) {
            // This external side effect is intentionally sent only by the transition winner.
            orderPaidNotificationService.notifyOrderPaid(storedOrder);
        }

        return storedOrder;
    }
}
