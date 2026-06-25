# 08 — Pricing Service (Spring Boot)

## Responsibility

Computes **fares** — both the base fare (distance + time + ride type + city rules)
and **dynamic surge** multipliers driven by real-time supply/demand. It answers
fare-estimate queries before a ride and locks the fare at match time.

---

## Spring Boot dependencies

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream</artifactId>
  </dependency>
  <dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-spring-boot-starter</artifactId>
    <version>3.1.0</version>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
  </dependency>
</dependencies>
```

---

## Two halves

### a. Fare rules (static-ish config) — Spring Data JPA / TimescaleDB

Base fare, per-km and per-minute rates, minimum fare, booking fees, promos, and
per-city / per-ride-type configuration stored in TimescaleDB (a PostgreSQL extension,
accessed via the same Hibernate JPA stack as regular PostgreSQL).

```java
@Entity @Table(name = "fare_rules")
public class FareRule {
    @Id @GeneratedValue private UUID id;
    private String city;
    @Enumerated(EnumType.STRING) private RideType rideType;
    private BigDecimal baseFare;
    private BigDecimal perKmRate;
    private BigDecimal perMinuteRate;
    private BigDecimal minimumFare;
    private BigDecimal bookingFee;
    private boolean active;
}

public interface FareRuleRepository extends JpaRepository<FareRule, UUID> {
    Optional<FareRule> findByCityAndRideTypeAndActiveTrue(String city, RideType rideType);
}
```

Surge history is written as time-series data via a native `@Query`:

```java
@Entity @Table(name = "surge_history")
public class SurgeHistory {
    @Id @GeneratedValue private Long id;
    private String zone;
    private double multiplier;
    private Instant recordedAt;
}

public interface SurgeHistoryRepository extends JpaRepository<SurgeHistory, Long> {

    @Query(value = """
        SELECT time_bucket('15 minutes', recorded_at) AS bucket,
               zone,
               AVG(multiplier) AS avg_surge,
               MAX(multiplier) AS peak_surge
        FROM surge_history
        WHERE zone = :zone AND recorded_at >= :from
        GROUP BY bucket, zone
        ORDER BY bucket DESC
        """, nativeQuery = true)
    List<SurgeBucketProjection> findSurgeBuckets(@Param("zone") String zone,
                                                  @Param("from") Instant from);
}
```

### b. Dynamic surge (real-time) — Spring Data Redis

Per-zone demand/supply counters updated continuously from Kafka events; surge
multiplier cached with a short TTL.

```mermaid
flowchart LR
    RR[ride.requests events] --> CNT[Redis demand:{zone} incr]
    DV[driver.events online/offline] --> SUP[Redis supply:{zone} incr/decr]
    CNT --> CALC[SurgeCalculatorJob @Scheduled]
    SUP --> CALC
    CALC --> SG[Redis surge:{zone} TTL 60s]
    CALC --> TS[(TimescaleDB surge history)]
    SG --> EST[gRPC EstimateFare / LockFare]
```

```java
@Component
public class SurgeCalculatorJob {

    private final RedisTemplate<String, String> redisTemplate;
    private final SurgeHistoryRepository surgeHistoryRepo;
    private final StreamBridge streamBridge;

    @Scheduled(fixedDelay = 10_000)    // recompute every 10 seconds per active zone
    public void recalculate() {
        Set<String> activeZones = getActiveZones();
        for (String zone : activeZones) {
            Long demand = getLong("demand:" + zone);
            Long supply = getLong("supply:" + zone);

            double multiplier = computeMultiplier(demand, supply);
            multiplier = Math.min(multiplier, maxSurge());          // regulatory cap
            multiplier = smooth(multiplier, getPrevious(zone));      // rate-limit changes

            redisTemplate.opsForValue().set(
                "surge:" + zone,
                String.valueOf(multiplier),
                Duration.ofSeconds(60)
            );

            // Async persist to TimescaleDB (off hot path)
            surgeHistoryRepo.save(new SurgeHistory(zone, multiplier, Instant.now()));

            // Broadcast for Analytics and Notification
            streamBridge.send("pricing.events-out-0",
                SurgeUpdatedEvent.of(zone, multiplier, Instant.now()));
        }
    }

    private double computeMultiplier(Long demand, Long supply) {
        if (supply == null || supply == 0) return maxSurge();
        double ratio = (double) demand / supply;
        if (ratio <= 1.0) return 1.0;
        return 1.0 + (ratio - 1.0) * surgeSensitivity();
    }
}
```

Surge cap and sensitivity are fetched from Config Server and refreshed at runtime:

```java
@ConfigurationProperties(prefix = "pricing.surge")
@RefreshScope
@Component
public class SurgeConfig {
    private double maxMultiplier = 3.0;
    private double sensitivity = 0.5;
}
```

---

## gRPC API

```protobuf
service PricingService {
  rpc EstimateFare (EstimateFareRequest) returns (FareEstimateResponse);
  rpc LockFare     (LockFareRequest)     returns (FareLockResponse);
  rpc FinalizeFare (FinalizeFareRequest) returns (FareFinalResponse);
}
```

```java
@GrpcService
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {

    private final FareCalculator fareCalculator;
    private final RedisTemplate<String, String> redisTemplate;
    private final FareLockRepository fareLockRepo;

    @Override
    public void estimateFare(EstimateFareRequest req, StreamObserver<FareEstimateResponse> obs) {
        double surge = getSurge(req.getZone());
        FareRule rule = fareCalculator.getRule(req.getCity(), req.getRideType());
        BigDecimal estimated = fareCalculator.compute(rule, req.getDistanceKm(),
                                                       req.getDurationMin(), surge);
        obs.onNext(FareEstimateResponse.newBuilder()
            .setAmount(estimated.doubleValue())
            .setSurgeMultiplier(surge)
            .build());
        obs.onCompleted();
    }

    @Override
    public void lockFare(LockFareRequest req, StreamObserver<FareLockResponse> obs) {
        // Fix the fare at match time — multiplier cannot move after this
        double surge = getSurge(req.getZone());
        FareLock lock = new FareLock(req.getTripId(), surge, Instant.now());
        fareLockRepo.save(lock);    // stored in Redis with TTL + persisted to DB

        obs.onNext(FareLockResponse.newBuilder()
            .setLockedSurge(surge)
            .build());
        obs.onCompleted();
    }

    private double getSurge(String zone) {
        String val = redisTemplate.opsForValue().get("surge:" + zone);
        return val != null ? Double.parseDouble(val) : 1.0;  // base fare fallback
    }
}
```

---

## Ops REST API

```java
@RestController
@RequestMapping("/pricing")
public class PricingOpsController {

    @GetMapping("/surge/{zone}")
    public Map<String, Object> getSurge(@PathVariable String zone) {
        String val = redisTemplate.opsForValue().get("surge:" + zone);
        return Map.of("zone", zone, "multiplier", val != null ? val : "1.0");
    }
}
```

---

## Spring Cloud Stream consumers

```java
@Configuration
public class PricingConsumers {

    @Bean
    public Consumer<Message<RideRequestedEvent>> onRideRequested(
            SurgeCounterService counter) {
        return message -> counter.incrementDemand(message.getPayload().getZone());
    }

    @Bean
    public Consumer<Message<DriverOnlineEvent>> onDriverOnline(
            SurgeCounterService counter) {
        return message -> counter.incrementSupply(message.getPayload().getCity());
    }

    @Bean
    public Consumer<Message<DriverOfflineEvent>> onDriverOffline(
            SurgeCounterService counter) {
        return message -> counter.decrementSupply(message.getPayload().getCity());
    }

    @Bean
    public Consumer<Message<TripCompletedEvent>> onTripCompleted(
            FinalizeService finalizeService) {
        return message -> finalizeService.finalize(message.getPayload());
    }
}
```

---

## Graceful degradation (Resilience4j)

```java
@Service
public class FareEstimateService {

    private final CircuitBreakerFactory cbFactory;

    public double getSurgeWithFallback(String zone) {
        CircuitBreaker cb = cbFactory.create("surge-redis");
        return cb.run(
            () -> Double.parseDouble(
                redisTemplate.opsForValue().get("surge:" + zone)
            ),
            ex -> getLastKnownSurge(zone)   // read from TimescaleDB
        );
    }
}
```

If the surge calculation is unavailable, fall back to the last cached multiplier or
base fare — **never block a ride on pricing**.

---

## Scaling & concerns

- **Counter updates are high-frequency but cheap** — Redis atomic `INCR`/`DECR` via
  `ValueOperations.increment()`. No JPA involved.
- **Surge recompute** runs on a short `@Scheduled` interval per active zone, not per
  request. The result is cached in Redis with a 60-second TTL.
- **TimescaleDB writes are async** — the `SurgeCalculatorJob` saves history off the
  hot gRPC path; use Spring's `@Async` + a dedicated thread pool.
- **Regulatory:** the TimescaleDB `surge_history` table is the auditable record
  explaining every multiplier at every moment ("why was surge 2.3x at 6pm Friday?").
- **A/B testable:** pricing strategies live in Config Server; changing `surge.sensitivity`
  requires only a config refresh, no redeployment.
