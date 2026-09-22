package com.vishesh.orderengine;

/*
 * A domain object, not a Spring bean. A new Order represents one real order and
 * owns its own state transition from CREATED to PAID.
 */
public class Order {
    private final String id;
    private OrderStatus status;

    Order(String id) {
        this(id, OrderStatus.CREATED);
    }

    // Rebuilds an existing order from repository data; it does not itself query or
    // update the database.
    Order(String id, OrderStatus status) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order cannot be blank");
        }
        this.id = id;
        this.status = status;
    }

    String getId() {
        return this.id;
    }

    OrderStatus getStatus() {
        return this.status;
    }

    void markPaid() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be PAID");
        }
        this.status = OrderStatus.PAID;
    }
}
