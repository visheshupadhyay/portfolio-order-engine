package com.vishesh.notificationservice.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.vishesh.notificationservice.notification.SmsProviderUnavailableException;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@DirtiesContext 
public class KafkaTemporaryNotificationFailureIntegrationTest extends AbstractNotificationServiceIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private SmsNotificationSender smsNotificationSender;

    @Test
    public void retriesTemporarySmsFailureAndKeepsOneProcessedEvent() throws Exception {
        long eventId = System.nanoTime();
        String orderId = "microservice-order-101";

        doThrow(new SmsProviderUnavailableException("Provider down"))
                .doNothing()
                .when(smsNotificationSender)
                .send(anyString(), anyString());

        OrderPaidMessage orderPaidMessage = new OrderPaidMessage(eventId, orderId);
        String validJSON = objectMapper.writeValueAsString(orderPaidMessage);

        kafkaTemplate.send("order-paid", validJSON).get();

        ArgumentCaptor<String> capturedMessage = ArgumentCaptor.forClass(String.class);
        verify(smsNotificationSender, timeout(15_000).times(2))
                .send(capturedMessage.capture(), eq(String.valueOf(eventId)));

        long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_notification_events WHERE event_id = ?",
                Long.class,
                eventId);

        assertEquals(1L, rows);
        String expectedMessage = "Order is paid orderID:" + orderId;
        assertEquals(2, capturedMessage.getAllValues().size());
        assertEquals(expectedMessage, capturedMessage.getAllValues().get(0));
        assertEquals(expectedMessage, capturedMessage.getAllValues().get(1));
    }
}
