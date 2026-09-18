package com.vishesh.orderengine;

/*
 * Lifecycle revision: initialization happens during Boot startup; destruction
 * happens only after the test closes its application context.
 */

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class OrderEngineLifecycleProbeTest {

    @Test 
    public void initializesAndDestroysSingletonBeanWithContext() {
        OrderEngineLifecycleProbe service;
        // Closing this try-with-resources context triggers Spring's destroy callback.
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class).run()) {
            service = context.getBean(OrderEngineLifecycleProbe.class);
            assertTrue(service.isInitialized());
            assertFalse(service.isDestroyed());
        }
        assertTrue(service.isDestroyed());
    }
}
