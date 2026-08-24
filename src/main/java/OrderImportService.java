import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class OrderImportService {
    private final OrderFileImporter orderFileImporter;
    private final OrderRepository orderRepository;

    OrderImportService(OrderFileImporter orderFileImporter, OrderRepository orderRepository) {
        this.orderFileImporter = orderFileImporter;
        this.orderRepository = orderRepository;
    }

    void importAndSave(Path inputFile) throws IOException {
        List<Order> orders = orderFileImporter.importOrders(inputFile);
        for (Order order: orders) {
            orderRepository.save(order);
        }
    }
}
