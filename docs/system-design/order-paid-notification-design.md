# Reliable Paid-Order Notification Design
The deign comprises of the following set of things:
 along with the order pay implementation.
Custome try to pay for an order
this will instantly mark the poayment to PAID from created.
If for some reason we are not able to mark the order paid we will just send an error that the request cant be fulllfilled.
After that once the order is paid we will create an outbox event for notification.Both of these should be atomic  either complete or rolback completly.
After that we will have workers which will chevk which outbox events are ready to be delivered.
We will pick them and send them to kafka event queue.And then we can make the outbox event row mark as SENT. But only if the transaction is completed and the event is routed to kafka queue.
Else we will just retry till the limi is reached where worker will make those event as failed.

So now when the events are in queue we will check if any consumer is available to process that.
We will sent the event to them for processing and then the notifiaction will be sent to customer.
If for some reason the consumer not able to process the event then we will again try for a certain limit.Then we will mark the event to DLT queue for an engineer to review the issue.
For the complete process we will create some checks which will help use to build this reilable notification sevice which included Atomicity,Transactional properies,idempotency etc

## Goal

- Reliably notify a customer after their order is paid.
- Do not make the payment request wait for the SMS provider.

## Functional requirements

- Mark a valid CREATED order as PAID.
- Save a durable notification event whenever payment succeeds.
- Publish the saved event to Kafka.
- Send an SMS notification when the consumer receives the event.
- Retry temporary publishing or notification failures.
- Send permanently failing consumer messages to a dead-letter topic.
- Ignore duplicate events using the permanent event ID.

## Non-functional requirements

- A paid order must never be lost if the application crashes.
- Once payment and its outbox event are committed, later Kafka or SMS-provider failure must not reverse the PAID order.
- A customer must not receive duplicate notifications for the same event.
- The system must record enough logs, metrics, and DLT information for an engineer to investigate failures.
- Sensitive values, such as passwords and JWT secrets, must not be stored in source control.

## Out of scope
- Inventory reservation or stock updates.
- Email, push, or WhatsApp notifications.
- Exactly-once delivery across every system.
- A user interface for engineers to inspect DLT messages.

## High-level architecture
Client
  |
  v
Order API
  |
  v
PostgreSQL
  - orders
  - outbox_events
  - processed notification event IDs
  |
  v
Outbox delivery worker
  |
  v
Kafka topic: order-paid
  |
  v
Order-paid notification consumer
  |
  v
SMS provider client
  |
  v
External SMS provider
  |
  v
Customer


Outbox worker cannot publish to Kafka
  -> keep/retry the PostgreSQL outbox event
  -> eventually mark it FAILED after its retry limit

Notification consumer fails
  -> Kafka retry topics
  -> order-paid.DLT after its retry limit

Duplicate Kafka event
  -> check permanent event ID in PostgreSQL
  -> skip duplicate SMS

## API contract
POST /orders/{orderId}/pay

- `POST /orders/{orderId}/pay`
- Requires a valid JWT with `ORDER_WRITER`.
- On success, atomically save the order as `PAID` and create a `PENDING` outbox event.
- Return `200 OK` with the paid order. This does not mean the SMS has already been delivered.
- Return `401 Unauthorized` when the JWT is missing or invalid.
- Return `403 Forbidden` when the authenticated user lacks `ORDER_WRITER`.
- Return `404 Not Found` when the order does not exist.
- If the order is already `PAID`, return `200 OK` with its existing `PAID` state and do not create another notification event.

## Data model
order -> `Order_id`, `order_status`: SOurce of truth to determine if order is paid or not

- `outbox_events` — `id`, `order_id`, `event_type`, `status`,
  `attempt_count`, `next_attempt_at`, `claimed_at`, `claim_token`,
  `sent_at`, and `last_error`.

  This is the durable delivery work list. The worker claims eligible
  `PENDING` events, publishes them to Kafka, retries temporary failures,
  and records either `SENT` or terminal `FAILED`.
processed_notification_events-> permanent event_id, processed time:It is the durable idempotency record used by the notification consumer to ensure one event ID does not cause two SMS sends.

## Main payment and notification flow

When the order is marked as PAID, at the same time we create an outbox event. Both are saved together in PostgreSQL. After this is saved, payment and notification become separate. So even if notification is delayed or fails, the order will not go back to CREATED.

So in the order payment workflow we will first check if the user is authorised or not after that we will check if the user have writer role or not and only then we will make the payment call for that order.anythign fails we will send appropriate response to the end user.

So in the notification workflow we will have workers which will pick eligible outbox events which are ready to be delivered and we will sent them to kafka event queue.Her aslo we will be having a mechanism to try to publish these events to kafka and retry in case of any failure.But retry csn be done till a certain limit.Afetr that the event will be marked as FAILEd in outbox event status.Then from there the Notification consumer service will poll events from the kafka and pas them to SMS provider to sent the notifiacation in this we will make sure to check for idempotency as wellso that we dont sent 2 notification to 1 paid order. Authentication, authorization, invalid order, and database-transaction failures return an immediate HTTP error to the client. Kafka and SMS failures are handled asynchronously after payment has already succeeded.


Short:
1. Client calls `POST /orders/{orderId}/pay` with a Bearer token.
2. Spring Security validates the JWT, then checks `ORDER_WRITER`.
3. The payment service changes the order from `CREATED` to `PAID`.
4. In the same PostgreSQL transaction, it creates one `PENDING` outbox event.
5. PostgreSQL commits both changes, and the API returns `200 PAID`.
6. The outbox worker later claims the ready event and publishes it to Kafka.
7. After Kafka acknowledges it, the worker marks the outbox event `SENT`.
8. The notification consumer polls the `order-paid` Kafka topic.
9. It checks the permanent event ID in `processed_notification_events`.
10. If the event is new, it sends the SMS with that event ID as its idempotency key.
11. If SMS succeeds, the processed-event record remains. If it fails, Kafka retry handling starts; repeated failure eventually reaches the DLT.


## Failure handling and recovery
Before starting payment, we first check if the request data is valid. If it is not valid, we return 400 Bad Request.
Then we check if the user has a valid JWT. If not, we return 401 Unauthorized.
If the user is logged in but does not have the writer role, we return 403 Forbidden.
If the order does not exist, we return 404 Not Found.
If too many payment requests are coming at the same time, we return 429 Too Many Requests.

Now if the payment is done and the  order status IS PAID and the outbox event is creted .
Now the wroker will check and try to publish the valid events to kafka.Failure in doing so we tend to retyr a certain time and after that we will mark the=at event as failure and log it so that engineer can check.

If the worker successfully sends the event to Kafka, it marks the outbox event as SENT. Kafka keeps the event safely until the notification consumer reads it. The notification consumer is the one that tries to send the SMS.

Special Case: Kafka accepts the event, but the worker app crashes before marking the outbox event as SENT. After some time, the worker will pick that event again and send it to Kafka again. So Kafka can get the same event two times. This is okay because the notification consumer checks the permanent event ID and skips the second SMS.

Now in kafka the evtns are publish but the SMS provider times out/fails kafka will retry till certain attempts and after that it will move that event to DLT quee and logs the info so that engineers can review.If sms succeds but consumer crashes before kafka offset than kafka will again deliver the same event but this time the consumer wont do that again because the event id will be the idempotency key to skip duplication.

A DLT message is not automatically fixed; an engineer investigates the cause, fixes it if needed, and decides whether the event should be replayed.

## Scaling and bottlenecks
Right now each app Pod allows only 20 order-write requests per minute. This is a safety limit, not the real maximum traffic our project can handle.

Right now Kafka has only one partition. So only one notification consumer from the same consumer group can work on this topic at one time.


Order API:	
    What become busy:Too many HTTP payment/read requests	
    Safe scaling approach: Safe scaling approachRun multiple stateless application Pods behind the Kubernetes Service/load balancer.

PostgreSQL:	
    What become busy:Many writes, locks, connections, or slow queries	
    Safe scaling approach: Keep payment writes on the primary database; use indexes, connection pooling, query review, and eventually read replicas for heavy read traffic.

Redis:	
    What become busy:Many repeated order reads	
    Safe scaling approach: Cache frequently read orders with TTL/invalidation; Redis reduces read pressure but never replaces PostgreSQL as source of truth.

Outbox worker:	
    What become busy:Large number of ready outbox events	
    Safe scaling approach: Run multiple workers; atomic database claims ensure two workers do not deliver the same claimed row simultaneously.

Kafka + notification consumers:	
    What become busy:Consumer lag when messages arrive faster than SMS work completes	
    Safe scaling approach: Add Kafka partitions and consumer instances in the same consumer group; use the order ID as the message key to preserve order for one order.

SMS provider:	
    What become busy:Provider capacity, slow responses, or outages	
    Safe scaling approach: Keep timeout, bulkhead, circuit breaker, rate limiter, retries, and DLT handling; do not let one slow provider block all payments.


## Key trade-offs
Kafka asynchronous notification instead of direct SMS during payment
    Benefit:So that we can separate the dependency of order pament and ntification. make the transaction fast.
    Cost/Trade off: Order can be paid but sms ntoification can be failure for some reasone.

Transactional outbox instead of "save PAID, then send Kafka directly"
    Benefit: When payment succeeds, the outbox event is also saved with it. So we do not have a PAID order without a notification event saved somewhere.
    Cost/Trade off: separate table to store the data for outbox events including extra worker imple,emtation and complex oprations.

At-least-once delivery plus idempotency:
    Benefit:  Benefit: We do not lose an event if the app crashes or Kafka sends the same event again.
    Cost/Trade off: need to have idempotency logic implementation for that

Redis cache-aside reads:
    Benefit: Faster Reads
    Cost/Trade Off: Cache can show old data for some time. When an order changes, we remove its old cache value so the next read gets fresh data from PostgreSQL.

One local Kafka partition:
    Benefit: every event has one clear total order.
    Cost/Trade off: only one worker processes events, so throughput is limited.

DLT after retries
    Benefit: failed events will nto blockt he queue and move to DLT so that other valid prdervevent s can be processed
    Cost/ Trade off: Engineer need to check the DLT queue to find the issue for not processing events
