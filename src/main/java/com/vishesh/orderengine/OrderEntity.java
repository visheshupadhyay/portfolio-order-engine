package com.vishesh.orderengine;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/*
    Persistence representation of the existing orders table.
    It is separate from domain Order so Hibernate mapping requirements do not weaken domain business rules.
*/
@Entity
@Table(name = "orders")
public class OrderEntity {
    // Application-assigned ID mapped to the orders table primary-key column.
    @Id
    private String id;

    // Store enum names such as CREATED/PAID, not fragile ordinal numbers.
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // Inverse relationship: the child owns order_id. Children are saved/removed
    // with this aggregate, but their rows are loaded only when items are needed.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items = new ArrayList<>();

    // Hibernate includes this value in updates, incrementing it on success and
    // rejecting a stale concurrent copy instead of silently overwriting data.
    @Version
    @Column(nullable = false)
    private Long version;

    // Required by Hibernate when it reconstructs an entity from a database row.
    protected OrderEntity() {

    }

    protected OrderEntity(String id, OrderStatus status) {
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return this.id;
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    void updateStatus(OrderStatus orderStatus) {
        // Dirty checking detects this change while the entity remains managed.
        this.status = orderStatus;
    }

    void addItem(String productName, int quantity) {
        OrderItemEntity orderItem = new OrderItemEntity(this, productName, quantity);
        // Keep both Java sides consistent: child points to parent and parent retains child.
        items.add(orderItem);
    }

    public List<OrderItemEntity> getItems() {
        // Do not expose the managed collection for arbitrary caller mutation.
        return List.copyOf(items);
    }

    public Long getVersion() {
        return this.version;
    }

    
}
