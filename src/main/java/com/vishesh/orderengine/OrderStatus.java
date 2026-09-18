package com.vishesh.orderengine;

/*
 * Domain enum, not a Spring bean. It represents the valid lifecycle states an
 * individual Order may have.
 */
enum OrderStatus {
    CREATED,
    PAID
}
