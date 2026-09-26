package com.vishesh.orderengine;

import java.util.List;

/*
 * Storage-neutral page result returned by OrderRepository.
 * It prevents controllers/services from depending on Spring Data's Page type.
 */
public record OrderPage(List<Order> content,
        int page,
        int size,
        long totalElements) {

    public OrderPage {
        // Defensive copy prevents callers from changing page content after construction.
        content = List.copyOf(content);
    }
}
