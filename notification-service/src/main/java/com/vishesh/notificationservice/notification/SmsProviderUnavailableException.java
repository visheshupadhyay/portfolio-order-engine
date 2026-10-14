package com.vishesh.notificationservice.notification;

public class SmsProviderUnavailableException extends RuntimeException {
    public SmsProviderUnavailableException(String message) {
        super(message);
    }

    public SmsProviderUnavailableException(String message, Throwable originalCause) {
        super(message, originalCause);
    }

}
