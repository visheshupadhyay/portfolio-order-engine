package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.*;

/*
 * Email is one concrete notification channel. Configuration registers this
 * implementation as the default (@Primary) AbstractNotifier bean.
 */
public class EmailNotifier extends AbstractNotifier{
    public EmailNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        System.out.println("SenderName: " + getSenderName() + " Email message: " + message);
    }
}
