package com.vishesh.orderengine.order;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// A profile test proves Spring selected the JDBC implementation, not merely that
// JdbcOrderRepository can be constructed in isolation.
@ActiveProfiles("postgres")
@SpringBootTest 
public class PostgresRepositoryWiringTest {
    @Autowired 
    private OrderRepository orderRepository;

    @Test 
    public void usesJdbcRepositoryWhenPostgresProfileIsActive() {
        assertInstanceOf(JdbcOrderRepository.class,orderRepository);
    }
}
