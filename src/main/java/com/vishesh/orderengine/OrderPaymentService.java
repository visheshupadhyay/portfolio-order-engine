package com.vishesh.orderengine;

import org.springframework.stereotype.Service;

/*
 * Business workflow extracted from the controller so REST, jobs, or messages
 * can all pay an order through one place. Repeating a successful payment is
 * idempotent: it returns PAID without saving or notifying again.
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

    public Order pay(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            return order;
        }

        order.markPaid();
        orderRepository.save(order);
        orderPaidNotificationService.notifyOrderPaid(order);
        return order;
    }
}
