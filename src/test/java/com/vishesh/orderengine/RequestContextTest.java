package com.vishesh.orderengine;

/*
 * ThreadLocal revision: each thread keeps its own request ID, and clear()
 * prevents stale context being reused by a pooled worker thread.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.RequestContext;

public class RequestContextTest {
    @Test
    public void keepsRequestIdsSeparateAcrossThreads() throws InterruptedException {
        RequestContext requestContext = new RequestContext();
        String values[] = new String[2];
        Thread thread1 = new Thread(() -> {
            try {
                requestContext.setCurrentRequestId("request-A");
                values[0] = requestContext.getCurrentRequestId();
            } finally {
                requestContext.clear();
            }

        });
        Thread thread2 = new Thread(() -> {
            try {
                requestContext.setCurrentRequestId("request-B");
                values[1] = requestContext.getCurrentRequestId();
            } finally {
                requestContext.clear();
            }
        });
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertEquals("request-A", values[0]);
        assertEquals("request-B", values[1]);

    }

    @Test
    public void clearsRequestIdForCurrentThread() {
        RequestContext requestContext = new RequestContext();
        requestContext.setCurrentRequestId("request-A");
        requestContext.clear();
        assertNull(requestContext.getCurrentRequestId());
    }
}
