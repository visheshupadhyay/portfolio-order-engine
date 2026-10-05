package com.vishesh.orderengine.message;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;

import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.Order;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderPaidNotificationConsumer {
    private final ObjectMapper objectMapper;
    private final OrderPaidNotificationService service;

    public OrderPaidNotificationConsumer(ObjectMapper objectMapper,
            OrderPaidNotificationService service) {

        this.objectMapper = objectMapper;
        this.service = service;
    }

    // A notification provider can have a short outage. Spring retries such a
    // failure asynchronously; after the attempts are exhausted, it moves the
    // original record to order-paid.DLT instead of blocking later Kafka records.
    @RetryableTopic(attempts = "3",backOff = @BackOff(delay = 1000), dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "${messaging.order-paid-topic}", groupId = "${messaging.order-paid-notification-group}")
    public void consumeOrderPaid(String jsonText) {
        try{
            // Rebuild the event received from Kafka. @JsonIgnoreProperties on
            // OrderPaidMessage makes this tolerant of extra future JSON fields.
            OrderPaidMessage recievedOrder = objectMapper.readValue(jsonText, OrderPaidMessage.class);
            Order order = new Order(recievedOrder.orderId());

            // The notification service owns duplicate protection. Kafka normally
            // provides at-least-once delivery, so this same event may arrive again.
            service.notifyOrderPaid(order, recievedOrder.eventId());
        }
        catch(JacksonException e) {
            // Bad JSON cannot succeed by retrying unchanged text. Throwing lets
            // @RetryableTopic eventually route it to the DLT for investigation.
            throw new IllegalStateException("Json text is malformed",e);
        }
        
    }
}
