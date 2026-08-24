import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class InMemoryOrderRepositoryTest {
    @Test
    void saveOrderTest() {
        InMemoryOrderRepository orderRepository= new InMemoryOrderRepository();
        Order order = new Order("order-101");
        orderRepository.save(order);

        Optional<Order> found = orderRepository.findOrderById(order.getId());
        assertTrue(found.isPresent());
        assertEquals("order-101", found.orElseThrow().getId());
    }

    @Test
    void missingOrderReturnsEmpty() {
        InMemoryOrderRepository orderRepository= new InMemoryOrderRepository();
        Order order = new Order("order-101");
        orderRepository.save(order);
        Order wrongOrder = new Order("order-102");

        Optional<Order> found = orderRepository.findOrderById(wrongOrder.getId());
        // assertTrue();
        assertFalse(found.isPresent());
        // assertEquals("order-101", found.orElseThrow().getId());
    }

}
