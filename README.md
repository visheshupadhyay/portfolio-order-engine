# Portfolio Order Engine

A Java 21 / Spring Boot order-management learning project that has grown from file import and in-memory storage into a secured REST API with PostgreSQL, JDBC, and JPA/Hibernate persistence paths.

The project is deliberately built in small, tested steps. It demonstrates backend concepts that matter in production: validation, error contracts, authorization, transactional state changes, database constraints, query performance, and optimistic locking.

## Highlights

- REST endpoints to create, find, list, filter, paginate, and pay orders.
- `CREATED` and `PAID` are enum-backed domain states; duplicate payment requests are idempotent and do not create a second notification event.
- Consistent JSON errors for validation failures, missing orders, duplicates, invalid query values, invalid pagination, and temporary database outages (`503` without SQL details).
- Stateless Bearer JWT authentication: `/auth/login` exchanges credentials for a short-lived token, while order reads/writes use reader/writer roles carried by that token. Browser CORS is deliberately limited and CSRF is disabled because the token is sent explicitly in an `Authorization` header rather than automatically as a cookie.
- OpenAPI documentation and Swagger UI for the order API.
- Flyway versioned migrations create the PostgreSQL `orders`, `order_items`, and `outbox_events` schema automatically.
- Atomic duplicate-safe creation with `INSERT ... ON CONFLICT DO NOTHING` and atomic `CREATED -> PAID` payment transitions.
- JDBC and JPA/Hibernate implementations behind one `OrderRepository` domain contract.
- JPA mappings for one order to many order items, cascades, lazy loading, targeted entity-graph fetches, JPQL fetch joins, database-side pagination, and optimistic locking with `@Version`.
- Offset pagination for numbered pages and totals, plus cursor pagination for sequential load-more reads without a total-count query.
- A transactional outbox persists notification work with the payment transaction, then a retrying worker delivers it after commit.
- Redis cache-aside reads keep PostgreSQL as the source of truth; a stale cache entry is evicted after a successful payment.
- Kafka decouples paid-order publication from notification delivery. Consumers deduplicate by event ID and use retry/dead-letter-topic handling for failures.
- Docker Compose runs the application with PostgreSQL, Redis, and Kafka locally; GitHub Actions runs the full test suite and validates the Docker image build.
- Unit, MockMvc, JDBC, JPA, and PostgreSQL-backed integration tests.

## Architecture

```text
HTTP client / startup import
            |
            v
Controller / OrderImportService
            |
            v
OrderPaymentService and domain Order rules
            |
            v
OrderRepository + OutboxEventRepository
   |             |                 |
   v             v                 v
in-memory     JDBC profile       JPA profile
(default)     JdbcTemplate       JpaOrderRepository
                                  |
                                  v
                         Spring Data JPA / Hibernate
                                  |
                                  v
                             PostgreSQL

Committed PENDING outbox event
            |
            v
PROCESSING claim (lease + token)
            |
            v
OutboxEventDeliveryWorker --> Kafka order-paid topic
            |                         |
            v                         v
      SENT / retry later / FAILED   notification consumer --> SMS provider
```

The controller and service depend on `OrderRepository`, not on a database technology. Spring chooses an implementation through profiles:

| Active profile | Selected implementation | Purpose |
| --- | --- | --- |
| no persistence profile | `InMemoryOrderRepository` | Fast local/default learning flow |
| `postgres` | `JdbcOrderRepository` | Explicit SQL through `JdbcTemplate` |
| `jpa` | `JpaOrderRepository` | Domain-to-entity adapter using Spring Data JPA |

Activate only one database profile at a time.

## API overview

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/orders` | Create a `CREATED` order; duplicate IDs return `409` |
| `GET` | `/orders/{id}` | Find one order |
| `GET` | `/orders?status=PAID&page=0&size=10` | List, optionally filter, and paginate orders |
| `GET` | `/orders/cursor?status=CREATED&after=order-101&size=20` | Load the next ID-sorted cursor batch; response contains `content` and `nextAfter` |
| `POST` | `/orders/{id}/pay` | Atomically pay a created order; safe to retry |

Swagger/OpenAPI is available when the application runs at `/swagger-ui/index.html` and `/v3/api-docs`.

## Pagination and indexes

- `GET /orders` uses offset/page-number pagination and returns `page`, `size`, and `totalElements`. It is useful when a client needs numbered pages or a total result count.
- `GET /orders/cursor` uses cursor pagination and returns `content` plus `nextAfter`. Send a previous response's `nextAfter` value back as `after` to load the next batch.
- Cursor pagination avoids deep-offset scanning and avoids position-shift duplicates when new rows are inserted between sequential requests. A newly inserted row that sorts before the cursor is intentionally not added to the already-started traversal.
- The primary key on `orders(id)` supports unfiltered cursor queries. Status-filtered cursor queries require this composite PostgreSQL index:

```sql
CREATE INDEX IF NOT EXISTS idx_orders_status_id
ON orders (status, id);
```

The index supports `WHERE status = ? AND id > ? ORDER BY id LIMIT ?` by narrowing to one status and then reading IDs in cursor order.

## JPA/Hibernate notes

- `OrderEntity` maps `orders`; `OrderItemEntity` maps `order_items`.
- `OrderItemEntity.order` owns the `order_id` foreign key. `OrderEntity.items` is the inverse collection.
- The item collection is lazy by default, so item rows are not loaded for every order lookup.
- Detail reads can use an `@EntityGraph`; list reads that need every item use a JPQL `left join fetch` to avoid the N+1 query pattern.
- `@Version` prevents a stale entity copy from overwriting a newer committed update.
- The JPA adapter intentionally uses explicit modifying queries for atomic PostgreSQL creation/upsert/payment operations. JPA optimistic locking protects entity updates, but it does not replace conditional business-state transitions.

## Transactional outbox and notification reliability

Paying an order does not call an external provider inside the payment request. Instead, one transaction changes the order from `CREATED` to `PAID` and inserts an `outbox_events` row with status `PENDING`.

- If the transaction commits, both the paid order and its pending notification event are durable.
- If it rolls back, neither change remains in PostgreSQL.
- `OutboxEventDeliveryWorker` atomically claims due `PENDING` events as `PROCESSING`, publishes the stable event ID and order ID to Kafka, and marks successful Kafka publication `SENT`. PostgreSQL uses `FOR UPDATE SKIP LOCKED`, so concurrent application instances claim different rows rather than publishing one event together.
- A claim has a five-minute lease (`claimed_at`) and a unique `claim_token`. A later poll releases an abandoned lease after a crash; completion updates require the same token, so a stale worker cannot update a reclaimed event.
- A delivery failure records the error, increments `attempt_count`, and retries one minute later. After the third failed delivery attempt, the event becomes `FAILED` instead of retrying forever.
- The default profile uses `InMemoryOutboxEventRepository`; both `postgres` and `jpa` profiles use `JdbcOutboxEventRepository` against PostgreSQL. The JPA order adapter and JDBC outbox write were tested together for commit and rollback behavior.

The scheduler is deliberately opt-in. Set `OUTBOX_DELIVERY_ENABLED=true` in a deployed environment to enable its five-second polling loop. It stays disabled by default so test-created `PENDING` rows cannot be consumed by a background job.

This is an **at-least-once** delivery design. If the process stops after Kafka accepts an event but before its outbox row is marked `SENT`, it can be published again. The notification consumer records completed event IDs so a repeated Kafka message does not send a repeated customer notification. Temporary consumer failures are retried; exhausted messages go to `order-paid.DLT` for investigation.

## Run with Docker Compose

Docker Compose is the easiest way to run the complete local runtime without
installing Java, Maven, PostgreSQL, Redis, or Kafka separately.

Create an ignored `.env` file with these two values; never commit it:

```text
ORDER_ENGINE_DB_PASSWORD=choose-a-local-password
JWT_BASE64_SECRET=your-base64-encoded-secret
```

Build and start the local environment:

```powershell
docker compose up -d --build
docker compose ps
```

The application is available at `http://localhost:8081`, including health at
`/actuator/health` and Swagger UI at `/swagger-ui/index.html`.

Stop application containers while preserving PostgreSQL data:

```powershell
docker compose down
```

`docker compose down -v` also deletes the named PostgreSQL volume and its
data. Use it only when a fresh local database is intended.

## Run directly from source

### Prerequisites

- Java 21
- Maven
- PostgreSQL with an empty `order_engine` database; Flyway creates the project schema on first startup
- A `DB_PASSWORD` environment variable containing the local PostgreSQL password

Run all tests:

```powershell
mvn test
```

Run with the default in-memory repository:

```powershell
mvn spring-boot:run
```

Run with JDBC persistence:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Run with the JPA/Hibernate adapter:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=jpa"
```

Enable automatic outbox delivery for a deployed run:

```powershell
$env:OUTBOX_DELIVERY_ENABLED="true"
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

The application reads its database password from `DB_PASSWORD`; do not commit passwords to `application.properties`.

## Project structure

```text
src/main/java/com/vishesh/orderengine/
|-- Order.java / OrderStatus.java             # domain state and rules
|-- OrderController.java                      # HTTP adapter
|-- OrderPaymentService.java                  # transactional payment workflow
|-- OutboxEvent*.java                          # durable notification-event contract and storage adapters
|-- OutboxEventDeliveryWorker.java             # send, retry, and terminal-failure workflow
|-- OutboxEventDeliveryScheduler.java          # opt-in periodic worker trigger
|-- OrderRepository.java                      # persistence abstraction
|-- InMemoryOrderRepository.java              # default implementation
|-- JdbcOrderRepository.java                  # explicit PostgreSQL SQL implementation
|-- JpaOrderRepository.java                   # domain-to-JPA adapter
|-- OrderEntity.java / OrderItemEntity.java   # Hibernate mappings
`-- OrderEntityJpaRepository.java             # Spring Data JPA queries
```

## Current scope and next steps

This is a modular-monolith backend project, not yet a complete production
service. It already includes Flyway migrations, Docker Compose, Redis cache
aside, Kafka/outbox reliability patterns, JWT security, resilience controls,
and GitHub Actions CI. The next planned improvements include a frontend,
production deployment infrastructure, and further operational hardening.

## Kubernetes local learning environment

The `k8s/` directory contains a two-replica Order Engine Deployment, internal
Service, ConfigMap template, health probes, resource rules, and rolling-update
strategy. It intentionally runs only the stateless application Pods in
Kubernetes; the local PostgreSQL, Redis, and Kafka services remain in Docker
Compose.

Machine-specific connection addresses and all secrets stay in ignored local
files. See [the local Kubernetes guide](docs/kubernetes-local.md) for setup,
verification, rolling update, rollback, and cleanup commands.
