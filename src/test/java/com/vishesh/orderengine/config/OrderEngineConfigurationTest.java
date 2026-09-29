package com.vishesh.orderengine.config;

import com.vishesh.orderengine.order.*;

/*
 * Boot integration revision: checks component discovery, default singleton
 * scope, qualified notifier wiring, and property-backed Path creation.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.vishesh.orderengine.importer.OrderImportService;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;

@SpringBootTest
public class OrderEngineConfigurationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void createsOrderImportServiceWithInjectedDependencies() {
        OrderImportService service = applicationContext.getBean(OrderImportService.class);
        assertNotNull(service);
    }

    @Test
    public void returnsSameServiceBeanByDefault() {

        OrderImportService service1 = applicationContext.getBean(OrderImportService.class);
        OrderImportService service2 = applicationContext.getBean(OrderImportService.class);
        assertSame(service1, service2);

    }

    @Test
    public void createsPaidNotificationServiceUsingQualifiedSmsNotifier() {

        OrderPaidNotificationService service = applicationContext.getBean(OrderPaidNotificationService.class);

        assertNotNull(service);

    }

    @Test
    public void providesConfiguredOrderInputPath() {

        Path path = applicationContext.getBean(Path.class);

        assertEquals(Path.of("src/main/resources/orders.txt"), path);

    }
}
