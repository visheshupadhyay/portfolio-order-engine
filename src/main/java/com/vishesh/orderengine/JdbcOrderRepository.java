package com.vishesh.orderengine;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/*
 * PostgreSQL implementation of OrderRepository. JdbcTemplate handles connection
 * acquisition/release while this class owns the SQL and row-to-domain mapping.
 */
@Profile("postgres")
@Repository
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Order order) {
        // Save-style UPSERT is appropriate when the caller intentionally persists the
        // current state of an existing order.
        jdbcTemplate.update("INSERT INTO orders (id, status)\r\n" + //
                "VALUES (?, ?)\r\n" + //
                "ON CONFLICT (id)\r\n" + //
                "DO UPDATE SET status = EXCLUDED.status", order.getId(), order.getStatus().name());
    }

    @Override
    public Optional<Order> findOrderById(String orderId) {
        List<Order> list = jdbcTemplate.query("SELECT id, status\r\n" + //
                "FROM orders\r\n" + //
                "WHERE id = ?",
                new OrderRowMapper(),
                orderId);

        return list.stream().findFirst();
    }

    @Override
    public List<Order> findAll() {
        String sql = "SELECT id, status FROM orders";
        return jdbcTemplate.query(sql, new OrderRowMapper());
    }

    @Override
    public boolean markPaidIfCreated(String orderId) {
        // The status condition is part of one SQL statement, making the transition
        // atomic across concurrent PostgreSQL requests.
        int rows = jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ? AND status = 'CREATED'",
                orderId);
        return rows == 1;
    }

    @Override
    public boolean createIfAbsent(Order order) {
        // Unlike save(), creation must never overwrite an existing order. PostgreSQL
        // reports 1 inserted row for success and 0 for the duplicate path.
        int rows = jdbcTemplate.update(
                "INSERT INTO orders (id, status) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                order.getId(),
                order.getStatus().name());

        return rows == 1;
    }

    @Override
    public OrderPage findPage(OrderStatus status, int page, int size) {
        // Offset pages require two queries: one slice for content and one COUNT for navigation metadata.
        int offset = page * size;
        List<Order> content;
        long totalElements;

        if (status == null) {
            content = jdbcTemplate.query("SELECT id, status FROM orders ORDER BY id ASC LIMIT ? OFFSET ?",
                    new OrderRowMapper(), size, offset);
            totalElements = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        } else {
            content = jdbcTemplate.query(
                    "SELECT id, status FROM orders WHERE status = ? ORDER BY id ASC LIMIT ? OFFSET ?",
                    new OrderRowMapper(), status.name(), size, offset);
            totalElements = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders where status=?", Long.class,
                    status.name());
        }
        return new OrderPage(content, page, size, totalElements);
    }

    @Override
    public OrderCursorPage findAfter(OrderStatus status, String after, int size) {
        // Cursor traversal avoids OFFSET. LIMIT size + 1 reveals whether another batch exists.
        List<Order> candidates;

        if (status == null && after == null) {
            candidates = jdbcTemplate.query("SELECT id, status FROM orders ORDER BY id ASC LIMIT ?",
                    new OrderRowMapper(), size + 1);
        } else if (status == null) {
            candidates = jdbcTemplate.query("SELECT id, status FROM orders WHERE id > ? ORDER BY id ASC LIMIT ?",
                    new OrderRowMapper(), after, size + 1);

        } else if (after == null) {
            candidates = jdbcTemplate.query("SELECT id, status FROM orders WHERE status = ? ORDER BY id ASC LIMIT ?",
                    new OrderRowMapper(), status.name(), size + 1);

        } else {
            candidates = jdbcTemplate.query(
                    "SELECT id, status FROM orders WHERE status = ? AND id > ? ORDER BY id ASC LIMIT ?",
                    new OrderRowMapper(), status.name(), after, size + 1);
        }

        boolean hasMore = candidates.size() > size;
        int startIndex = 0;
        int endIndex = Math.min(size, candidates.size());
        List<Order> content = candidates.subList(startIndex, endIndex);
        String nextAfter = hasMore ? content.getLast().getId() : null;

        return new OrderCursorPage(content, nextAfter);
    }
}
