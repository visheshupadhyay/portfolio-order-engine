package com.vishesh.orderengine;

/*
 * Plain-Java orchestration revision: OrderImportService coordinates an importer
 * and repository supplied through constructor injection.
 */
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vishesh.orderengine.InMemoryOrderRepository;
import com.vishesh.orderengine.OrderFileImporter;
import com.vishesh.orderengine.OrderImportService;
import com.vishesh.orderengine.OrderStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class OrderImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsAndSavesOrders() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,CREATED");
        Files.writeString(inputFile, "\norder-102,PAID", java.nio.file.StandardOpenOption.APPEND);

        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        OrderImportService orderImportService = new OrderImportService(orderFileImporter, inMemoryOrderRepository);
        orderImportService.importAndSave(inputFile);
        assertTrue(inMemoryOrderRepository.findOrderById("order-101").isPresent());
        assertTrue(inMemoryOrderRepository.findOrderById("order-102").isPresent());
        assertEquals(OrderStatus.PAID,inMemoryOrderRepository.findOrderById("order-102").orElseThrow().getStatus());
    }
}
