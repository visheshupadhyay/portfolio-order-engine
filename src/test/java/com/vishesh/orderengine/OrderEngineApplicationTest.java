package com.vishesh.orderengine;

import com.vishesh.orderengine.order.*;

/*
 * Spring Boot smoke test: @SpringBootTest starts App and injects a running
 * ApplicationContext instead of this test manually building one.
 */

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest 
public class OrderEngineApplicationTest {
    @Autowired 
    private ApplicationContext applicationContext;

    @Test 
    public void startsSpringBootApplicationContext() {
        assertNotNull(this.applicationContext);
    }
}
