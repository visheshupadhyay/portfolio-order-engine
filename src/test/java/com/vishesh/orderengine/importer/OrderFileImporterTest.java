package com.vishesh.orderengine.importer;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.*;

/*
 * Plain-Java file-importer revision: @TempDir isolates test files, valid orders
 * import successfully, and invalid rows are skipped safely.
 */
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.order.OrderStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OrderFileImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsCreatedOrder() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,CREATED");
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        List<Order> listOfOrders = orderFileImporter.importOrders(inputFile);
        assertEquals(1, listOfOrders.size());
        assertEquals(OrderStatus.CREATED, listOfOrders.get(0).getStatus());
    }

    @Test
    void importsPaidOrder() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,PAID");
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        List<Order> listOfOrders = orderFileImporter.importOrders(inputFile);
        assertEquals(1, listOfOrders.size());
        assertEquals(OrderStatus.PAID, listOfOrders.get(0).getStatus());
    }

    @Test
    void skipsMalformedLines() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,CREATED");
        Files.writeString(inputFile, "\nbroken-record", java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(inputFile, "\norder-202,PAID", java.nio.file.StandardOpenOption.APPEND);
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        List<Order> listOfOrders = orderFileImporter.importOrders(inputFile);
        assertEquals(2, listOfOrders.size());
        assertEquals(OrderStatus.CREATED, listOfOrders.get(0).getStatus());
        assertEquals(OrderStatus.PAID, listOfOrders.get(1).getStatus());
    }

    @Test
    void skipsInvalidStatus() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,CREATED");
        Files.writeString(inputFile, "\norder-102,UNKNOWN", java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(inputFile, "\norder-202,PAID", java.nio.file.StandardOpenOption.APPEND);
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        List<Order> listOfOrders = orderFileImporter.importOrders(inputFile);
        assertEquals(2, listOfOrders.size());
        assertEquals(OrderStatus.CREATED, listOfOrders.get(0).getStatus());
        assertEquals(OrderStatus.PAID, listOfOrders.get(1).getStatus());
    }

    @Test
    void skipsBlankOrderId() throws IOException {
        Path inputFile = tempDir.resolve("orders.txt");
        Files.writeString(inputFile, "order-101,CREATED");
        Files.writeString(inputFile, "\n,PAID", java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(inputFile, "\norder-202,PAID", java.nio.file.StandardOpenOption.APPEND);
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        List<Order> listOfOrders = orderFileImporter.importOrders(inputFile);
        assertEquals(2, listOfOrders.size());
        assertEquals("order-101", listOfOrders.get(0).getId());
        assertEquals("order-202", listOfOrders.get(1).getId());
    }

    @Test
    void missingFileThrowsIOException() {
        Path inputFile = tempDir.resolve("missing-orders.txt");

        OrderFileImporter orderFileImporter = new OrderFileImporter();

        assertThrows(IOException.class, () -> orderFileImporter.importOrders(inputFile));

    }

}
