package com.vishesh.orderengine.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.Order;

import tools.jackson.databind.ObjectMapper;

public class OrderPaidNotificationConsumerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderPaidNotificationService orderPaidNotificationService = mock(OrderPaidNotificationService.class);
    private final OrderPaidNotificationConsumer consumer = new OrderPaidNotificationConsumer(objectMapper, orderPaidNotificationService);

    @Test
    public void sendsNotificationForValidOrderPaidMessage() {
        OrderPaidMessage message = new OrderPaidMessage(1L, "kafka-order-101");
        String jsonText = objectMapper.writeValueAsString(message);
        consumer.consumeOrderPaid(jsonText);
        ArgumentCaptor<Order> receivedMessage = ArgumentCaptor.forClass(Order.class);

        verify(orderPaidNotificationService).notifyOrderPaid(receivedMessage.capture(), eq(1L));

        Order orderSentToNotifier = receivedMessage.getValue();

        assertEquals("kafka-order-101", orderSentToNotifier.getId());
    }

    @Test
    public void rejectsMalformedKafkaMessage() {
        assertThrows(IllegalStateException.class, ()->consumer.consumeOrderPaid("1"));
        verifyNoInteractions(orderPaidNotificationService);
    }
}
