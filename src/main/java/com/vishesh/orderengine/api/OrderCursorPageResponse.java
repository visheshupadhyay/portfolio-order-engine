package com.vishesh.orderengine.api;

import java.util.List;

/* Cursor HTTP contract: client sends nextAfter back as after to continue sequential loading. */
public record OrderCursorPageResponse(List<OrderResponse> content, String nextAfter) {

    public OrderCursorPageResponse{
        content = List.copyOf(content);
    }
}
