package com.vishesh.orderengine;

import java.util.List;
/*
 * Storage contract used by the service. Depending on this abstraction allows
 * Spring to supply the in-memory implementation today and a database
 * implementation later.
 */
import java.util.Optional;

interface OrderRepository {
    void save(Order order);

    Optional<Order> findOrderById(String orderId);

    /*
     * Listing is a storage concern; stable API ordering belongs in the
     * controller, not in every repository implementation.
     */
    List<Order> findAll();

    /*
     * Atomically attempts CREATED -> PAID. true identifies the one caller that
     * made the state change; false prevents repeated notification on retries.
     */
    boolean markPaidIfCreated(String orderId);

    /*
     * Atomically creates an order only if its ID is unused. The boolean lets the
     * controller return 201 for the winner and 409 for a duplicate request.
     */
    boolean createIfAbsent(Order order);

    /*
     * Returns one ID-sorted page and the total number of matching orders.
     * A null status means no status filter.
     */
    OrderPage findPage(OrderStatus status, int page, int size);

    // Sequential pagination contract: after is exclusive and nextAfter comes from the last returned ID.
    OrderCursorPage findAfter(OrderStatus status, String after, int size);
}
