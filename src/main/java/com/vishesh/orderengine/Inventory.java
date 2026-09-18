package com.vishesh.orderengine;

/*
 * Plain-Java synchronized inventory example, not a Spring bean. The monitor
 * lock protects the check-and-decrement operation from overselling stock.
 */
public class Inventory {
    private int availableStock;

    public Inventory(int availableStock) {
        if (availableStock>=0) {
            this.availableStock = availableStock;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    public synchronized int getAvailableStock() {
        return this.availableStock;
    }

    public synchronized boolean reserveOne() {
        if (availableStock==0) {
            return false;
        }
        else {
            availableStock--;
            return true;
        }
    }


}
