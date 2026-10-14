package com.vishesh.notificationservice.notification;

import org.springframework.stereotype.Component;

@Component 
public class SmsNotificationSender implements NotificationSender {
    private final SmsProviderClient smsProviderClient;
    
    public SmsNotificationSender(SmsProviderClient smsProviderClient) {
        this.smsProviderClient = smsProviderClient;
    }

    @Override
    public void send(String message, String idempotencyKey) {
        smsProviderClient.send("NotificationService", message, idempotencyKey);
    }  
    
}
