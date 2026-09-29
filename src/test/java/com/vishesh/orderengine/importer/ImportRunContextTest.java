package com.vishesh.orderengine.importer;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.*;

/*
 * Prototype-scope revision: each Spring getBean request returns an independent
 * per-import context rather than one shared mutable object.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class ImportRunContextTest {

    @Test
    public void createsDifferentContextForEachPrototypeRequest() {
        try (ConfigurableApplicationContext  context = new SpringApplicationBuilder(App.class).run()) {
            ImportRunContext service1 = context.getBean(ImportRunContext.class);
            ImportRunContext service2 = context.getBean(ImportRunContext.class);
            assertNotSame(service1, service2);
            service1.incrementByOne();
            assertEquals(1, service1.getImportedOrderCount());

            assertEquals(0, service2.getImportedOrderCount());
        }
    }
}
