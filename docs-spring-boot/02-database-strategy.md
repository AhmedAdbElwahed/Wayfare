# 02 — Database Strategy (Spring Boot & Spring Data)

Each service owns its data exclusively (database-per-service pattern). No shared
schema, no cross-service joins — data is exchanged through Spring MVC/gRPC APIs and
Spring Cloud Stream events only. This document maps every service to its datastore
and the **Spring Data module** that accesses it.

---

## Summary table

| Service | Primary store | Secondary store | Spring Data module(s) | Why (one line) |
|---------|--------------|-----------------|----------------------|----------------|
| User | **PostgreSQL** | Redis (session cache) | `spring-data-jpa` + `spring-data-redis` | Relational integrity for accounts, payments, history; ACID matters |
| Driver | **PostgreSQL** | **Redis (geospatial)** | `spring-data-jpa` + `spring-data-redis` | Profile/docs need relations; live GPS needs in-memory geo writes |
| Matching | **Redis** | — (Kafka for input) | `spring-data-redis` | Pure sub-millisecond geospatial reads + ephemeral match state |
| Pricing | **Redis** | **TimescaleDB / PostgreSQL** | `spring-data-redis` + `spring-data-jpa` | Real-time counters in Redis; rules + history in time-series/SQL |
| Trip | **PostgreSQL** (write/event store) | **MongoDB** (read models) | `spring-data-jpa` + `spring-data-mongodb` | CQRS: strong-consistency source of truth + denormalized reads |
| Notification | **Cassandra** | Redis (dedupe/rate) | `spring-data-cassandra` + `spring-data-redis` | Append-heavy, write-optimized, multi-region delivery log |

---

## 1. User Service → PostgreSQL via Spring Data JPA

**Data:** `users`, `profiles`, `payment_methods`, `addresses`, `ratings_given`,
`history_index`.

**Spring Data module:** `spring-boot-starter-data-jpa` (Hibernate as the JPA
provider) + `spring-boot-starter-data-redis` (Lettuce) for caching.

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
</dependency>
```

**Why PostgreSQL + JPA:**
- User and payment data is inherently **relational** (user → cards → addresses →
  history). JPA entities and foreign key constraints enforce this at the DB level.
- **ACID transactions** via `@Transactional` — you don't want a half-written payment
  method or a duplicate account from a race.
- Mature support for **row-level security** and encryption; PII compliance (GDPR).
- Read-heavy profile lookups are cached in Redis using Spring Cache (`@Cacheable` +
  a `RedisCacheManager`); short TTL + `@CacheEvict` on profile updates.

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String phone;
    private String email;
    // ...
}

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
}
```

**Database migrations:** Flyway (`spring-boot-starter-flyway`) — migration scripts
run automatically on startup.

---

## 2. Driver Service → PostgreSQL + Redis (geospatial)

Two very different data profiles; two stores.

**Profile data → Spring Data JPA / PostgreSQL:** driver identity, vehicle details,
documents, license/insurance expiry, approval status, payout account. Same reasoning
as User: relational, ACID, compliance-sensitive, low write rate.

**Live location → Spring Data Redis geospatial:** drivers emit GPS every 3–5 seconds.

```java
@Service
public class DriverLocationService {

    private final RedisTemplate<String, String> redisTemplate;

    public void updateLocation(String driverId, double lng, double lat, String city) {
        GeoOperations<String, String> geo = redisTemplate.opsForGeo();
        // Updates sorted-set position for radius queries
        geo.add("drivers:geo:" + city, new Point(lng, lat), driverId);
        // Store full payload with TTL so offline drivers auto-expire
        redisTemplate.opsForValue().set(
            "driver:loc:" + driverId,
            buildPayload(lng, lat),
            Duration.ofSeconds(30)
        );
    }
}
```

**Why Redis for location:**
- `GeoOperations.radius()` gives "drivers within X km" out of the box — O(log n).
- In-memory: a GPS update is a single fast write with no JPA flush or disk fsync on
  the hot path.
- Location is **ephemeral** — TTLs auto-expire stale drivers.

> Detailed location ingestion flow: [03-realtime-websockets.md](./03-realtime-websockets.md).

---

## 3. Matching Service → Redis via Spring Data Redis

**Data:** live driver geo-index + short-lived match reservation locks.

```java
@Service
public class MatchReservationService {

    private final RedisTemplate<String, String> redisTemplate;

    // SET NX EX — atomic reservation, no double-booking
    public boolean reserveDriver(String driverId, String tripId, long ttlSeconds) {
        Boolean set = redisTemplate.opsForValue().setIfAbsent(
            "match:reserve:" + driverId,
            tripId,
            Duration.ofSeconds(ttlSeconds)
        );
        return Boolean.TRUE.equals(set);
    }

    public List<String> findNearbyDrivers(String city, double lng, double lat, double radiusKm) {
        GeoOperations<String, String> geo = redisTemplate.opsForGeo();
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geo.radius(
            "drivers:geo:" + city,
            new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS))
        );
        return results.getContent().stream()
            .map(r -> r.getContent().getName())
            .collect(toList());
    }
}
```

There is **no durable state worth keeping**: on restart, Matching re-consumes
pending `ride.requests` from Kafka (offset committed only after successful dispatch)
and reads the current geo-index.

---

## 4. Pricing Service → Redis + TimescaleDB/PostgreSQL

**Real-time surge → Spring Data Redis:**

```java
@Service
public class SurgeCounterService {

    private final RedisTemplate<String, Long> redisTemplate;

    public void incrementDemand(String zone) {
        redisTemplate.opsForValue().increment("demand:" + zone);
        redisTemplate.expire("demand:" + zone, Duration.ofMinutes(5));
    }

    public Double getCurrentSurge(String zone) {
        // Read cached multiplier (computed by surge calculator job)
        String val = (String) redisTemplate.opsForValue().get("surge:" + zone);
        return val != null ? Double.parseDouble(val) : 1.0;
    }
}
```

**Fare rules + history → Spring Data JPA / TimescaleDB:**
TimescaleDB is a PostgreSQL extension, so `spring-boot-starter-data-jpa` works
unchanged — Hibernate talks to it like regular PostgreSQL. Time-bucket queries for
analytics use native SQL via `@Query`:

```java
@Repository
public interface SurgeHistoryRepository extends JpaRepository<SurgeHistory, Long> {

    @Query(value = """
        SELECT time_bucket('15 minutes', recorded_at) AS bucket,
               zone,
               AVG(multiplier) AS avg_surge
        FROM surge_history
        WHERE zone = :zone AND recorded_at >= :from
        GROUP BY bucket, zone
        ORDER BY bucket
        """, nativeQuery = true)
    List<SurgeBucket> findBuckets(@Param("zone") String zone,
                                   @Param("from") Instant from);
}
```

---

## 5. Trip Service → PostgreSQL (write) + MongoDB (read) — the CQRS core

Trip is the only service applying **CQRS** with separate write and read stores.

**Write side → Spring Data JPA / PostgreSQL:**

```java
@Entity
@Table(name = "trips")
public class Trip {
    @Id private UUID id;
    private UUID riderId;
    private UUID driverId;
    @Enumerated(EnumType.STRING)
    private TripStatus status;
    private BigDecimal fareLocked;
    private BigDecimal fareFinal;
    @Version private Long version;   // optimistic locking
    // ...
}

@Entity
@Table(name = "outbox")
public class OutboxEvent {
    @Id @GeneratedValue private UUID id;
    private UUID aggregateId;
    private String eventType;
    @Column(columnDefinition = "jsonb")
    private String payload;
    private Instant publishedAt;     // NULL = unpublished
}
```

Commands mutate trip state and write to the outbox **in the same `@Transactional`
block**. A scheduled `@Component` (or Debezium CDC) polls the outbox and publishes
to Kafka, then marks rows as published.

**Read side → Spring Data MongoDB:**

```java
@Document(collection = "trips_read")
public class TripReadModel {
    @Id private String id;
    private RiderInfo rider;
    private DriverInfo driver;
    private String status;
    private FareInfo fare;
    private RouteInfo route;
    private List<TimelineEntry> timeline;
}

public interface TripReadRepository extends MongoRepository<TripReadModel, String> {
    List<TripReadModel> findByRiderIdOrderByCreatedAtDesc(String riderId);
}
```

A `@KafkaListener` projector consumes `trip.events`, builds/updates these documents,
and stores them in MongoDB. Read models are **disposable** — drop and replay to
rebuild.

```yaml
spring:
  data:
    jpa:
      repositories:
        enabled: true
    mongodb:
      uri: mongodb://mongo:27017/trip_read
```

> Full CQRS mechanics: [04-cqrs-event-driven.md](./04-cqrs-event-driven.md).

---

## 6. Notification Service → Cassandra via Spring Data Cassandra

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-cassandra</artifactId>
</dependency>
```

```java
@Table("notifications_by_user")
public class NotificationRecord {
    @PrimaryKeyColumn(ordinal = 0, type = PARTITIONED)
    private UUID userId;
    @PrimaryKeyColumn(ordinal = 1, type = CLUSTERED, ordering = DESCENDING)
    private Instant sentAt;
    private UUID notificationId;
    private String channel;
    private String eventType;
    private String status;
}

public interface NotificationRepository
    extends CassandraRepository<NotificationRecord, NotificationPrimaryKey> { }
```

**Why Cassandra + Spring Data Cassandra:**
- Workload is **write-heavy and append-only**. Spring Data Cassandra maps directly to
  the wide-row model without any ORM translation overhead.
- Partition key `user_id` + clustering column `sent_at` = efficient time-ordered
  reads per user, matching Cassandra's strengths exactly.
- **Redis** (via Spring Data Redis) handles **dedupe and rate-limiting** with
  `setIfAbsent` + TTLs.

---

## Decision principles recap

1. **Relational + money/compliance → PostgreSQL + Spring Data JPA.**
2. **High-frequency, ephemeral, geo/atomic → Redis + Spring Data Redis (`GeoOperations`, `ValueOperations`).**
3. **Write-heavy, append-only, simple access → Cassandra + Spring Data Cassandra.**
4. **Denormalized query-shaped reads → MongoDB + Spring Data MongoDB.**
5. **Time-series analytics → TimescaleDB (accessed via Spring Data JPA / native `@Query`).**
6. **Cache the hot read path** with Spring Cache (`@Cacheable`) backed by
   `RedisCacheManager` — short TTLs, `@CacheEvict` on mutations.
