package com.vishesh.orderengine;

/*
 * AtomicInteger/CAS revision: with one item and two threads, exactly one
 * reservation succeeds without overselling.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.AtomicInventory;

public class AtomicInventoryTest {
    @Test
    public void concurrentReservationsDoNotOversell() throws InterruptedException {
        AtomicInventory atomicInventory = new AtomicInventory(1);
        boolean result[] = new boolean[2];
        Thread thread1 = new Thread(()->{
            result[0] = atomicInventory.reserveOne();
        });
        thread1.start();
        Thread thread2 =  new Thread(()->{
            result[1] = atomicInventory.reserveOne();
        });
        thread2.start();
        thread1.join();
        thread2.join();
        assertEquals(0,atomicInventory.getAvailableStock());
        assertTrue(result[0] != result[1]);
    }
}
