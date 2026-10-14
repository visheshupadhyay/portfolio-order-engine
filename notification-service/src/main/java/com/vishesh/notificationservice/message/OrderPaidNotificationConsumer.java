package com.vishesh.notificationservice.message;

import tools.jackson.core.JacksonException;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

import com.vishesh.notificationservice.notification.OrderPaidNotificationService;

import tools.jackson.databind.ObjectMapper;

@Component
public class OrderPaidNotificationConsumer {
    private final ObjectMapper objectMapper;
    private final OrderPaidNotificationService notificationService;

    public OrderPaidNotificationConsumer(ObjectMapper objectMapper, OrderPaidNotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000), dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "${notification.kafka.order-paid-topic}", groupId = "${notification.kafka.consumer-group}")
    public void consumeOrderPaid(String json) {
        try {
            OrderPaidMessage message = objectMapper.readValue(json, OrderPaidMessage.class);
            notificationService.notifyOrderPaid(message.orderId(), message.eventId());
        } catch (JacksonException e) {
            throw new RuntimeException("Could not read order-paid Kafka message", e);
        }

    }
}
