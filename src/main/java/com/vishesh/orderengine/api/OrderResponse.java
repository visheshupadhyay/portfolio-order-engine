package com.vishesh.orderengine.api;

/* Outgoing API DTO: expose only the fields clients need, not the full domain object. */
public record OrderResponse(String id, String status) {
}
