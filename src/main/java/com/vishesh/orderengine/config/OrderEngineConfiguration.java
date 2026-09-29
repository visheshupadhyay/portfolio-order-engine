package com.vishesh.orderengine.config;

/*
 * Keeps the few beans that need custom construction or runtime configuration:
 * notifiers, the property-backed input Path, and startup work. Spring Boot now
 * performs component scanning and property loading automatically.
 */
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.vishesh.orderengine.importer.OrderImportService;
import com.vishesh.orderengine.notification.AbstractNotifier;
import com.vishesh.orderengine.notification.EmailNotifier;
import com.vishesh.orderengine.notification.SmsNotifier;

@Configuration
public class OrderEngineConfiguration {
    @Bean
    @Primary
    public AbstractNotifier emailNotifier() {
        return new EmailNotifier("orders@example.com");
    }

    @Bean
    public AbstractNotifier smsNotifier() {
        return new SmsNotifier("OrderEngine");
    }

    // Spring reads this value from application.properties, so each environment
    // can choose its input file without changing Java code.
    @Bean
    public Path orderInputFile(@Value("${order.input-file}") String inputFile) {
        return Path.of(inputFile);
    }

    /*
     * Boot invokes this runner only after the context has created and injected
     * its beans. The runner starts the use case; OrderImportService owns its logic.
     */
    @Bean 
    public CommandLineRunner importConfiguredOrdersAtStartup(OrderImportService orderImportService, Path orderInputFile) {
        return args -> orderImportService.importAndSave(orderInputFile);
    }
}
