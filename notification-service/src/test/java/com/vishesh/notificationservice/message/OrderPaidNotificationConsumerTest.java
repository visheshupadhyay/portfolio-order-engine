package com.vishesh.notificationservice.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vishesh.notificationservice.notification.OrderPaidNotificationService;

import tools.jackson.databind.ObjectMapper;

public class OrderPaidNotificationConsumerTest {
    private ObjectMapper objectMapper = new ObjectMapper();
    private OrderPaidNotificationService notificationService = mock(OrderPaidNotificationService.class);

    @Test
    public void passesValidKafkaMessageToNotificationService() {
        String orderId = "microservice-order-501";
        long eventId = 101L;
        String json = objectMapper.writeValueAsString(Map.of("eventId",eventId,"orderId",orderId));
        OrderPaidNotificationConsumer consumer = new OrderPaidNotificationConsumer(objectMapper, notificationService);
        consumer.consumeOrderPaid(json);
        verify(notificationService).notifyOrderPaid(orderId, eventId);

    }

    @Test
    public void rejectsMalformedKafkaMessage() {
        String malformedJson = """
                {
                  
                """;

        OrderPaidNotificationConsumer consumer = new OrderPaidNotificationConsumer(objectMapper, notificationService);
        RuntimeException exception = assertThrows(RuntimeException.class,()->consumer.consumeOrderPaid(malformedJson));
        assertEquals("Could not read order-paid Kafka message",exception.getLocalizedMessage());
        verifyNoInteractions(notificationService);
    }
}
