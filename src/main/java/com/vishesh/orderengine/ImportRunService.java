package com.vishesh.orderengine;

/*
 * Singleton Spring service that uses ObjectProvider to obtain a fresh
 * prototype ImportRunContext for every import run, rather than keeping one
 * mutable context forever.
 */

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service 
public class ImportRunService {
    private final ObjectProvider<ImportRunContext> orderProvider;
    public ImportRunService(ObjectProvider<ImportRunContext> orderProvider) {
        this.orderProvider = orderProvider;
    }

    public ImportRunContext startRun() {
        return orderProvider.getObject();
    }
}
