package com.vishesh.orderengine;

import com.vishesh.orderengine.order.*;

/*
 * Legacy helper test: App.message() remains covered separately from the Spring
 * Boot startup behavior tested by OrderEngineApplicationTest.
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void applicationMessageIsCorrect() {
        assertEquals("Portfolio order engine started", App.message());
    }
}
