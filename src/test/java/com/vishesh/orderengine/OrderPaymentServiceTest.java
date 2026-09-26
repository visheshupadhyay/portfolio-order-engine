package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * Fast unit tests for the transactional-outbox decision in the payment workflow.
 * The service records durable PENDING work; a separate worker performs delivery later.
 */
public class OrderPaymentServiceTest {

    @Test
    public void marksOrderPaidAndEnqueuesPendingNotificationEvent() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        Order order = new Order("order-101", OrderStatus.CREATED);
        inMemoryOrderRepository.save(order);
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        OrderPaymentService orderPaymentService = new OrderPaymentService(inMemoryOrderRepository,
                inMemoryOutboxEventRepository);
        Order returnedOrder = orderPaymentService.pay(order);

        // A successful state transition creates exactly one durable delivery task.
        List<OutboxEvent> events = inMemoryOutboxEventRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(returnedOrder.getId(), events.get(0).orderId());
        assertEquals(OutboxEventStatus.PENDING, events.get(0).status());
        assertEquals(0, events.get(0).attemptCount());
        assertEquals(OrderStatus.PAID, returnedOrder.getStatus());

    }

    @Test
    public void doesNotEnqueueSecondEventForAlreadyPaidOrder() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        Order order = new Order("order-101");
        inMemoryOrderRepository.save(order);
        OrderPaymentService orderPaymentService = new OrderPaymentService(inMemoryOrderRepository,
                inMemoryOutboxEventRepository);
        Order returnedOrderCopy1 = orderPaymentService.pay(order);
        Order returnedOrderCopy2 = orderPaymentService.pay(new Order("order-101"));
        assertEquals(OrderStatus.PAID, returnedOrderCopy1.getStatus());
        assertEquals(OrderStatus.PAID, returnedOrderCopy2.getStatus());
        // A second request loses the CREATED -> PAID transition and creates no second event.
        List<OutboxEvent> events = inMemoryOutboxEventRepository.findAll();
        assertEquals(1, events.size());
    }
}
