package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/* Unit tests for the worker policy: send, retry, stop after three failures, and continue a batch. */
/* Worker-flow tests prove a claimed event reaches SENT, retries/fails correctly,
 * and recovers a lease abandoned by a simulated crash. */
public class OutboxEventDeliveryWorkerTest {

    @Test
    public void deliversReadyEventAndMarksItSent() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
        TestingNotifier testingNotifier = new TestingNotifier("TEST");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(testingNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                inMemoryOutboxEventRepository, orderPaidNotificationService);

        LocalDateTime now = LocalDateTime.now();
        // A normally returning notifier call is the only path that may mark SENT.
        outboxEventDeliveryWorker.deliverReadyEvents(now, 10);
        assertEquals("Order is paid orderID:worker-order-1", testingNotifier.getMessage());
        assertEquals(1, inMemoryOutboxEventRepository.findAll().size());
        assertEquals(OutboxEventStatus.SENT, inMemoryOutboxEventRepository.findAll().get(0).status());
        assertEquals(now, inMemoryOutboxEventRepository.findAll().get(0).sentAt());
        assertNull(inMemoryOutboxEventRepository.findAll().get(0).lastError());
        assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now.plusMinutes(1), 10).size());
    }

    @Test
    public void reschedulesEventWhenNotificationFailsBeforeMaximumAttempts() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        ErrorNotifier errorNotifier = new ErrorNotifier("Error");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(errorNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                inMemoryOutboxEventRepository, orderPaidNotificationService);
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
        LocalDateTime now = LocalDateTime.now();
        // Provider failure is captured as outbox state, not leaked to stop the worker.
        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));
        OutboxEvent event = inMemoryOutboxEventRepository.findAll().get(0);
        assertEquals(OutboxEventStatus.PENDING, event.status());
        assertEquals(1, event.attemptCount());
        assertEquals("SMS provider unavailable", event.lastError());
        assertNull(event.sentAt());
        assertEquals(now.plusMinutes(1), event.nextAttemptAt());
        assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now, 10).size());

    }

    @Test
    public void marksEventFailedWhenThirdDeliveryAttemptFails() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
        ErrorNotifier errorNotifier = new ErrorNotifier("Error");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(errorNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                inMemoryOutboxEventRepository, orderPaidNotificationService);
        LocalDateTime now = LocalDateTime.now();
        // Arrange two previous failed attempts; the next provider failure is attempt
        // three.
        LocalDateTime firstClaimAt = LocalDateTime.now();
        LocalDateTime firstRetryAt = firstClaimAt.plusMinutes(1);
        OutboxEvent event1 = inMemoryOutboxEventRepository.claimPendingReadyForDelivery(firstClaimAt.plusSeconds(1), 10)
                .get(0);
        inMemoryOutboxEventRepository.rescheduleAfterFailure(event1.id(), event1.claimToken(), "first failure",
                firstRetryAt);
        OutboxEvent event2 = inMemoryOutboxEventRepository
                .claimPendingReadyForDelivery(firstRetryAt.plusSeconds(10), 10).get(0);
        inMemoryOutboxEventRepository.rescheduleAfterFailure(event2.id(), event2.claimToken(), "second failure",
                firstRetryAt.plusMinutes(2));
        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(firstRetryAt.plusMinutes(3), 10));
        OutboxEvent returnEvent = inMemoryOutboxEventRepository.findAll().get(0);
        assertEquals(OutboxEventStatus.FAILED, returnEvent.status());
        assertEquals(3, returnEvent.attemptCount());
        assertEquals("SMS provider unavailable", returnEvent.lastError());
        assertNull(returnEvent.sentAt());
        assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now.plusMinutes(10), 10).size());
    }

    @Test
    public void continuesDeliveringLaterEventsWhenOneNotificationFails() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-a");
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-b");
        WorkerTestingNotifier errorNotifier = new WorkerTestingNotifier("Error");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(errorNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                inMemoryOutboxEventRepository, orderPaidNotificationService);

        LocalDateTime now = LocalDateTime.now();
        // The catch block is inside the worker loop, so order B must still be processed
        // after A fails.
        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));
        List<OutboxEvent> events = inMemoryOutboxEventRepository.findAll();
        OutboxEvent failedEvent = events.stream()
                .filter(event -> event.orderId().equals("worker-order-a"))
                .findFirst()
                .orElseThrow();

        OutboxEvent passedEvent = events.stream()
                .filter(event -> event.orderId().equals("worker-order-b"))
                .findFirst()
                .orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, failedEvent.status());
        assertEquals(1, failedEvent.attemptCount());
        assertEquals("SMS provider unavailable", failedEvent.lastError());
        assertNull(failedEvent.sentAt());
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(now, passedEvent.sentAt());
        assertNull(passedEvent.lastError());
        assertEquals("Order is paid orderID:worker-order-b", errorNotifier.getMessage());

    }

    @Test
    public void releasesExpiredClaimAndDeliversEventDuringWorkerRun() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-a");
        TestingNotifier testingNotifier = new TestingNotifier("Success");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(testingNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                inMemoryOutboxEventRepository, orderPaidNotificationService);
        
        LocalDateTime workerRunAt = LocalDateTime.now().plusMinutes(10);
        LocalDateTime oldClaimAt = workerRunAt.minusMinutes(6);
        OutboxEvent manualEvent = inMemoryOutboxEventRepository.claimPendingReadyForDelivery(oldClaimAt, 1).get(0);
        assertNotNull(manualEvent);
        assertEquals(OutboxEventStatus.PROCESSING, manualEvent.status());
        assertNotNull(manualEvent.claimToken());
        outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 10);

        assertEquals("Order is paid orderID:worker-order-a",testingNotifier.getMessage());
        OutboxEvent passedEvent = inMemoryOutboxEventRepository.findAll().stream()
                .filter(event -> event.orderId().equals("worker-order-a"))
                .findFirst()
                .orElseThrow();  
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertNull(passedEvent.claimToken());
        assertNull(passedEvent.claimedAt());
        assertEquals(0,passedEvent.attemptCount());
    }

}

class ErrorNotifier extends AbstractNotifier {

    public ErrorNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        throw new RuntimeException("SMS provider unavailable");
    }
}

class TestingNotifier extends AbstractNotifier {
    private String message;

    public TestingNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }
}

class WorkerTestingNotifier extends AbstractNotifier {
    private String message;

    public WorkerTestingNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        if (message.contains("worker-order-a")) {
            // Deterministically fail only the first event in the mixed-batch test.
            throw new RuntimeException("SMS provider unavailable");
        } else {
            this.message = message;
        }
    }

    public String getMessage() {
        return this.message;
    }
}
