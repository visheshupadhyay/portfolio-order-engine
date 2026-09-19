package com.vishesh.orderengine;

/* Outgoing API DTO: expose only the fields clients need, not the full domain object. */
public record OrderResponse(String id, String status) {
}

