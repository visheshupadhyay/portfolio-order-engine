package com.vishesh.notificationservice.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.vishesh.notificationservice.integration.AbstractNotificationServiceIntegrationTest;
import com.vishesh.notificationservice.notification.SmsNotificationSender;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@DirtiesContext 
public class KafkaDuplicateDeliveryIntegrationTest extends AbstractNotificationServiceIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SmsNotificationSender smsNotificationSender;

    @Test
    public void sendsOnlyOneSmsWhenKafkaDeliversTheSameEventTwice() throws Exception {
        long eventId = System.nanoTime();
        String orderId = "duplicate-order-" + eventId;
        OrderPaidMessage orderPaidMessage = new OrderPaidMessage(eventId, orderId);
        String validJSON = objectMapper.writeValueAsString(orderPaidMessage);
        
        kafkaTemplate.send("order-paid", validJSON).get();
        kafkaTemplate.send("order-paid", validJSON).get();
        ArgumentCaptor<String> capturedMessage = ArgumentCaptor.forClass(String.class);
        verify(smsNotificationSender,timeout(15_000).times(1)).send(capturedMessage.capture(),eq(String.valueOf(eventId)));

        assertEquals(1, capturedMessage.getAllValues().size());
        assertTrue(capturedMessage.getAllValues().get(0).contains("Order is paid orderID:"+ orderId));
        long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_notification_events WHERE event_id = ?",
                Long.class,
                eventId);
        assertEquals(1L, rows);

    }
}
