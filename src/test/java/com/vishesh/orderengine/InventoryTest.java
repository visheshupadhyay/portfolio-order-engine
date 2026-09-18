package com.vishesh.orderengine;

/*
 * Concurrency revision: synchronized reservation prevents overselling, while
 * the local UnsafeInventory uses latches to reproduce a race deterministically.
 */
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.Inventory;

public class InventoryTest {
    @Test
    public void reservesOnlyAvailableStock() {
        Inventory inventory = new Inventory(1);
        assertTrue(inventory.reserveOne());
        assertFalse(inventory.reserveOne());
        assertEquals(0, inventory.getAvailableStock());

    }

    @Test
    public void concurrentReservationsDoNotOversell() {
        Inventory inventory = new Inventory(1);
        boolean result[]= new boolean[2];
        Thread thread1 = new Thread(() -> {
            result[0] = inventory.reserveOne();
        });
        Thread thread2 = new Thread(() -> {
            result[1] = inventory.reserveOne();
        });
        thread1.start();
        thread2.start();


        try {
            thread1.join();
            thread2.join();
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(0, inventory.getAvailableStock());
        assertTrue(result[0]!= result[1]);
    }

    @Test
    public void unsafeInventoryCanOversell() throws InterruptedException {
        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch continueNow = new CountDownLatch(1);
        UnsafeInventory unsafeInventory = new UnsafeInventory(bothChecked, continueNow);
        boolean result[] = new boolean[2];
        Thread thread1 = new Thread(() -> {
            try{
                result[0]=unsafeInventory.reservation();
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        });
        Thread thread2 = new Thread(() -> {
            try{
                result[1]=unsafeInventory.reservation();
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread1.start();
        thread2.start();
        // Both threads must first save the same "stock is available" answer;
        // releasing this gate then demonstrates the unsafe check-then-decrement race.
        bothChecked.await();
        continueNow.countDown();
        thread1.join();
        thread2.join();

        assertTrue(result[0]);
        
        assertTrue(result[1]);
    }

    @Test
    public void rejectsNegativeInitialStock() {
        assertThrows(IllegalArgumentException.class, ()-> new Inventory(-200));
        assertDoesNotThrow(()->new Inventory(0));
    }
}

class UnsafeInventory {
    private int availableStock = 1;
    private final CountDownLatch bothChecked;
    private final CountDownLatch continueNow;

    public UnsafeInventory (CountDownLatch bothChecked, CountDownLatch continueNow) {
        this.bothChecked = bothChecked;
        this.continueNow = continueNow;
    }

    public boolean reservation() throws InterruptedException {
        boolean stockWasAvailable;

        if (availableStock>0) {
            stockWasAvailable = true;
        }
        else {
            stockWasAvailable = false;
        }

        bothChecked.countDown();
        continueNow.await();
        if (stockWasAvailable) {
            availableStock--;
            return true;
        }
        return false;
    }
}
