package com.vishesh.orderengine;

/*
 * Plain-Java lock-free inventory example. AtomicInteger and compare-and-set make
 * the read-and-decrement operation safe without taking a traditional lock.
 */
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicInventory {
    private final AtomicInteger availableStock;

    public AtomicInventory(int availableStock) {
        if (availableStock>=0) {
            this.availableStock = new AtomicInteger(availableStock);
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    public int getAvailableStock() {
        return this.availableStock.get();
    }

    public boolean reserveOne() {
        while (true) {
            int current = availableStock.get();

            if (current == 0) {
                return false;
            }

            if (availableStock.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

}
