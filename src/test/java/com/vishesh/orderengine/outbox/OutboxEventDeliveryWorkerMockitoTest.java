package com.vishesh.orderengine.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vishesh.orderengine.message.OrderPaidEventPublisher;
import com.vishesh.orderengine.message.OrderPaidMessage;

/*
 * Fast unit tests for the worker's decision making. Kafka is represented by a
 * Mockito publisher here: these tests prove what the worker asks Kafka to do,
 * not whether a real broker can perform it (the Testcontainers tests do that).
 */
public class OutboxEventDeliveryWorkerMockitoTest {

	// These tests do not use a database. Mockito supplies controlled collaborator
	// behavior, then verifies the worker chose the correct repository/metric calls.
	private final OutboxDeliveryProperties deliveryProperties = new OutboxDeliveryProperties(
			false,
			Duration.ofSeconds(5),
			100,
			Duration.ofMinutes(1),
			Duration.ofMinutes(5),
			3);

	@Test
	public void marksClaimedEventSentWhenKafkaPublishSucceeds() {
		LocalDateTime now = LocalDateTime.of(2026,
				9,
				30,
				10,
				8);

		OutboxEvent event = new OutboxEvent(1L,
				"claim-A",
				"order-1",
				"ORDER_PAID",
				OutboxEventStatus.PROCESSING,
				0,
				now,
				now,
				now,
				null,
				null);

		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
		ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				deliveryProperties,
				metrics,
				publisher);

		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		worker.deliverReadyEvents(now, 10);

		verify(publisher).publish(messageCaptor.capture());
		OrderPaidMessage publishedMessage = messageCaptor.getValue();
		assertEquals(1L, publishedMessage.eventId());
		assertEquals("order-1", publishedMessage.orderId());

		verify(repository).markSent(1L, "claim-A", now);
		verify(metrics).recordDelivered();

		verify(repository, never()).rescheduleAfterFailure(anyLong(), anyString(), anyString(),
				any(LocalDateTime.class));

		verify(repository, never()).markFailed(anyLong(), anyString(), anyString());
	}

	@Test
	public void reschedulesClaimedEventWhenKafkaFailsBeforeMaximumAttempts() {
		LocalDateTime now = LocalDateTime.of(2026,
				9,
				30,
				10,
				40);

		OutboxEvent event = new OutboxEvent(2L,
				"claim-B",
				"order-2",
				"ORDER_PAID",
				OutboxEventStatus.PROCESSING,
				0,
				now,
				now,
				now,
				null,
				null);

		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				deliveryProperties,
				metrics,
				publisher);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
				.publish(any(OrderPaidMessage.class));

		worker.deliverReadyEvents(now, 10);

		verify(repository).rescheduleAfterFailure(
				2L,
				"claim-B",
				"Kafka unavailable",
				now.plus(deliveryProperties.retryDelay()));

		verify(metrics).recordRetried();

		verify(repository, never()).markSent(
				anyLong(),
				anyString(),
				any(LocalDateTime.class));

		verify(repository, never()).markFailed(
				anyLong(),
				anyString(),
				anyString());

		verify(metrics, never()).recordDelivered();
		verify(metrics, never()).recordFailed();

		verify(metrics).recordDeliveryRun(any(Duration.class));
		verify(publisher).publish(any(OrderPaidMessage.class));
	}

	@Test
	public void marksClaimedEventFailedWhenKafkaFailsAtMaximumAttempts() {
		LocalDateTime now = LocalDateTime.of(2026,
				9,
				30,
				10,
				40);

		OutboxEvent event = new OutboxEvent(3L,
				"claim-C",
				"order-3",
				"ORDER_PAID",
				OutboxEventStatus.PROCESSING,
				2,
				now,
				now,
				now,
				null,
				null);

		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				deliveryProperties,
				metrics,
				publisher);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
				.publish(any(OrderPaidMessage.class));

		worker.deliverReadyEvents(now, 10);

		verify(publisher).publish(any(OrderPaidMessage.class));

		verify(repository).markFailed(3L, "claim-C", "Kafka unavailable");

		verify(metrics).recordFailed();

		verify(repository, never()).markSent(
				anyLong(),
				anyString(),
				any(LocalDateTime.class));

		verify(repository, never()).rescheduleAfterFailure(anyLong(), anyString(), anyString(),
				any(LocalDateTime.class));

		verify(metrics, never()).recordDelivered();
		verify(metrics, never()).recordRetried();

		verify(metrics).recordDeliveryRun(any(Duration.class));
	}

	@Test
	public void continuesDeliveringLaterEventsWhenEarlierKafkaFails() {
		LocalDateTime now = LocalDateTime.of(2026,
				9,
				30,
				10,
				40);

		OutboxEvent event1 = new OutboxEvent(4L,
				"claim-D",
				"order-4",
				"ORDER_PAID",
				OutboxEventStatus.PROCESSING,
				0,
				now,
				now,
				now,
				null,
				null);

		OutboxEvent event2 = new OutboxEvent(5L,
				"claim-E",
				"order-5",
				"ORDER_PAID",
				OutboxEventStatus.PROCESSING,
				0,
				now,
				now,
				now,
				null,
				null);

		OutboxEventRepository repository = mock(OutboxEventRepository.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				deliveryProperties,
				metrics,
				publisher);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event1, event2));
		doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
				.publish(argThat(message -> message.eventId()== 4L));

		worker.deliverReadyEvents(now, 10);
		verify(publisher).publish(argThat(message -> message.eventId()== 4L));

		verify(publisher).publish(argThat(message -> message.eventId()== 5L));

		verify(repository).rescheduleAfterFailure(4L, "claim-D", "Kafka unavailable",
				now.plus(deliveryProperties.retryDelay()));
		verify(repository).markSent(5L, "claim-E", now);

		verify(metrics).recordDelivered();
		verify(metrics).recordRetried();
		verify(metrics, never()).recordFailed();
		verify(repository, never()).markFailed(anyLong(), anyString(), anyString());
	}
}
