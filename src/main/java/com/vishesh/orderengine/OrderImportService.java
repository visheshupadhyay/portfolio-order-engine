package com.vishesh.orderengine;

/*
 * Application service that coordinates importing and saving orders. Constructor
 * injection lets Spring provide its importer and repository instead of App
 * creating those dependencies manually.
 */
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

@Service 
public class OrderImportService {
    private final OrderFileImporter orderFileImporter;
    private final OrderRepository orderRepository;

    OrderImportService(OrderFileImporter orderFileImporter, OrderRepository orderRepository) {
        this.orderFileImporter = orderFileImporter;
        this.orderRepository = orderRepository;
    }

    void importAndSave(Path inputFile) throws IOException {
        List<Order> orders = orderFileImporter.importOrders(inputFile);
        for (Order order: orders) {
            orderRepository.save(order);
        }
    }
}
