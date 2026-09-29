package com.vishesh.orderengine.order;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// With no postgres profile active, Spring must expose the lightweight repository.
@SpringBootTest 
public class InMemoryRepositoryWiringTest {
    @Autowired 
    private OrderRepository orderRepository;

    @Test 
    public void usesInMemoryRepositoryWhenPostgresProfileIsNotActive() {
        assertInstanceOf(InMemoryOrderRepository.class,orderRepository);
    }
}
