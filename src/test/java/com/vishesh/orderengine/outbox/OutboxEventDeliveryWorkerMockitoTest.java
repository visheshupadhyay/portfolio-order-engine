package com.vishesh.orderengine.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.Order;

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
	public void marksClaimedEventSentWhenNotificationSucceeds() {
		ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
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
		OrderPaidNotificationService notificationService = mock(OrderPaidNotificationService.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);

		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				notificationService,
				deliveryProperties,
				metrics);

		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		worker.deliverReadyEvents(now, 10);

		verify(notificationService).notifyOrderPaid(orderCaptor.capture(), eq(1L));
		verify(repository).markSent(1L, "claim-A", now);
		verify(metrics).recordDelivered();

		verify(repository, never()).rescheduleAfterFailure(anyLong(), anyString(), anyString(),
				any(LocalDateTime.class));

		verify(repository, never()).markFailed(anyLong(), anyString(), anyString());
		assertEquals("order-1", orderCaptor.getValue().getId());
	}

	@Test
	public void reschedulesClaimedEventWhenNotificationFailsBeforeMaximumAttempts() {
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
		OrderPaidNotificationService notificationService = mock(OrderPaidNotificationService.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				notificationService,
				deliveryProperties,
				metrics);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		doThrow(new RuntimeException("Provider unavailable")).when(notificationService)
				.notifyOrderPaid(any(Order.class), eq(2L));

		worker.deliverReadyEvents(now, 10);

		verify(notificationService).notifyOrderPaid(any(Order.class), eq(2L));

		verify(repository).rescheduleAfterFailure(
				2L,
				"claim-B",
				"Provider unavailable",
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
	}

	@Test
	public void marksClaimedEventFailedWhenNotificationFailsAtMaximumAttempts() {
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
		OrderPaidNotificationService notificationService = mock(OrderPaidNotificationService.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				notificationService,
				deliveryProperties,
				metrics);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event));
		doThrow(new RuntimeException("Provider unavailable")).when(notificationService)
				.notifyOrderPaid(any(Order.class), eq(3L));

		worker.deliverReadyEvents(now, 10);

		verify(notificationService).notifyOrderPaid(any(Order.class), eq(3L));

		verify(repository).markFailed(3L, "claim-C", "Provider unavailable");

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
	public void continuesDeliveringLaterEventsWhenEarlierNotificationFails() {
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
		OrderPaidNotificationService notificationService = mock(OrderPaidNotificationService.class);
		OutboxDeliveryMetrics metrics = mock(OutboxDeliveryMetrics.class);
		OutboxEventDeliveryWorker worker = new OutboxEventDeliveryWorker(repository,
				notificationService,
				deliveryProperties,
				metrics);

		when(repository.releaseExpiredClaims(now.minus(deliveryProperties.claimTimeout()))).thenReturn(0);
		when(repository.claimPendingReadyForDelivery(now, 10)).thenReturn(List.of(event1, event2));
		doThrow(new RuntimeException("Provider unavailable")).when(notificationService)
				.notifyOrderPaid(any(Order.class), eq(4L));

		worker.deliverReadyEvents(now, 10);
		verify(notificationService).notifyOrderPaid(any(Order.class), eq(4L));

		verify(notificationService).notifyOrderPaid(any(Order.class), eq(5L));

		verify(repository).rescheduleAfterFailure(4L, "claim-D", "Provider unavailable",
				now.plus(deliveryProperties.retryDelay()));
		verify(repository).markSent(5L, "claim-E", now);

		verify(metrics).recordDelivered();
		verify(metrics).recordRetried();
		verify(metrics, never()).recordFailed();
		verify(repository, never()).markFailed(
				anyLong(),
				anyString(),
				anyString());
	}
}
