package com.vishesh.orderengine.config;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.App;

/*
 * Spring profile integration: a @Profile bean exists only when "development"
 * is selected before Boot starts creating beans.
 */

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class DevelopmentOrderDiagnosticsTest {
    @Test
    public void createsDiagnosticsWhenDevelopmentProfileIsActive() {
        // A profile must be selected before run(), because Boot creates beans
        // while the application context is starting.
        try (ConfigurableApplicationContext  context = new SpringApplicationBuilder(App.class).profiles("development").run()) {
            DevelopmentOrderDiagnostics service = context.getBean(DevelopmentOrderDiagnostics.class);
            assertTrue(service.isEnabled());
        }
    }

    @Test 
    public void doesNotCreateDiagnosticsWhenDevelopmentProfileIsInactive() {
        try (ConfigurableApplicationContext  context = new SpringApplicationBuilder(App.class).run()) {
            assertFalse(context.containsBean("developmentOrderDiagnostics"));
        }
    }
}
