import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class App {
    public static void main(String[] args) {
        System.out.println(message());
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        OrderFileImporter orderFileImporter = new OrderFileImporter();
        OrderImportService orderImportService = new OrderImportService(orderFileImporter, inMemoryOrderRepository);
        Path inputFile = Path.of("src/main/resources/orders.txt");
        try{
            orderImportService.importAndSave(inputFile);
            Optional<Order> order1 = inMemoryOrderRepository.findOrderById("order-101");
            if (order1.isEmpty()) {
                throw new IOException("Order not Found");
            }
            Optional<Order> order2 = inMemoryOrderRepository.findOrderById("order-102");
            if (order2.isEmpty()) {
                throw new IOException("Order not Found");
            }
            System.out.println(order1.get().getId()+ ":"+ order1.get().getStatus());
            System.out.println(order2.get().getId()+ ":"+ order2.get().getStatus());
        }
        catch( IOException e) {
            System.out.println("Unable to import and save orders.");
        }
        
    }

    static String message() {
        return "Portfolio order engine started";
    }
}
