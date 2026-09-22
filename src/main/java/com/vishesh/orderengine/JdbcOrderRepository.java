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
}
