package com.vishesh.orderengine;

/*
 * Semaphore revision: one permit lets the first payment call enter while the
 * second waits until the first call releases it.
 */
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class PaymentProviderLimiterTest {
    @Test
    public void allowsSecondCallOnlyAfterFirstCallFinishes() throws InterruptedException {
        PaymentProviderLimiter paymentProviderLimiter = new PaymentProviderLimiter(1);
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        CountDownLatch secondCallEntered = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                paymentProviderLimiter.runWithPermit(() -> {
                    firstCallEntered.countDown();
                    try {
                        // Keep the only permit occupied until the test proves
                        // that the second call cannot enter yet.
                        releaseFirstCall.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread1.start();
        firstCallEntered.await();
        Thread thread2 = new Thread(() -> {
            try {
                paymentProviderLimiter.runWithPermit(() -> {
                    secondCallEntered.countDown();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread2.start();
    

        boolean secondEnteredTooEarly;
        try {
            secondEnteredTooEarly = secondCallEntered.await(100, TimeUnit.MILLISECONDS);
            assertFalse(secondEnteredTooEarly);
        } finally {
            releaseFirstCall.countDown();
        }

        thread1.join();
        thread2.join();

        assertTrue(secondCallEntered.getCount() == 0);
    }
}
