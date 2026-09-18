package com.vishesh.orderengine;

/*
 * ObjectProvider revision: one singleton service asks Spring for a fresh
 * prototype context on every startRun call.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class ImportRunServiceTest {
    @Test
    public void startsSeparatePrototypeContextForEachRun() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class).run()) {
            ImportRunService service = context.getBean(ImportRunService.class);
            ImportRunContext first = service.startRun();
            ImportRunContext second = service.startRun();
            assertNotSame(first, second);
            first.incrementByOne();
            assertEquals(1, first.getImportedOrderCount());
            assertEquals(0, second.getImportedOrderCount());
            
            
        }
    }
}
