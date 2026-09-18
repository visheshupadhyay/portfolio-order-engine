package com.vishesh.orderengine;

/*
 * ReentrantLock revision: the explicit lock protects the check-and-decrement
 * operation so only one thread reserves the final item.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.LockingInventory;

public class LockingInventoryTest {
    @Test
    public void preventsOversellingWithReentrantLock()throws InterruptedException {
        LockingInventory lockingInventory = new LockingInventory(1);
        boolean result[] = new boolean[2];
        Thread thread1 = new Thread(()->{
            result[0] = lockingInventory.reserveOne();
        });
        thread1.start();
        Thread thread2 =  new Thread(()->{
            result[1] = lockingInventory.reserveOne();
        });
        thread2.start();
        thread1.join();
        thread2.join();

        assertEquals(0,lockingInventory.getAvailableStock());
        assertTrue(result[0] != result[1]);
    }
}
