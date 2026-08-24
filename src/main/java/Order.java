// package portfolio-order-engine.src.main.java;
// import src.main.java.OrderStatus;
public class Order {
    private final String id;
    private OrderStatus status;

    Order(String id) {
        if (id==null || id.isBlank()) {
            throw new IllegalArgumentException("Order cannot be blank");
        }
        this.id = id;
        this.status = OrderStatus.CREATED;
    }

    String getId() {
        return this.id;
    }

    OrderStatus getStatus() {
        return this.status;
    }

    void markPaid() {
        if (this.status!= OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be PAID");
        }
        this.status= OrderStatus.PAID;
    }
}
