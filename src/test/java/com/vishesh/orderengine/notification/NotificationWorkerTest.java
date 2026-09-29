package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.*;

/*
 * Worker revision: compares direct Thread execution with ExecutorService work
 * and uses completion waiting before checking a delivered notification.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;


public class NotificationWorkerTest {
    @Test
    protected void deliversQueuedTask() throws InterruptedException {
        NotificationTaskQueue notificationTaskQueue = new NotificationTaskQueue(1);
        WorkerRecordingNotifier workerRecordingNotifier = new WorkerRecordingNotifier("TEST");
        NotificationWorker notificationWorker = new NotificationWorker(notificationTaskQueue, workerRecordingNotifier);

        Thread thread1 = new Thread(()-> notificationWorker.run());
        thread1.start();
        Thread thread2 = new Thread(()-> {
            try {
                notificationTaskQueue.enqueue("Order order-101 is paid");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread1.join();
        thread2.join();
        assertEquals("Order order-101 is paid",workerRecordingNotifier.getMessage());
        
    }

    @Test
    public void executorRunsNotificationWorker() throws Exception  {
        NotificationTaskQueue notificationTaskQueue = new NotificationTaskQueue(1);
        WorkerRecordingNotifier workerRecordingNotifier = new WorkerRecordingNotifier("TEST");
        NotificationWorker notificationWorker = new NotificationWorker(notificationTaskQueue, workerRecordingNotifier);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(notificationWorker);
        notificationTaskQueue.enqueue("Order order-101 is paid");
        future.get();
        assertEquals("Order order-101 is paid",workerRecordingNotifier.getMessage());
        executor.shutdown();

    }
}

class WorkerRecordingNotifier extends AbstractNotifier{
    private String receivedMessage;
    public WorkerRecordingNotifier(String senderName) {
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
