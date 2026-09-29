package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.*;

/*
 * Plain-Java bounded producer/consumer queue. Producers can block while the
 * queue is full, consumers can block while it is empty, and timed offers can
 * fail instead of waiting forever.
 */
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
public class NotificationTaskQueue {
    private final BlockingQueue<String> blockingQueue;

    public NotificationTaskQueue(int capacity) {
        blockingQueue = new ArrayBlockingQueue<>(capacity);
    }

    public void enqueue(String task) throws InterruptedException {
        blockingQueue.put(task);
    }

    public String takeNext() throws InterruptedException {
        return blockingQueue.take();
    }

    public boolean tryEnqueue(String task, long timeoutMillis) throws InterruptedException {
        return blockingQueue.offer(task,timeoutMillis,TimeUnit.MILLISECONDS);
    }
}
