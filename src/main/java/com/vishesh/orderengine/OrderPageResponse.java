package com.vishesh.orderengine;

import java.util.List;

/* Numbered-page HTTP contract: content plus the metadata needed for page navigation. */
public record OrderPageResponse(List<OrderResponse> content,
        int page,
        int size,
        long totalElements) {

}
