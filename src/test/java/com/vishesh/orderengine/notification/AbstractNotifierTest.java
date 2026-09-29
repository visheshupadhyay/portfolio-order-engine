package com.vishesh.orderengine.notification;

import com.vishesh.orderengine.order.*;

import com.vishesh.orderengine.*;

/*
 * Plain-Java unit revision: the shared notifier validation rejects null and
 * blank messages before any concrete delivery channel is reached.
 */
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class AbstractNotifierTest {
    @Test
    void rejectsNullOrBlankMessage() {
        EmailNotifier emailNotifier1 = new EmailNotifier("TEST1");
        assertThrows(IllegalArgumentException.class, () -> emailNotifier1.send(null));
        EmailNotifier emailNotifier2 = new EmailNotifier("TEST2");
        assertThrows(IllegalArgumentException.class, () -> emailNotifier2.send(" "));
        assertThrows(IllegalArgumentException.class, () -> emailNotifier2.send("valid message", null));
        assertThrows(IllegalArgumentException.class, () -> emailNotifier2.send("valid message", " "));
    }
}
