package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.order.*;

/*
 * CompletableFuture revision: covers async success, exception wrapping,
 * timeouts, and cancelling queued work before notification delivery begins.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

public class AsyncNotificationServiceTest {

    @Test
    public void notifiesPaidOrderAsync() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AsyncRecordingNotifier recordingNotifier = new AsyncRecordingNotifier("TEST");
        AsyncNotificationService asyncNotificationService = new AsyncNotificationService(executor, recordingNotifier);
        CompletableFuture<Void> future = asyncNotificationService.notifyPaidAsync(new Order("order-101"));

        try {
            future.join();
            assertEquals("Order order-101 is paid", recordingNotifier.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void completesExceptionallyWhenNotificationFails() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        FailingNotifier failingNotifier = new FailingNotifier("FAIL");
        AsyncNotificationService asyncNotificationService = new AsyncNotificationService(executor, failingNotifier);
        CompletableFuture<Void> future = asyncNotificationService.notifyPaidAsync(new Order("order-fail"));
        try {
            CompletionException exception = assertThrows(CompletionException.class, () -> future.join());
            assertTrue(exception.getCause() instanceof IllegalStateException);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void timesOutWhenNotificationTakesTooLong()
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LongWaitNotifier longWaitNotifier = new LongWaitNotifier("LONG WAIT");
        AsyncNotificationService asyncNotificationService = new AsyncNotificationService(executor, longWaitNotifier);
        CompletableFuture<Void> future = asyncNotificationService.notifyPaidAsync(new Order("order-wait"));

        future.orTimeout(100, TimeUnit.MILLISECONDS);
        try {
            CompletionException exception = assertThrows(CompletionException.class, () -> future.join());
            assertTrue(exception.getCause() instanceof TimeoutException);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void cancelsQueuedNotificationBeforeDelivery() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch allowWorkerToFinish = new CountDownLatch(1);
        // Occupy the only executor worker so the notification remains queued and
        // cancellation can be tested deterministically.
        executor.submit(() -> {
            workerStarted.countDown();

            try {
                allowWorkerToFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        workerStarted.await();
        AsyncRecordingNotifier asyncRecordingNotifier = new AsyncRecordingNotifier("ORDER CANCEL");
        AsyncNotificationService asyncNotificationService = new AsyncNotificationService(executor,
                asyncRecordingNotifier);
        CompletableFuture<Void> future = asyncNotificationService.notifyPaidAsync(new Order("order-cancel"));
        boolean result = future.cancel(false);
        try {

            allowWorkerToFinish.countDown();
        } finally {
            executor.shutdown();
        }
        assertTrue(result);
        assertTrue(future.isCancelled());
        assertNull(asyncRecordingNotifier.getMessage());
    }
}

class AsyncRecordingNotifier extends AbstractNotifier {
    private String receivedMessage;

    public AsyncRecordingNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        receivedMessage = message;
    }

    public String getMessage() {
        return this.receivedMessage;
    }
}

class FailingNotifier extends AbstractNotifier {
    public FailingNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        throw new IllegalStateException();
    }
}

class LongWaitNotifier extends AbstractNotifier {
    public LongWaitNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }
}
