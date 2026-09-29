package com.vishesh.orderengine.order;

/*
 * Domain enum, not a Spring bean. It represents the valid lifecycle states an
 * individual Order may have.
 */
public enum OrderStatus {
    CREATED,
    PAID
}
