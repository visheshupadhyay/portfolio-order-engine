package com.vishesh.orderengine.inventory;

/*
 * Plain-Java ReentrantLock inventory example. It shows explicit lock/unlock
 * control and why unlock belongs in finally even when a method returns early.
 */
import java.util.concurrent.locks.ReentrantLock;

public class LockingInventory {
    private int availableStock;
    private final ReentrantLock reentrantLock =  new ReentrantLock();
    public  LockingInventory(int availableStock) {
        if (availableStock>=0) {
            this.availableStock = availableStock;
        }
        else {
            throw new IllegalStateException();
        }
    }

    public int getAvailableStock() {
        reentrantLock.lock();
        try{
            return this.availableStock;
        }
        finally{
            reentrantLock.unlock();
        }
        
    }

    public boolean reserveOne() {
        reentrantLock.lock();
        try{
            if (availableStock==0) {
                return false;
            }
            else {
                availableStock--;
                return true;
            }
        }
        finally {
            reentrantLock.unlock();
        }
    }
}
