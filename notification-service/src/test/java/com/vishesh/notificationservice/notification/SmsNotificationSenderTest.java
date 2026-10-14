package com.vishesh.notificationservice.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

public class SmsNotificationSenderTest {

    @Test
    public void test() {
        SmsProviderClient smsProviderClient = mock(SmsProviderClient.class);
        SmsNotificationSender sender = new SmsNotificationSender(smsProviderClient);
        sender.send("Test message", "101");
        verify(smsProviderClient).send("NotificationService", "Test message", "101");
    }
}
