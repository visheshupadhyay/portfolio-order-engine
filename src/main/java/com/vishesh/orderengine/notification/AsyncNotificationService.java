package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.*;

/*
 * Plain-Java async example, not a Spring bean. A caller supplies the executor
 * and notifier so CompletableFuture can send a notification off the caller's
 * thread and report completion, failure, or cancellation.
 */
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.vishesh.orderengine.order.Order;

public class AsyncNotificationService {
    private final Executor executor;
    private final AbstractNotifier abstractNotifier;

    public AsyncNotificationService(Executor executor, AbstractNotifier abstractNotifier) {
        this.abstractNotifier = abstractNotifier;
        this.executor = executor;
    }

    public CompletableFuture<Void> notifyPaidAsync(Order order) {
        return CompletableFuture.runAsync(() -> abstractNotifier.send("Order " + order.getId() + " is paid"),executor);
    }
}
