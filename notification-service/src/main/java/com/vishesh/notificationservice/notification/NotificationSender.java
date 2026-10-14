package com.vishesh.notificationservice.notification;

public interface NotificationSender {
    void send(String message, String idempotencyKey);
}