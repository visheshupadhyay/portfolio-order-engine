package com.vishesh.orderengine.support;

/*
 * Plain-Java semaphore helper for an external payment provider. It limits how
 * many calls may run at once and always releases a permit when work finishes.
 */
import java.util.concurrent.Semaphore;

public class PaymentProviderLimiter {
    private final Semaphore semaphore;

    public PaymentProviderLimiter(int maxConcurrentCalls) {
        if (maxConcurrentCalls > 0) {
            this.semaphore = new Semaphore(maxConcurrentCalls);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void runWithPermit(Runnable task) throws InterruptedException {
        semaphore.acquire();
        try {
            task.run();
        } finally {
            semaphore.release();
        }
    }
}
