package com.vishesh.orderengine.support;

/*
 * Plain-Java idempotency helper. ConcurrentHashMap.putIfAbsent atomically lets
 * only one caller claim a request ID, preventing duplicate in-process work.
 */
import java.util.concurrent.ConcurrentHashMap;

public class ProcessedRequestRegistry {
    private final ConcurrentHashMap<String,String> requestStatusByID;

    public ProcessedRequestRegistry() {
        this.requestStatusByID= new ConcurrentHashMap<>();
    }
    public boolean startIfNotProcessed(String requestId) {
        String value = requestStatusByID.putIfAbsent(requestId, "PROCESSING");
        return value == null;
    }
}
