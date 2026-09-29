package com.vishesh.orderengine.order;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

// Rehydrates one database row as a domain object; it keeps ResultSet details out of
// the repository's query methods.
public class OrderRowMapper implements RowMapper<Order> {

    @Override 
    public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
        String orderId= rs.getString("id");
        OrderStatus status = OrderStatus.valueOf(rs.getString("status"));
        return new Order(orderId,status);
    }
}
