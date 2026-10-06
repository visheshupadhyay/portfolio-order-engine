package com.vishesh.orderengine.notification;

public class SmsProviderUnavailableException extends RuntimeException {

    public SmsProviderUnavailableException(String message) {
        super(message);
    }

    public SmsProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
