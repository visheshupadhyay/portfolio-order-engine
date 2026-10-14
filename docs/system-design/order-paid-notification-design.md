# Reliable Paid-Order Notification Design

## Goal

When a valid order is paid, mark it `PAID` and reliably notify the customer
without making the payment request wait for an external SMS provider.

## Functional requirements

- Mark a valid `CREATED` order as `PAID`.
- Save a durable notification event whenever payment succeeds.
- Publish the saved event to Kafka.
- Send an SMS when Notification Service receives the event.
- Retry temporary Kafka-publication and notification failures.
- Move permanently failing notification messages to a dead-letter topic.
- Ignore duplicate Kafka events using their permanent event ID.

## Non-functional requirements

- A committed payment must never be lost because Kafka or the SMS provider is
  temporarily unavailable.
- A later notification failure must not reverse a valid `PAID` order.
- One event must not produce two customer SMS messages.
- Logs, metrics, and DLT records must provide enough evidence for an engineer
  to investigate a failure.
- Passwords, JWT secrets, and provider credentials must remain outside source
  control.

## Out of scope

- Inventory reservation or stock updates.
- Email, push, or WhatsApp notifications.
- Exactly-once delivery across PostgreSQL, Kafka, and an SMS provider.
- An engineer-facing UI for inspecting or replaying DLT messages.

## High-level architecture

```text
Client
  |
  v
Order Engine API
  |
  v
order_engine PostgreSQL
  - orders
  - outbox_events
  |
  v
Outbox delivery worker
  |
  v
Kafka topic: order-paid
  |
  v
Notification Service
  |
  +--> notification_service PostgreSQL
  |    - processed_notification_events
  |
  v
SMS provider client
  |
  v
External SMS provider
  |
  v
Customer
```

## API contract

`POST /orders/{orderId}/pay`

- Requires a valid JWT with `ORDER_WRITER`.
- On success, atomically save the order as `PAID` and create one `PENDING`
  outbox event.
- Return `200 OK` with the paid order. This does not mean the SMS has already
  been delivered.
- Return `401 Unauthorized` when the JWT is missing or invalid.
- Return `403 Forbidden` when the authenticated user lacks `ORDER_WRITER`.
- Return `404 Not Found` when the order does not exist.
- If the order is already `PAID`, return `200 OK` with its existing state and
  do not create another outbox event.

## Data ownership

### Order Engine database: `order_engine`

- `orders`: source of truth for order ID, payment status, and version.
- `outbox_events`: event ID, order ID, event type, delivery status, retry
  count, next attempt time, claim lease/token, sent time, and last error.

The outbox worker claims eligible `PENDING` events, publishes them to Kafka,
and records either `SENT` or terminal `FAILED`.

### Notification Service database: `notification_service`

- `processed_notification_events`: permanent event ID and processed time.

This is the idempotency record. It belongs to Notification Service because it
decides whether an incoming Kafka event should cause external customer work.

## Main payment and notification flow

1. Client calls `POST /orders/{orderId}/pay` with a Bearer token.
2. Spring Security validates the JWT and requires `ORDER_WRITER`.
3. Order Engine changes the order from `CREATED` to `PAID`.
4. In the same PostgreSQL transaction, it creates one `PENDING` outbox event.
5. PostgreSQL commits both changes and the API returns `200 PAID`.
6. The outbox worker later claims the ready event and publishes it to Kafka.
7. After Kafka acknowledges it, the worker marks the outbox event `SENT`.
8. Notification Service consumes the `order-paid` Kafka event.
9. It tries to insert the permanent event ID into
   `processed_notification_events`.
10. If the event is new, it sends the SMS using that event ID as the provider
    idempotency key.
11. If the event is already recorded, Notification Service skips the duplicate
    SMS.
12. If notification processing fails before successful delivery, Kafka retry
    handling runs; repeated failure eventually reaches `order-paid.DLT`.

## Failure handling and recovery

### Before payment commits

- Invalid request data: `400 Bad Request`.
- Missing or invalid JWT: `401 Unauthorized`.
- Valid user without writer permission: `403 Forbidden`.
- Missing order: `404 Not Found`.
- Too many write requests: `429 Too Many Requests`.
- Database transaction failure: no paid order and no outbox event are kept.

### After payment commits

If Kafka is unavailable, the `PENDING` outbox event remains durable. The
worker retries it up to its configured limit, then records a terminal `FAILED`
state for investigation.

If Kafka accepts an event but Order Engine crashes before recording `SENT`, the
worker can publish it again after recovering the expired claim. This is why
Kafka delivery is at least once.

If Notification Service cannot call the SMS provider, Kafka retry topics retry
the event. A message that keeps failing moves to `order-paid.DLT`. An engineer
must investigate the cause and decide whether a repaired message should be
replayed.

If SMS succeeds but Notification Service crashes before Kafka commits its
offset, Kafka can deliver the event again. The processed-event record means the
duplicate delivery is skipped rather than sending a second SMS.

## Scaling and bottlenecks

| Component | What can become busy | Safe scaling approach |
| --- | --- | --- |
| Order Engine API | HTTP payment/read traffic | Run multiple stateless copies behind a load balancer. |
| PostgreSQL | Writes, locks, connections, slow queries | Keep payment writes on the primary; use indexing, connection pooling, query review, and eventually read replicas. |
| Redis | Repeated order reads | Cache frequently read orders with TTL/invalidation; Redis never replaces PostgreSQL. |
| Outbox worker | Many ready outbox events | Run multiple workers; atomic database claims prevent duplicate claims. |
| Kafka and Notification Service | Consumer lag when SMS work is slow | Add Kafka partitions and consumer instances; use the order ID as key to preserve per-order order. |
| SMS provider | Capacity, latency, outage | Apply timeout, bulkhead, circuit breaker, retry, and DLT handling. |

The local topic has one partition, so only one consumer in a consumer group can
actively process it. More partitions allow more consumers to work in parallel,
but ordering is then guaranteed only within each partition.

## Key trade-offs

| Decision | Benefit | Cost |
| --- | --- | --- |
| Kafka notification instead of direct SMS during payment | Payment is fast and independent of SMS-provider availability. | An order can be paid before its notification arrives. |
| Transactional outbox instead of “save PAID, then publish Kafka directly” | A successful payment always has durable event work. | Extra table, worker, and operational complexity. |
| At-least-once delivery plus idempotency | A crash does not silently lose a notification event. | Duplicate-handling logic and a durable idempotency record are required. |
| Redis cache-aside | Faster repeated reads. | Cache can be stale until eviction/refresh. |
| One Kafka partition locally | Simple total event order. | One active consumer limits notification throughput. |
| DLT after retries | A poisoned message does not block healthy work forever. | A person or tool must investigate and possibly replay it. |

## Why no Saga is implemented

Saga is useful when a later business failure must compensate earlier business
work, such as refunding a payment after inventory reservation fails. In this
project, a failed SMS is not a reason to undo a valid payment. Retry and DLT
handling are the correct recovery mechanisms.
