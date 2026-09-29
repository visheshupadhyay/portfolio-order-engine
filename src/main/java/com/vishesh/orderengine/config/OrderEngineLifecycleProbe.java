package com.vishesh.orderengine.config;

/*
 * A deliberately small Spring component used to learn lifecycle callbacks:
 * initialization happens after injection, and destruction happens when the
 * application context closes.
 */

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class OrderEngineLifecycleProbe implements InitializingBean, DisposableBean {
    private boolean initialized, destroyed;

    @Override
    public void destroy() {
        this.destroyed = true;
    }

    @Override
    public void afterPropertiesSet() {
        this.initialized = true;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

}
