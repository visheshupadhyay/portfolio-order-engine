package com.vishesh.orderengine.integration;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.order.OrderStatus;
import com.vishesh.orderengine.outbox.JdbcOutboxEventRepository;
import com.vishesh.orderengine.outbox.OutboxEvent;
import com.vishesh.orderengine.outbox.OutboxEventRepository;
import com.vishesh.orderengine.outbox.OutboxEventRowMapper;
import com.vishesh.orderengine.outbox.OutboxEventStatus;

@SpringBootTest
@ActiveProfiles("postgres")
public class PostgresSpringBootTestcontainerTest extends AbstractPostgresIntegrationTest {
    // This proves Spring itself—not just raw JDBC—uses the container URL from
    // the shared base while the postgres profile selects the JDBC adapters.
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    private String savedOrderId;

    @Test
    void connectsSpringBootToTestcontainerPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2));
        }
    }

    @AfterEach
    public void cleanUpDatabase() {
        // This test commits real rows, so remove them explicitly to keep the
        // shared Testcontainers database safe for later test classes.
        if (savedOrderId != null) {
            jdbcTemplate.update("Delete from outbox_events where order_id=?", savedOrderId);
            jdbcTemplate.update("Delete from orders where Id=?", savedOrderId);
        }
    }

    @Test
    public void enqueuesOrderPaidEventUsingSpringAndContainerizedPostgres() throws Exception {
        savedOrderId = UUID.randomUUID().toString();

        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        assertInstanceOf(JdbcOutboxEventRepository.class, outboxEventRepository);
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
                order.getStatus().name());
        outboxEventRepository.enqueueOrderPaid(savedOrderId);
        List<OutboxEvent> events = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);
        assertEquals(1,events.size());
        assertEquals(savedOrderId,events.get(0).orderId());
        assertEquals("ORDER_PAID",events.get(0).eventType());
        assertEquals(0, events.get(0).attemptCount());
        assertNull(events.get(0).claimedAt());
        assertNull(events.get(0).sentAt());
        assertEquals(OutboxEventStatus.PENDING, events.get(0).status());
        try (Connection connection = dataSource.getConnection()) {
           assertEquals(postgres.getJdbcUrl(), connection.getMetaData().getURL());
        }
        
    }
}
