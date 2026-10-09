package com.vishesh.orderengine.importer;

/*
 * Prototype-scoped, per-import mutable state. Every request to Spring for this
 * bean gets a fresh counter, so separate import runs do not share state.
 */

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component 
@Scope("prototype")
public class ImportRunContext {
    private int importedOrderCount=0;

    public void incrementByOne() {
        this.importedOrderCount++;
    }

    public int getImportedOrderCount() {
        return this.importedOrderCount;
    }
}
