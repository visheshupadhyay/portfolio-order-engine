package com.vishesh.orderengine;

/*
 * SMS is a concrete notification channel. Configuration registers it under the
 * smsNotifier bean name, which the paid-notification service selects with a
 * qualifier.
 */
public class SmsNotifier extends AbstractNotifier{
    public SmsNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        System.out.println("SenderName: " + getSenderName() + " SMS message: " + message);
    }
}
