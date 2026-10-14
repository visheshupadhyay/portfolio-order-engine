package com.vishesh.notificationservice.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class OrderPaidMessageCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsCurrentOrderPaidMessage() throws Exception {
        String json = "{\"eventId\":101,\"orderId\":\"order-501\"}";

        OrderPaidMessage message = objectMapper.readValue(json, OrderPaidMessage.class);

        assertEquals(101L, message.eventId());
        assertEquals("order-501", message.orderId());
    }

    @Test
    void ignoresOptionalFieldAddedByFutureOrderEngine() throws Exception {
        String json = """
                {
                  "eventId": 101,
                  "orderId": "order-501",
                  "paymentMethod": "CARD"
                }
                """;

        OrderPaidMessage message = objectMapper.readValue(json, OrderPaidMessage.class);

        assertEquals(101L, message.eventId());
        assertEquals("order-501", message.orderId());
    }
}