package com.vishesh.orderengine.api;

import com.vishesh.orderengine.order.*;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/*
 * REST adapter added in this revision. It translates HTTP/JSON into domain
 * calls; payment business rules remain in OrderPaymentService. The OpenAPI
 * annotations document the real HTTP contract for clients, but do not change
 * controller behavior or replace SecurityConfiguration's enforcement.
 */

// Applies the named OpenAPI Bearer JWT scheme to every order operation in the docs.
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Create, find, list, and pay orders.")
@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int ORDER_CHAR_LIMIT = 100;
    private final OrderRepository orderRepository;
    private final OrderPaymentService orderPaymentService;
    // Keeps cache-aside policy out of this HTTP adapter.
    private final OrderLookupService orderLookupService;

    public OrderController(OrderRepository orderRepository, OrderPaymentService orderPaymentService,
            OrderLookupService orderLookupService) {
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
        this.orderLookupService = orderLookupService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "Find an order by ID", description = "Returns an order's ID and current status.")
    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable String id) {
        validateOrderId(id);
        // The lookup service checks Redis first and falls back to persistent storage on a miss.
        Order order = orderLookupService.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found: " + id));

        return new OrderResponse(order.getId(), order.getStatus().name());
    }

    @Operation(summary = "Create an order", description = "Creates a new order with CREATED status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid order request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order ID already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = new Order(request.id());
        // Avoid an unsafe "find first, then save" race: the repository/database makes
        // the create-or-already-exists decision atomically.
        if (!orderRepository.createIfAbsent(order)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order already exists: " + request.id());
        }

        URI location = URI.create("/orders/" + order.getId());

        OrderResponse orderResponse = new OrderResponse(order.getId(), order.getStatus().name());
        return ResponseEntity.created(location).body(orderResponse);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order paid or already paid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "Pay an order", description = "Marks a CREATED order as PAID. A repeated payment returns the existing PAID order without a second notification event.")
    @PostMapping("/{id}/pay")
    public OrderResponse payOrder(@PathVariable String id) {
        validateOrderId(id);
        Order order = orderRepository.findOrderById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        // Payment may be persisted by a direct SQL update, so use the freshly loaded
        // result returned by the service rather than the earlier Java object.
        Order paidOrder = orderPaymentService.pay(order);
        // pay(...) completes its transactional work before returning; discard any old CREATED cache copy.
        orderLookupService.evict(paidOrder.getId());
        return new OrderResponse(paidOrder.getId(), paidOrder.getStatus().name());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid list query parameters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "List orders", description = "Returns a paginated, ID-sorted list of orders. Results can optionally be filtered by status. Page size must be from 1 to 100.")
    @GetMapping
    public OrderPageResponse getOrders(@RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }

        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be greater than 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be less than or equal to 100");
        }
        OrderPage orderPage = orderRepository.findPage(status, page, size);
        List<OrderResponse> content = orderPage.content().stream()
                .map(order -> new OrderResponse(order.getId(), order.getStatus().name())).toList();

        return new OrderPageResponse(content, orderPage.page(), orderPage.size(), orderPage.totalElements());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cursor batch returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderCursorPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid cursor query parameters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "List orders using cursor pagination", description = "Returns an ID-sorted cursor batch of orders. Results can optionally be filtered by status. Send the previous response's nextAfter value as after to load the next batch. Page size must be from 1 to 100. nextAfter is null when no more orders remain.")
    @GetMapping("/cursor")
    public OrderCursorPageResponse getOrderByCursor(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "10") int size) {

        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be greater than 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be less than or equal to 100");
        }
        if (after != null) {
            validateOrderId(after);
        }

        OrderCursorPage page = orderRepository.findAfter(status, after, size);
        List<OrderResponse> content = page.content().stream()
                .map(order -> new OrderResponse(order.getId(), order.getStatus().name())).toList();
        return new OrderCursorPageResponse(content, page.nextAfter());
    }

    private void validateOrderId(String orderId) {
        if (orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order id cannot be blank");
        } else if (orderId.length() > ORDER_CHAR_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order id must be less than 101 char limit");
        }
    }

}
