package com.vishesh.orderengine.order;

/*
 * A domain object, not a Spring bean. A new Order represents one real order and
 * owns its own state transition from CREATED to PAID.
 */
public class Order {
    private final String id;
    private OrderStatus status;

    public Order(String id) {
        this(id, OrderStatus.CREATED);
    }

    // Rebuilds an existing order from repository data; it does not itself query or
    // update the database.
    public Order(String id, OrderStatus status) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order cannot be blank");
        }
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return this.id;
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    public void markPaid() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be PAID");
        }
        this.status = OrderStatus.PAID;
    }
}
