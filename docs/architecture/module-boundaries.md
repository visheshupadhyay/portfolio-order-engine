# Module boundaries

This application is currently a **modular monolith**: one deployable Spring
Boot application containing modules with separate responsibilities. These
boundaries make the system easier to understand today and safer to split into
independent services later.

## Current event and data flow

```text
HTTP client
    |
    v
api -> order -> outbox -> Kafka -> message -> notification
                  |
                  v
              PostgreSQL

api/order -> cache -> Redis
```

- PostgreSQL is the source of truth for durable order state and durable outbox
  records.
- Redis holds only temporary cached order copies. Losing Redis must not lose an
  order.
- The outbox records an event in PostgreSQL before publication, so a successful
  payment is not lost if Kafka is temporarily unavailable.
- Kafka carries the paid-order event from the publishing side to the
  notification side without the payment request waiting for SMS delivery.
- The notification module sends the SMS and records completed event IDs so a
  repeated Kafka event does not send a repeated customer message.

## Module responsibilities

| Module | Owns | Must not own |
| --- | --- | --- |
| `api` | HTTP request/response mapping and validation | Order persistence or Kafka delivery logic |
| `order` | Order state and payment workflow | SMS-provider logic |
| `outbox` | Durable event rows and publishing recovery | HTTP-controller logic |
| `message` | Kafka event format, publishing, and consuming | Direct order-state changes |
| `notification` | SMS delivery and duplicate-notification protection | Direct order-table access |
| `cache` | Temporary Redis copies | Authoritative order state |
| `security` | Login, JWT validation, and authorization | Business or payment logic |

## Boundary rules

1. `notification` receives an order ID through `OrderPaidMessage`; it must not
   call `OrderRepository` or write directly to the `orders` table.
2. `message` publishes and consumes Kafka records; it must not mark an order as
   paid.
3. Only the order/payment workflow may change an order from `CREATED` to
   `PAID`.
4. Redis can be unavailable or cleared without losing order data because
   PostgreSQL is the source of truth.
5. The outbox row becomes `SENT` only after Kafka acknowledges the event.
6. Kafka delivery is at-least-once, so the notification module deduplicates
   repeated messages using the permanent event ID.

## Eventual consistency: payment and notification

### What is atomic

`OrderPaymentService` saves the order state as `PAID` and creates a `PENDING`
outbox event in the same PostgreSQL transaction. Either both changes commit or
both changes roll back, so a successful payment always leaves durable work for
notification delivery.

### What happens later

The outbox worker publishes the saved event to Kafka and marks the outbox row
as `SENT` only after Kafka acknowledges it. The Kafka notification consumer
then calls the SMS provider. Temporary notification failures are retried; a
message that continues to fail is sent to the dead-letter topic for
investigation.

### Meaning of the states

- `OrderStatus.PAID` means payment succeeded.
- An outbox event with status `SENT` means Kafka accepted the event. It does
  **not** mean that the customer has received an SMS.
- An SMS delivery failure delays or exhausts notification delivery; it never
  reverses a committed payment.

For a short time, an order can correctly be `PAID` while its notification is
still waiting, retrying, or being investigated from the dead-letter topic.
This is eventual consistency: independent parts of the workflow safely reach
their final outcome at different times.

### Why this is not a full Saga

A Saga coordinates several important business steps and uses compensating
actions when a later step fails—for example, refunding a payment and releasing
inventory when delivery cannot be arranged. SMS delivery is only a
notification in this application, so an SMS failure must not compensate or
undo a valid payment.

## Resilience trade-offs and operating limits

The values below are deliberately small, visible configuration values for this
learning application. They are not universal production defaults; real values
should be chosen from traffic, latency, provider-capacity, and incident data.

| Protection | Current setting | Why it exists | Production consideration |
| --- | --- | --- | --- |
| SMS timeout | 3 seconds | A single SMS call must not wait forever for a provider response. | Tune from observed provider latency and the caller's overall request budget. |
| SMS bulkhead | 5 concurrent calls; no waiting queue | Slow SMS calls cannot consume unlimited application resources. | Tune from application thread capacity and provider throughput. |
| Circuit breaker | Evaluates after at least 2 calls; opens at 50% failures; waits 2 seconds; permits 1 recovery call | Repeated provider failures should temporarily stop new calls. | Tune the failure threshold and recovery time from real incident data. |
| Order-write rate limit | 20 requests per minute per application instance; no waiting queue | A request flood must not reach the controller, PostgreSQL, Redis, or Kafka. | This limiter is in-memory and local to one application instance. Use shared storage such as Redis for one limit across multiple instances. |
| PostgreSQL outbox publish retry | At most 3 attempts with a 1-minute delay | Kafka outages leave durable outbox work that can be retried later. | Alert when rows remain pending too long or become failed. |
| Kafka notification retry and DLT | 3 total attempts with 1-second backoff, then `order-paid.DLT` | Temporary notification failures can recover without blocking normal consumer progress. | Monitor the dead-letter topic and provide an operational replay/investigation process. |

Kafka delivery is at-least-once, so consumers must remain idempotent: the same
event can be delivered more than once without causing repeated customer work.

A dead-letter topic is not a success state. It means automatic processing has
stopped; a person or operational tool must investigate the message, repair the
underlying problem when possible, and decide whether to replay it.

## Future extraction

The notification module is the safest first candidate to become a separate
Notification Service. It already receives `OrderPaidMessage` events through
Kafka and does not need direct access to order data. A future extracted service
would consume the same event, own its notification-delivery records, and send
SMS messages independently of the Order Service.
