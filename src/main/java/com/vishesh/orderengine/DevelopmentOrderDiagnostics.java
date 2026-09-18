package com.vishesh.orderengine;

/*
 * A Spring component that exists only with the "development" profile active.
 * Profiles let the same application choose environment-specific beans without
 * changing business code.
 */

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("development")
public class DevelopmentOrderDiagnostics {
    public boolean isEnabled() {
        return true;
    }
}
