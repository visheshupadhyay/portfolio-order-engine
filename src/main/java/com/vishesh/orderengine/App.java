package com.vishesh.orderengine;

/*
 * Spring Boot entry point. It replaces the old manually-created Spring context:
 * Boot starts the context, scans this package, loads configuration, and manages
 * the beans before the application begins its work.
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enables @Scheduled methods; the outbox scheduler itself is still opt-in by property.
@EnableScheduling
@SpringBootApplication 
public class App {
    public static void main(String[] args) {
        // Boot creates and refreshes the ApplicationContext, then runs startup runners.
        SpringApplication.run(App.class,args);
    }

    static String message() {
        return "Portfolio order engine started";
    }
}
