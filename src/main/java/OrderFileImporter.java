import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OrderFileImporter {
    List<Order> importOrders(Path inputFile) throws IOException {
        String line;
        List<Order> orders = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                if (parts.length != 2) {
                    continue;
                }
                String orderId = parts[0];
                OrderStatus status;
                Order order;

                try {
                    order = new Order(orderId);
                    status = OrderStatus.valueOf(parts[1]);
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                    continue;
                }
                
                if (status == OrderStatus.PAID) {
                    order.markPaid();
                }
                orders.add(order);
            }
        }
        return orders;
    }
}