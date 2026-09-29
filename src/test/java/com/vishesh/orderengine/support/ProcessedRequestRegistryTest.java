package com.vishesh.orderengine.support;

import com.vishesh.orderengine.order.*;

/*
 * ConcurrentHashMap revision: putIfAbsent lets exactly one thread claim the
 * same request ID, preventing duplicate in-process work.
 */
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ProcessedRequestRegistryTest {
    @Test 
    public void acceptsOnlyOneConcurrentRequestWithSameId() throws InterruptedException  {
        ProcessedRequestRegistry processedRequestRegistry = new ProcessedRequestRegistry();
        String request = "request-101";
        boolean result[]= new boolean[2];
        Thread thread1 = new Thread(()->{
            result[0] = processedRequestRegistry.startIfNotProcessed(request);
        });

        Thread thread2 = new Thread(()->{
            result[1] = processedRequestRegistry.startIfNotProcessed(request);
        });
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(result[0]!= result[1]);
    }
}
