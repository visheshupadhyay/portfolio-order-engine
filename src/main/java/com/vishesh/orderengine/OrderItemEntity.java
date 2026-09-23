package com.vishesh.orderengine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/*
 * Child persistence representation of order_items. Each item belongs to exactly
 * one OrderEntity, while an OrderEntity can contain many items.
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    // PostgreSQL generates this child-row ID during insert.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side of the relationship because this field maps the order_id FK.
    // LAZY prevents loading the parent merely because an item was loaded.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    // Required by Hibernate for row reconstruction.
    protected OrderItemEntity() {

    }

    OrderItemEntity(OrderEntity order,String productName, int quantity) {
        this.productName = productName;
        this.order = order;
        this.quantity = quantity;

    }

    public Long getId() {
        return this.id;
    }

    public String getProductName() {
        return this.productName;
    }

    public int getQuantity() {
        return this.quantity;
    }

    
}
