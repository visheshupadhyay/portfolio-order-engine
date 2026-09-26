package com.vishesh.orderengine;

import java.util.List;

/*
 * Storage-neutral sequential batch. nextAfter is the last returned ID only
 * when another batch exists; null tells the API client that traversal is complete.
 */
public record OrderCursorPage(List<Order> content, String nextAfter) {
    public OrderCursorPage{
        content = List.copyOf(content);
    }
}
