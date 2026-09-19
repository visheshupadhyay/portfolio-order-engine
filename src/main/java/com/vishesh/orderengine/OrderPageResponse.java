package com.vishesh.orderengine;

import java.util.List;

/* Paginated list contract: content plus the metadata a client needs to request the next page. */
public record OrderPageResponse(List<OrderResponse> content,
        int page,
        int size,
        int totalElements) {

}
