# 04 — CQRS & Event-Driven Design (Spring Cloud Stream + Transactional Outbox)

This doc explains the two patterns that tie the system together: an **event-driven
backbone** (Kafka via Spring Cloud Stream) used by every service, and **CQRS**
applied specifically inside the Trip service using Spring Data JPA (write side) and
Spring Data MongoDB (read side).

---

## 1. Event-driven backbone with Spring Cloud Stream

Most state in the system propagates as **immutable events**. Spring Cloud Stream
provides a messaging abstraction over Kafka: you write plain Java functions
(`Supplier`, `Consumer`, `Function`) and the framework wires them to Kafka topics
via binder configuration.

### Why Kafka + Spring Cloud Stream
- **Durable, replayable log** — events are retained; a new or recovered consumer
  can replay history. Spring Cloud Stream consumers reset offsets to replay.
- **High throughput with partitioning** — partition by `trip_id` or `city` to keep
  ordering where it matters while parallelizing.
- **Consumer groups** — multiple service instances share a group id so each event
  is processed once; different service types use different groups for fan-out.
- **Spring Cloud Bus** can broadcast config refresh or cache eviction events over
  the same Kafka cluster.

### Topic design

| Topic | Partition key | Produced by | Consumed by |
|-------|--------------|-------------|-------------|
| `trip.events` | `trip_id` | Trip | Notification, Pricing, Matching, Analytics, WS Gateway |
| `ride.requests` | `city` | Trip | Matching |
| `match.events` | `trip_id` | Matching | Trip, Notification |
| `driver.events` | `driver_id` | Driver | Matching, Pricing, Analytics |
| `pricing.events` | `zone` | Pricing | Trip, Notification, Analytics |
| `payment.events` | `trip_id` | User/Payment | Trip, Notification |

### Spring Cloud Stream bindings

```yaml
# application.yml — shared pattern for all services
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: kafka:9092
          configuration:
            enable.idempotence: true          # exactly-once producer semantics
            acks: all
      bindings:
        # Outbound
        rideRequested-out-0:
          destination: trip.events
          contentType: application/json
          producer:
            partition-key-expression: headers['trip_id']
            partition-count: 12
        # Inbound
        onTripEvent-in-0:
          destination: trip.events
          group: notification-service          # consumer group = one delivery
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
```

### Producing events with StreamBridge

`StreamBridge` is the imperative way to publish from inside a `@Transactional` block
(e.g. from a command handler that doesn't return a message but needs to publish after
the DB commit):

```java
@Service
@Transactional
public class TripCommandService {

    private final TripRepository tripRepository;
    private final OutboxRepository outboxRepository;

    public Trip confirmTrip(ConfirmTripCommand cmd) {
        Trip trip = tripRepository.findById(cmd.getTripId()).orElseThrow();
        trip.confirm();
        tripRepository.save(trip);

        // Write domain event to outbox — same transaction as state change
        outboxRepository.save(OutboxEvent.of(
            trip.getId(), "RideRequested", toJson(RideRequestedEvent.from(trip))
        ));

        return trip;  // outbox relay publishes to Kafka asynchronously
    }
}
```

### Consuming events

```java
@Configuration
public class TripEventConsumers {

    @Bean
    public Consumer<Message<DriverMatchedEvent>> onDriverMatched(
            TripCommandService commandService) {
        return message -> {
            DriverMatchedEvent event = message.getPayload();
            commandService.applyDriverMatched(event);
        };
    }
}
```

---

## 2. Event catalog

Every event is a plain Java record serialized to JSON. Every event carries:
`eventId` (UUID, for dedupe), `tripId` (correlation), `occurredAt`, and `version`.

| Event | Emitted when | Key payload | Spring Cloud Stream topic |
|-------|-------------|-------------|--------------------------|
| `RideRequested` | Rider confirms a trip | tripId, riderId, pickup, dropoff, fareEstimate | `trip.events` |
| `DriverMatched` | Matching reserves a driver | tripId, driverId, eta | `match.events` |
| `MatchRejected` / `MatchTimedOut` | Driver declines / no answer | tripId, driverId | `match.events` |
| `DriverArriving` | Driver near pickup | tripId, eta | `trip.events` |
| `TripStarted` | Driver starts the trip | tripId, startTs, startLoc | `trip.events` |
| `TripCompleted` | Driver ends the trip | tripId, endTs, distance, duration, finalFare | `trip.events` |
| `TripCancelled` | Rider/driver/system cancels | tripId, by, reason, fee | `trip.events` |
| `PaymentCaptured` | Fare charged | tripId, amount, status | `payment.events` |
| `SurgeUpdated` | Surge recomputed for a zone | zone, multiplier, ts | `pricing.events` |

**Schema registry:** use Confluent Schema Registry (JSON Schema or Avro) with the
Spring Cloud Stream schema registry support to enforce backward compatibility:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-stream-schema-registry-client</artifactId>
</dependency>
```

---

## 3. CQRS in the Trip service

**Command Query Responsibility Segregation** separates the model that **changes
state** (commands / Spring Data JPA) from the model that **answers queries**
(reads / Spring Data MongoDB).

```mermaid
flowchart LR
    subgraph Write Side
        C[REST Commands<br/>POST /trips] --> H[TripCommandService]
        H --> PG[(PostgreSQL<br/>Spring Data JPA)]
        PG --> OUT[outbox table]
    end
    OUT -->|OutboxRelay @Scheduled| K[(Kafka trip.events)]
    subgraph Read Side
        K --> PROJ[TripProjector<br/>@KafkaListener]
        PROJ --> MG[(MongoDB<br/>Spring Data MongoDB)]
    end
    Q[REST Queries<br/>GET /trips] --> MG
```

### Write side — Spring Data JPA + PostgreSQL

```java
@Entity
@Table(name = "trips")
public class Trip {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) private TripStatus status;
    private UUID riderId;
    private UUID driverId;
    private BigDecimal fareLocked;
    private BigDecimal fareFinal;
    @Version private Long version;           // optimistic locking, prevents lost updates

    public void confirm() {
        if (status != TripStatus.PENDING) throw new IllegalStateException();
        this.status = TripStatus.REQUESTED;
    }
    // other transitions: applyDriverMatched, start, complete, cancel
}
```

#### Transactional Outbox pattern

In the **same `@Transactional` block** as the state change, a row is written to the
`outbox` table. A separate `@Scheduled` relay reads unpublished rows, sends them to
Kafka, and marks them published. This guarantees "state changed ⟺ event published"
with no dual-write race.

```java
@Entity
@Table(name = "outbox")
public class OutboxEvent {
    @Id @GeneratedValue private UUID id;
    private UUID aggregateId;
    private String eventType;
    @Column(columnDefinition = "jsonb")
    private String payload;
    private Instant publishedAt;     // NULL = not yet published
}

@Component
@Transactional
public class OutboxRelay {

    private final OutboxRepository outboxRepo;
    private final StreamBridge streamBridge;

    @Scheduled(fixedDelay = 500)      // poll every 500ms
    public void relay() {
        List<OutboxEvent> pending = outboxRepo.findTop100ByPublishedAtIsNull(
            Sort.by("id")
        );
        for (OutboxEvent evt : pending) {
            streamBridge.send("trip.events", buildMessage(evt));
            evt.setPublishedAt(Instant.now());
            outboxRepo.save(evt);
        }
    }
}
```

> **Alternative:** use **Debezium** (CDC on the `outbox` table) for sub-millisecond
> relay latency without the polling overhead. Debezium connects to PostgreSQL WAL
> and publishes outbox rows directly to Kafka, removing the `@Scheduled` loop.

### Read side — Spring Data MongoDB projector

```java
@Component
public class TripProjector {

    private final TripReadRepository readRepo;

    @Bean
    public Consumer<Message<TripEvent>> onTripEvent() {
        return message -> {
            TripEvent event = message.getPayload();

            // Idempotency: skip if already processed
            if (readRepo.existsByProcessedEventIds(event.getEventId())) return;

            TripReadModel doc = readRepo.findById(event.getTripId())
                .orElseGet(() -> new TripReadModel(event.getTripId()));

            applyEvent(doc, event);
            readRepo.save(doc);
        };
    }
}
```

- All queries ("my trips", "active rides", "driver earnings") hit MongoDB —
  fast single-document lookups, no joins, independently scalable.
- Read models are **disposable**: drop the collection and replay `trip.events` from
  offset 0 to rebuild.

### Consistency model

Reads are **eventually consistent** (sub-second lag while the projector catches up).
Where a read must be immediately consistent (e.g. the rider's own just-confirmed
trip), the app can read-through the write side with a direct JPA query or use the
command response payload directly.

---

## 4. Delivery guarantees & idempotency

| Concern | Spring / Kafka mechanism |
|---------|--------------------------|
| At-least-once delivery | Default Kafka consumer semantics; offset committed after successful processing |
| Consumer idempotency | Track `event_id` in a Redis set or a `processed_events` table; skip if present |
| Producer idempotency | `enable.idempotence=true` on the Kafka producer (Spring Cloud Stream config) |
| Command idempotency | `idempotency_key` unique constraint on the `trips` table; Spring `@Transactional` rolls back duplicates |
| Per-trip ordering | Kafka partition key = `trip_id`; single-partition consumers process a trip's events in order |

---

## 5. Saga: handling the multi-step trip flow

A trip spans Trip → Matching → Pricing → Payment. Instead of a distributed
transaction, use a **choreographed saga**: each step emits a Spring Cloud Stream
event; the next service reacts; failures emit **compensating events**.

Example — payment fails after trip completion:

1. `TripCompleted` → Kafka → Payment Service attempts charge.
2. Charge fails → Payment Service publishes `PaymentFailed`.
3. Trip's `onPaymentFailed` consumer sets `status = PAYMENT_PENDING`.
4. Notification's `onPaymentFailed` consumer prompts the rider to update payment.
5. Retry policy (`@Scheduled` or Spring Retry) re-triggers charge. No locks held
   across services; no two-phase commit.

```java
// Trip Service: compensating consumer
@Bean
public Consumer<Message<PaymentFailedEvent>> onPaymentFailed() {
    return message -> {
        PaymentFailedEvent evt = message.getPayload();
        tripCommandService.markPaymentPending(evt.getTripId());
    };
}
```

The saga state is implicit in each service's local store — no saga orchestrator
service needed for this choreographed approach.
