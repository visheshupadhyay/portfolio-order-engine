package com.vishesh.orderengine.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vishesh.orderengine.message.OrderPaidEventPublisher;
import com.vishesh.orderengine.message.OrderPaidMessage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/* Unit tests for the worker policy: send, retry, stop after three failures, and continue a batch. */
/* Worker-flow tests prove a claimed event reaches SENT, retries/fails correctly,
 * and recovers a lease abandoned by a simulated crash. */
/*
 * Worker behaviour with an in-memory outbox and a small fake publisher.
 *
 * This layer is useful for reading the state transitions (PENDING, PROCESSING,
 * SENT, FAILED) without a database or a Kafka container hiding the flow.
 */
public class OutboxEventDeliveryWorkerTest {
        private final OutboxDeliveryProperties deliveryProperties = new OutboxDeliveryProperties(
                        false,
                        Duration.ofSeconds(5),
                        100,
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5),
                        3);

        @Test
        public void deliversReadyEventAndMarksItSent() {
                InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
                SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
                OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
                ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);
                OutboxDeliveryMetrics outboxDeliveryMetrics = new OutboxDeliveryMetrics(meterRegistry);
                OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                                inMemoryOutboxEventRepository,
                                deliveryProperties,
                                outboxDeliveryMetrics,
                                publisher);

                LocalDateTime now = LocalDateTime.now();
                // A normally returning notifier call is the only path that may mark SENT.
                outboxEventDeliveryWorker.deliverReadyEvents(now, 10);

                verify(publisher).publish(messageCaptor.capture());
                OrderPaidMessage publishedMessage = messageCaptor.getValue();
                assertEquals(1L, publishedMessage.eventId());
                assertEquals("worker-order-1", publishedMessage.orderId());

                assertEquals(1, inMemoryOutboxEventRepository.findAll().size());
                assertEquals(OutboxEventStatus.SENT, inMemoryOutboxEventRepository.findAll().get(0).status());
                assertEquals(now, inMemoryOutboxEventRepository.findAll().get(0).sentAt());
                assertNull(inMemoryOutboxEventRepository.findAll().get(0).lastError());
                assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now.plusMinutes(1), 10)
                                .size());
                assertEquals(1.0, meterRegistry.get("order.outbox.events.delivered").counter().count());
        }

        @Test
        public void reschedulesEventWhenKakfaPublishFailsBeforeMaximumAttempts() {
                InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();

                SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
                OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
                OutboxDeliveryMetrics outboxDeliveryMetrics = new OutboxDeliveryMetrics(meterRegistry);
                OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                                inMemoryOutboxEventRepository,
                                deliveryProperties,
                                outboxDeliveryMetrics,
                                publisher);

                doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
                                .publish(any(OrderPaidMessage.class));

                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
                LocalDateTime now = LocalDateTime.now();
                // Provider failure is captured as outbox state, not leaked to stop the worker.
                assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));
                OutboxEvent event = inMemoryOutboxEventRepository.findAll().get(0);
                assertEquals(OutboxEventStatus.PENDING, event.status());
                assertEquals(1, event.attemptCount());
                assertEquals("Kafka unavailable", event.lastError());
                assertNull(event.sentAt());
                assertEquals(now.plusMinutes(1), event.nextAttemptAt());
                assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now, 10).size());
                assertEquals(1, meterRegistry.get("order.outbox.events.retried").counter().count());
                verify(publisher).publish(any(OrderPaidMessage.class));
        }

        @Test
        public void marksEventFailedWhenThirdDeliveryAttemptFails() {
                InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-1");
                SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
                OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
                OutboxDeliveryMetrics outboxDeliveryMetrics = new OutboxDeliveryMetrics(meterRegistry);
                OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                                inMemoryOutboxEventRepository,
                                deliveryProperties,
                                outboxDeliveryMetrics,
                                publisher);
                LocalDateTime now = LocalDateTime.now();
                // Arrange two previous failed attempts; the next provider failure is attempt
                // three.
                LocalDateTime firstClaimAt = LocalDateTime.now();
                LocalDateTime firstRetryAt = firstClaimAt.plusMinutes(1);
                doThrow(new RuntimeException("Kafka unavailable")).when(publisher).publish(any(OrderPaidMessage.class));

                OutboxEvent event1 = inMemoryOutboxEventRepository
                                .claimPendingReadyForDelivery(firstClaimAt.plusSeconds(1), 10)
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
                assertEquals("Kafka unavailable", returnEvent.lastError());
                assertNull(returnEvent.sentAt());
                assertEquals(0, inMemoryOutboxEventRepository.claimPendingReadyForDelivery(now.plusMinutes(10), 10)
                                .size());
                assertEquals(1, meterRegistry.get("order.outbox.events.failed").counter().count());
                verify(publisher).publish(any(OrderPaidMessage.class));
        }

        @Test
        public void continuesDeliveringLaterEventsWhenOneKafkaPublishFails() {
                InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-a");
                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-b");
                SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
                OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
                OutboxDeliveryMetrics outboxDeliveryMetrics = new OutboxDeliveryMetrics(meterRegistry);
                OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                                inMemoryOutboxEventRepository,
                                deliveryProperties,
                                outboxDeliveryMetrics,
                                publisher);

                doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
                                .publish(argThat(message -> message.orderId().equals("worker-order-a")));
                LocalDateTime now = LocalDateTime.now();
                // The catch block is inside the worker loop, so order B must still be processed
                // after A fails.
                assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));
                verify(publisher).publish(argThat(message -> message.orderId().equals("worker-order-a")));
                verify(publisher).publish(argThat(message -> message.orderId().equals("worker-order-b")));
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
                assertEquals("Kafka unavailable", failedEvent.lastError());
                assertNull(failedEvent.sentAt());
                assertEquals(OutboxEventStatus.SENT, passedEvent.status());
                assertEquals(now, passedEvent.sentAt());
                assertNull(passedEvent.lastError());
                assertEquals(1, meterRegistry.get("order.outbox.events.retried").counter().count());
                assertEquals(1, meterRegistry.get("order.outbox.events.delivered").counter().count());
                assertEquals(1L, meterRegistry.get("order.outbox.delivery.run").timer().count());

        }

        @Test
        public void republishesRecoveredEventAfterPossibleCrashBeforeMarkingSent() {
                InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
                inMemoryOutboxEventRepository.enqueueOrderPaid("worker-order-a");
                OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
                SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
                OutboxDeliveryMetrics outboxDeliveryMetrics = new OutboxDeliveryMetrics(meterRegistry);
                OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                                inMemoryOutboxEventRepository,
                                deliveryProperties,
                                outboxDeliveryMetrics,
                                publisher);

                LocalDateTime workerRunAt = LocalDateTime.now().plusMinutes(10);
                LocalDateTime oldClaimAt = workerRunAt.minusMinutes(6);
                OutboxEvent manualEvent = inMemoryOutboxEventRepository.claimPendingReadyForDelivery(oldClaimAt, 1)
                                .get(0);

                publisher.publish(new OrderPaidMessage(manualEvent.id(), "worker-order-a"));
                assertNotNull(manualEvent);
                assertEquals(OutboxEventStatus.PROCESSING, manualEvent.status());
                assertNotNull(manualEvent.claimToken());
                outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 10);
                ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);

                verify(publisher, times(2)).publish(messageCaptor.capture());

                List<OrderPaidMessage> publishedMessages = messageCaptor.getAllValues();

                assertEquals(2, publishedMessages.size());

                assertEquals(manualEvent.id(), publishedMessages.get(0).eventId());
                assertEquals("worker-order-a", publishedMessages.get(0).orderId());
                assertEquals(manualEvent.id(), publishedMessages.get(1).eventId());
                assertEquals("worker-order-a", publishedMessages.get(1).orderId());
        }
}
