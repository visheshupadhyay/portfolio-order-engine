package com.vishesh.orderengine;

/*
 * Plain-Java notification template: it validates a message once, then lets a
 * concrete notifier decide how that message is delivered. Configuration creates
 * the concrete Spring beans; this shared base class is not a bean itself.
 */

abstract class AbstractNotifier {
    private final String senderName;

    public AbstractNotifier(String senderName) {
        this.senderName = senderName;
    }

    public void send(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Blank messages are not allowed");
        }

        deliver(message);
    }

    public String getSenderName() {
        return this.senderName;
    }

    protected abstract void deliver(String message);
}
