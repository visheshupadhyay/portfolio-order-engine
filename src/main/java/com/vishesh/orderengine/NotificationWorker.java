package com.vishesh.orderengine;

/*
 * Plain-Java worker Runnable. It takes one queued task, sends it through a
 * notifier, and restores its interrupted status when the worker is stopped.
 */
public class NotificationWorker implements Runnable{
    private final NotificationTaskQueue notificationTaskQueue;
    private final AbstractNotifier abstractNotifier;

    public NotificationWorker(NotificationTaskQueue notificationTaskQueue, AbstractNotifier abstractNotifier) {
        this.abstractNotifier= abstractNotifier;
        this.notificationTaskQueue = notificationTaskQueue;
    }

    @Override
    public void run () {
        String task;
        try{
            task = notificationTaskQueue.takeNext();
            abstractNotifier.send(task);
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
    }
}
