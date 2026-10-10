package com.vishesh.orderengine.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class OutboxBacklogMetricsTest {
    @Test
    public void reportsCurrentNumberOfOverdueOutboxEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);

        // A fixed clock makes the five-minute "overdue" boundary repeatable.
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
        OutboxObservabilityProperties properties = new OutboxObservabilityProperties(Duration.ofMinutes(5));
        LocalDateTime expectedCutoff = LocalDateTime.now(clock).minus(properties.overdueAfter());

        when(repository.countPendingDueBefore(expectedCutoff)).thenReturn(0L);

        new OutboxBacklogMetrics(registry,
                repository,
                properties,
                clock);

        // Gauges call their supplier when read, so changing the mocked database
        // answer changes the next observed value without re-registering a meter.
        assertEquals(0.0, registry.get("order.outbox.events.overdue").gauge().value());

        when(repository.countPendingDueBefore(expectedCutoff)).thenReturn(1L);

        assertEquals(1.0, registry.get("order.outbox.events.overdue").gauge().value());

        verify(repository, atLeastOnce()).countPendingDueBefore(expectedCutoff);
    }
}
