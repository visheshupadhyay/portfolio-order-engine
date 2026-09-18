package com.vishesh.orderengine;

/*
 * Plain-Java ThreadLocal request metadata. Each thread sees its own request ID,
 * and callers must clear it when work ends so pooled threads do not reuse stale
 * context.
 */
public class RequestContext {
    private final ThreadLocal<String> currentRequestID;

    public RequestContext() {
        currentRequestID = new ThreadLocal<>();
    }

    public void setCurrentRequestId(String requestId){
        currentRequestID.set(requestId);
    }

    public String getCurrentRequestId() {
        return currentRequestID.get();
    }

    public void clear() {
        currentRequestID.remove();
    }
}
