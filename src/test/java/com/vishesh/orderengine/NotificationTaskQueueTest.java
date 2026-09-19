package com.vishesh.orderengine;

/*
 * BlockingQueue revision: verifies FIFO delivery, a consumer waiting for work,
 * and a full queue rejecting a timed offer.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class NotificationTaskQueueTest {
    @Test
    public void takesTasksInFirstInFirstOutOrder() throws InterruptedException {
        NotificationTaskQueue notificationTaskQueue = new NotificationTaskQueue(2);
        notificationTaskQueue.enqueue("email-order-101");
        notificationTaskQueue.enqueue("sms-order-102");
        String task1 = notificationTaskQueue.takeNext();
        String task2 = notificationTaskQueue.takeNext();
        assertEquals("email-order-101",task1);
        assertEquals("sms-order-102",task2);
    }

    @Test
    public void takesTaskAddedLater() throws InterruptedException{
        NotificationTaskQueue notificationTaskQueue = new NotificationTaskQueue(2);
        String worker[] = new String[1];
        
        Thread thread = new Thread(() -> {
            try {
                worker[0] = notificationTaskQueue.takeNext();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        Thread thread2 = new Thread(()->{
            try {
                notificationTaskQueue.enqueue("email-order-101");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread2.start();
        thread.join();

        assertEquals("email-order-101", worker[0]);
    }

    @Test
    public void rejectsTaskWhenQueueIsFull() throws InterruptedException {
        NotificationTaskQueue notificationTaskQueue = new NotificationTaskQueue(1);
        notificationTaskQueue.enqueue("email-order-101");
        assertFalse(notificationTaskQueue.tryEnqueue("email-order-101",0));
    }
}
