package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.*;

/*
 * Plain-Java notification template: it validates a message once, then lets a
 * concrete notifier decide how that message is delivered. Configuration creates
 * the concrete Spring beans; this shared base class is not a bean itself.
 */

public abstract class AbstractNotifier {
    private final String senderName;

    public AbstractNotifier(String senderName) {
        this.senderName = senderName;
    }

    public void send(String message) {
        // Validate once in the shared template method; concrete notifiers only decide delivery mechanics.
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Blank messages are not allowed");
        }

        deliver(message);
    }

    protected abstract void deliver(String message);

    protected void deliver(String message, String idempotencyKey) {
        deliver(message);
    }

    public void send(String message, String idempotencyKey){
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Blank messages are not allowed");
        }

        if (idempotencyKey==null|| idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Blank idempotency keys are not allowed");
        }

        deliver(message, idempotencyKey);
    }

    public String getSenderName() {
        return this.senderName;
    }

}
