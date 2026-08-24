import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void applicationMessageIsCorrect() {
        assertEquals("Portfolio order engine started", App.message());
    }
}