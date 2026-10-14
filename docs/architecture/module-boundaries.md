# Service boundaries

The project began as a modular monolith. Notification delivery has now been
extracted into an independently deployable **Notification Service**. The
services communicate through Kafka rather than by direct method calls or shared
database tables.

## Current event and data flow

```text
HTTP client / React frontend
            |
            v
       Order Engine
  api -> order -> outbox -> Kafka publisher
          |         |
          v         v
     order_engine    order-paid Kafka topic
     PostgreSQL                 |
                              v
                     Notification Service
                       Kafka consumer
                              |
              +---------------+----------------+
              v                                v
notification_service PostgreSQL            SMS provider
(processed event IDs)                 (external boundary)

Order Engine -> Redis cache
```

## Service ownership

| Service or component | Owns | Must not own |
| --- | --- | --- |
| Order Engine | Order API, order state, payment workflow, `orders`, `outbox_events`, and publishing paid-order events | SMS delivery or Notification Service database tables |
| Notification Service | Kafka consumption, notification retries/DLT, SMS-provider resilience, `processed_notification_events` | Direct reads or writes to Order Engine `orders` / `outbox_events` tables |
| Kafka | Durable asynchronous handoff of the `order-paid` event | Payment state or notification idempotency records |
| Redis | Temporary cached order copies | Authoritative order state |
| PostgreSQL databases | Durable records owned by their respective service | Cross-service writes |

## Database ownership

Two databases exist in the same local PostgreSQL server for convenience. They
are still separate ownership boundaries:

| Database | Owner | Important tables |
| --- | --- | --- |
| `order_engine` | Order Engine | `orders`, `order_items`, `outbox_events` |
| `notification_service` | Notification Service | `processed_notification_events` |

Notification Service receives `eventId` and `orderId` from Kafka. It does not
need and must not use a direct database connection to Order Engine. This keeps
the services independently deployable and prevents one service from silently
changing another service's data.

## Boundary rules

1. Only Order Engine may change an order from `CREATED` to `PAID`.
2. The Order Engine payment transaction writes the paid order and one `PENDING`
   outbox event together.
3. Order Engine marks an outbox event `SENT` only after Kafka acknowledges the
   event.
4. Notification Service is the only consumer that sends an SMS for
   `order-paid` events.
5. Kafka provides at-least-once delivery. Notification Service deduplicates
   with the permanent event ID before external SMS delivery.
6. A failure in Notification Service must not undo a completed payment.
7. Redis can be unavailable or cleared without losing order data because
   PostgreSQL remains the source of truth.

## Eventual consistency: payment and notification

### What is atomic

`OrderPaymentService` changes an order to `PAID` and inserts a `PENDING`
outbox event in one PostgreSQL transaction. Both commit together or both roll
back together. Therefore every successful payment leaves durable work that can
be published later.

### What happens later

The outbox worker claims due events, publishes them to Kafka, and marks them
`SENT` after Kafka acknowledges them. Notification Service independently
consumes the event and calls the SMS provider.

### Meaning of the states

- `OrderStatus.PAID` means payment succeeded.
- An outbox event with `SENT` means Kafka accepted the event. It does **not**
  mean that the customer has received an SMS.
- A Notification Service retry or DLT event means notification work needs more
  time or human investigation; it never reverses a valid payment.

For a short time an order can correctly be `PAID` while notification delivery
is still waiting, retrying, or in the DLT. This is eventual consistency:
separate systems safely reach their final outcome at different times.

## Reliability and resilience ownership

| Concern | Owner | Current approach |
| --- | --- | --- |
| Payment/outbox atomicity | Order Engine | One PostgreSQL transaction |
| Kafka publication retry | Order Engine | Claim lease, retry delay, terminal `FAILED` state |
| Duplicate Kafka event | Notification Service | Insert event ID once in `processed_notification_events` |
| Temporary SMS outage | Notification Service | Kafka retry topics and retry scheduler |
| Permanent message failure | Notification Service | `order-paid.DLT` for investigation |
| Slow or failing SMS provider | Notification Service | Timeout, bulkhead, circuit breaker |
| Read performance | Order Engine | Redis cache-aside with payment-time eviction |

## Why this is not a Saga

A Saga coordinates several business actions that may need compensation, such
as refunding a payment when inventory reservation fails. SMS delivery is a
notification after a valid payment; a failed SMS should not refund or undo that
payment. The correct response is retry, DLT, and investigation—not a Saga
compensation.

## Deployment implication

Order Engine and Notification Service can have different deployment and
scaling settings. For example, a payment traffic increase may require more
Order Engine copies, while a slow SMS provider may require carefully limited
Notification Service consumer concurrency. Kafka is the stable boundary that
allows each service to change independently.
