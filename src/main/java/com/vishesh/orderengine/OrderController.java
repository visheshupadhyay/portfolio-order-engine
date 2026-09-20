package com.vishesh.orderengine;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

// Applies the named OpenAPI Basic-auth scheme to every order operation in the docs.
@SecurityRequirement(name = "basicAuth")
@Tag(name = "Orders", description = "Create, find, list, and pay orders.")
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepository orderRepository;
    private final OrderPaymentService orderPaymentService;

    public OrderController(OrderRepository orderRepository, OrderPaymentService orderPaymentService) {
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "Find an order by ID", description = "Returns an order's ID and current status.")
    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable String id) {
        Order order = orderRepository.findOrderById(id)
                .orElseThrow(() -> new ResponseStatusException(
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
        Optional<Order> existingOrder = orderRepository.findOrderById(request.id());
        if (existingOrder.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order already exists: " + request.id());
        }

        Order order = new Order(request.id());
        orderRepository.save(order);

        URI location = URI.create("/orders/" + order.getId());

        OrderResponse orderResponse = new OrderResponse(order.getId(), order.getStatus().name());
        return ResponseEntity.created(location).body(orderResponse);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order paid or already paid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "Pay an order", description = "Marks a CREATED order as PAID. A repeated payment returns the existing PAID order without a second notification.")
    @PostMapping("/{id}/pay")
    public OrderResponse payOrder(@PathVariable String id) {
        Order order = orderRepository.findOrderById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + id));

        orderPaymentService.pay(order);
        return new OrderResponse(id, order.getStatus().name());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid list query parameters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @Operation(summary = "List orders", description = "Returns a paginated, ID-sorted list of orders. Results can optionally be filtered by status.")
    @GetMapping
    public OrderPageResponse getOrders(@RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be greater than or equal to 0");
        }

        if (size <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be greater than 0");
        }
        List<Order> orders = orderRepository.findAll();
        List<OrderResponse> orderResponses = new ArrayList<>();
        for (Order order : orders) {
            if (status == null || order.getStatus() == status) {
                orderResponses.add(new OrderResponse(order.getId(), order.getStatus().name()));
            }
        }
        // HashMap storage has no stable iteration order, so sort before pagination for
        // repeatable API pages.
        orderResponses.sort(Comparator.comparing(OrderResponse::id));

        int totalElements = orderResponses.size();

        int startIndex = Math.min(page * size, totalElements);

        int endIndex = Math.min(startIndex + size, totalElements);

        List<OrderResponse> content = orderResponses.subList(startIndex, endIndex);

        return new OrderPageResponse(content, page, size, totalElements);
    }
}
