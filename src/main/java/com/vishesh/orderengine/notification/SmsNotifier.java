package com.vishesh.orderengine.notification;

/*
 * SMS is a concrete notification channel. Configuration registers it under the
 * smsNotifier bean name, which the paid-notification service selects with a
 * qualifier.
 */
public class SmsNotifier extends AbstractNotifier {

    private final SmsProviderClient smsProviderClient;

    public SmsNotifier(String senderName, SmsProviderClient smsProviderClient) {
        super(senderName);
        this.smsProviderClient = smsProviderClient;
    }

    @Override
    protected void deliver(String message) {
        System.out.println("SenderName: " + getSenderName() + " SMS message: " + message);
    }

    @Override
    protected void deliver(String message, String idempotencyKey) {
        smsProviderClient.send(
                getSenderName(),
                message,
                idempotencyKey);
    }

}
