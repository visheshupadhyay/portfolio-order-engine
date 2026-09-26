package com.vishesh.orderengine;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/*
 * Profile-selected adapter between the domain repository contract and Spring Data JPA.
 * Web/controllers/services continue to work with Order rather than leaking OrderEntity.
 */
@Repository
@Profile("jpa")
public class JpaOrderRepository implements OrderRepository {

    private final OrderEntityJpaRepository orderEntityJpaRepository;

    public JpaOrderRepository(OrderEntityJpaRepository orderEntityJpaRepository) {
        this.orderEntityJpaRepository = orderEntityJpaRepository;
    }

    @Override
    @Transactional
    public void save(Order order) {
        // Native UPSERT preserves existing importer behavior for assigned order IDs.
        orderEntityJpaRepository.upsertOrder(order.getId(), order.getStatus().name());
    }

    @Override
    public Optional<Order> findOrderById(String orderId) {
        // Optional.map preserves the empty lookup while converting a present entity.
        return orderEntityJpaRepository.findById(orderId).map(this::toDomain);
    }

    @Override
    public List<Order> findAll() {
        // Map before returning so JPA persistence types do not escape this adapter.
        return orderEntityJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markPaidIfCreated(String orderId) {
        // The affected-row count identifies the one request that made the transition.
        return orderEntityJpaRepository.updateStatusIfMatches(orderId, OrderStatus.CREATED, OrderStatus.PAID) == 1;
    }

    @Override
    @Transactional
    public boolean createIfAbsent(Order order) {
        // One INSERT...ON CONFLICT statement prevents check-then-insert races.
        return orderEntityJpaRepository.insertIfAbsent(order.getId(), order.getStatus().name()) == 1;
    }

    private Order toDomain(OrderEntity orderEntity) {
        // Only fields owned by the current domain Order are translated here.
        return new Order(orderEntity.getId(), orderEntity.getStatus());
    }

    private OrderEntity toEntity(Order order) {
        return new OrderEntity(order.getId(), order.getStatus());
    }

    @Override
    public OrderPage findPage(OrderStatus status, int page, int size) {
        // Page asks Spring Data for both content and total count, matching the offset API contract.
        Pageable request = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<OrderEntity> entityPage;

        if (status == null) {
            entityPage = orderEntityJpaRepository.findAll(request);
        } else {
            entityPage = orderEntityJpaRepository.findAllByStatus(status, request);
        }
        List<Order> content = entityPage.getContent().stream().map(this::toDomain).toList();
        return new OrderPage(content, entityPage.getNumber(), entityPage.getSize(), entityPage.getTotalElements());
    }

    @Override
    public OrderCursorPage findAfter(OrderStatus status, String after, int size) {
        // Slice deliberately avoids a total-count query; hasNext becomes the nextAfter decision.
        Pageable request = PageRequest.of(0, size, Sort.by("id"));
        Slice<OrderEntity> entitySlice;

        if (status == null && after == null) {
            entitySlice = orderEntityJpaRepository.findCursorSlice(request);
        } else if (status == null) {
            entitySlice = orderEntityJpaRepository.findByIdGreaterThan(after, request);
        } else if (after == null) {
            entitySlice = orderEntityJpaRepository.findByStatus(status, request);
        } else {
            entitySlice = orderEntityJpaRepository.findByStatusAndIdGreaterThan(status, after, request);
        }

        List<Order> content = entitySlice.getContent().stream().map(this::toDomain).toList();
        String nextAfter = entitySlice.hasNext() ? content.getLast().getId() : null;
        return new OrderCursorPage(content, nextAfter);
    }

}
