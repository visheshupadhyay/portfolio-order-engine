import java.util.Optional;

interface OrderRepository {
    void save(Order order);
    Optional<Order> findOrderById(String orderId);
}
