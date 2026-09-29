package com.vishesh.orderengine.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * Spring Data creates this implementation at startup. It works with JPA entities;
 * JpaOrderRepository translates its results to the application's domain Order type.
 */
public interface OrderEntityJpaRepository extends JpaRepository<OrderEntity, String> {
  // Spring Data derives the JPQL filter from the method name and entity field.
  List<OrderEntity> findAllByStatus(OrderStatus orderStatus);

  Page<OrderEntity> findAllByStatus(OrderStatus orderStatus, Pageable pageable);

  // Keep the default relationship lazy, but preload items for this one detail use
  // case.
  @EntityGraph(attributePaths = "items")
  @Query("select orderEntity from OrderEntity orderEntity where orderEntity.id = :id")
  Optional<OrderEntity> findWithItemsById(@Param("id") String orderId);

  List<OrderEntity> findAllByIdIn(List<String> ids);

  // Fetch join solves N+1 when every listed order needs its items. distinct
  // removes
  // duplicate parent entities caused by the one-to-many SQL join rows.
  @Query("""
      select distinct orderEntity
      from OrderEntity orderEntity
      left join fetch orderEntity.items
      where orderEntity.id in :ids
      """)
  List<OrderEntity> findAllWithItemsByIdIn(@Param("ids") List<String> ids);

  // Pageable carries requested page/size/sort; Page contains that slice plus
  // totals.
  Page<OrderEntity> findAllByIdIn(List<String> ids, Pageable pageable);

  // JPQL has no PostgreSQL ON CONFLICT equivalent, so this native statement makes
  // duplicate-safe creation one atomic database decision. Its row count is 1 or
  // 0.
  // Import-style save keeps previous UPSERT behavior and increments the database
  // version when an existing row changes.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = """
      INSERT INTO orders (id, status, version)
      VALUES (:id, :status, 0)
      ON CONFLICT (id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("id") String id, @Param("status") String status);

  // A conditional bulk update makes CREATED -> PAID atomic. Direct modifying
  // queries
  // bypass normal dirty checking, so flush/clear avoids stale managed entities.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = """
      INSERT INTO orders (id, status, version)
      VALUES (:id, :status, 0)
      ON CONFLICT (id) DO UPDATE
      SET status = EXCLUDED.status,
      version = orders.version + 1
      """, nativeQuery = true)
  int upsertOrder(@Param("id") String id, @Param("status") String status);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update OrderEntity orderEntity
      set orderEntity.status = :nextStatus,
          orderEntity.version = orderEntity.version + 1
      where orderEntity.id = :id
        and orderEntity.status = :expectedStatus
      """)
  int updateStatusIfMatches(
      @Param("id") String id,
      @Param("expectedStatus") OrderStatus expectedStatus,
      @Param("nextStatus") OrderStatus nextStatus);

  // Slice fetches one extra row internally to expose hasNext without a COUNT query.
  @Query("select orderEntity from OrderEntity orderEntity")
  Slice<OrderEntity> findCursorSlice(Pageable pageable);

  // Derived cursor queries retain the same ID order supplied in Pageable.
  Slice<OrderEntity> findByIdGreaterThan(String after, Pageable pageable);

  Slice<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

  Slice<OrderEntity> findByStatusAndIdGreaterThan(
      OrderStatus status, String after, Pageable pageable);
}
