# 06 — Driver Service (Spring Boot)

## Responsibility

Owns **driver identity, vehicles, compliance documents, availability status**, and is
the **entry point for driver location updates**. It manages the driver lifecycle
(onboarding → approval → active → suspended) and keeps the live geo-index of who is
available and where.

---

## Spring Boot dependencies

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
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
</dependencies>
```

---

## Data model

### PostgreSQL — Spring Data JPA (durable profile/compliance data)

```java
@Entity @Table(name = "drivers")
public class Driver {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(unique = true) private String phone;
    private String email;
    @Enumerated(EnumType.STRING) private DriverStatus status;
    private BigDecimal ratingAvg;
    private Instant approvedAt;
}

@Entity @Table(name = "vehicles")
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne @JoinColumn(name = "driver_id") private Driver driver;
    private String make;
    private String model;
    private String plate;
    private String color;
    private int capacity;
    @Enumerated(EnumType.STRING) private RideType type;
}

@Entity @Table(name = "documents")
public class DriverDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne @JoinColumn(name = "driver_id") private Driver driver;
    @Enumerated(EnumType.STRING) private DocumentKind kind;
    private String url;
    @Enumerated(EnumType.STRING) private DocumentStatus status;
    private Instant expiresAt;
}
```

### Redis (live, ephemeral) — Spring Data Redis

```
Key                      Spring Data Redis API      Value / TTL
drivers:geo:{city}       GeoOperations              GEOADD position for radius search
driver:loc:{id}          ValueOperations + TTL 30s  last full GPS payload (heading, speed)
driver:status:{id}       ValueOperations + TTL 60s  online | on_trip | offline
```

```java
@Service
public class DriverPresenceService {

    private final RedisTemplate<String, String> redisTemplate;

    public void setOnline(String driverId, String city) {
        redisTemplate.opsForValue().set(
            "driver:status:" + driverId, "online", Duration.ofSeconds(60)
        );
        // Position will be populated by the first GPS frame
    }

    public void setOffline(String driverId, String city) {
        redisTemplate.delete("driver:status:" + driverId);
        redisTemplate.opsForGeo().remove("drivers:geo:" + city, driverId);
    }

    public String getStatus(String driverId) {
        return redisTemplate.opsForValue().get("driver:status:" + driverId);
    }
}
```

---

## REST API

```java
@RestController
@RequestMapping("/drivers")
public class DriverController {

    @PostMapping("/register")
    public ResponseEntity<DriverDto> register(@RequestBody @Valid RegisterDriverRequest req) { ... }

    @PostMapping("/me/documents")
    public ResponseEntity<DocumentDto> uploadDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DocumentUploadRequest req) { ... }

    @PatchMapping("/me/vehicle")
    public ResponseEntity<VehicleDto> updateVehicle(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid VehicleRequest req) { ... }

    @PostMapping("/me/online")
    @Transactional
    public ResponseEntity<Void> goOnline(@AuthenticationPrincipal Jwt jwt) {
        String driverId = jwt.getSubject();
        driverService.setOnline(driverId);
        streamBridge.send("driver.events-out-0",
            DriverOnlineEvent.of(driverId, Instant.now()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/offline")
    public ResponseEntity<Void> goOffline(@AuthenticationPrincipal Jwt jwt) { ... }
}
```

---

## Internal gRPC API

Used by the Matching Service to reserve drivers and flip status atomically:

```protobuf
service DriverService {
  rpc GetDriver (GetDriverRequest) returns (DriverResponse);
  rpc ReserveDriver (ReserveDriverRequest) returns (ReserveDriverResponse);
  rpc SetDriverStatus (SetDriverStatusRequest) returns (google.protobuf.Empty);
}
```

```java
@GrpcService
public class DriverGrpcService extends DriverServiceGrpc.DriverServiceImplBase {

    private final DriverRepository driverRepo;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void reserveDriver(ReserveDriverRequest req,
                              StreamObserver<ReserveDriverResponse> obs) {
        // Atomic SET NX — prevents double-booking
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
            "match:reserve:" + req.getDriverId(),
            req.getTripId(),
            Duration.ofSeconds(req.getTtlSeconds())
        );
        obs.onNext(ReserveDriverResponse.newBuilder()
            .setSuccess(Boolean.TRUE.equals(reserved))
            .build());
        obs.onCompleted();
    }
}
```

---

## Events

**Produces (Spring Cloud Stream → `driver.events`):**
- `DriverOnline` / `DriverOffline`
- `DriverApproved`
- `DriverLocationSnapshot` (sampled 1-in-N, for analytics heatmaps)

**Consumes:**

```java
@Configuration
public class DriverEventConsumers {

    @Bean
    public Consumer<Message<DriverMatchedEvent>> onDriverMatched(
            DriverPresenceService presenceService) {
        return message -> {
            DriverMatchedEvent evt = message.getPayload();
            // Flip status to on_trip in Redis
            redisTemplate.opsForValue().set(
                "driver:status:" + evt.getDriverId(),
                "on_trip",
                Duration.ofHours(4)
            );
        };
    }

    @Bean
    public Consumer<Message<TripCompletedEvent>> onTripCompleted(
            DriverPresenceService presenceService) {
        return message -> {
            TripCompletedEvent evt = message.getPayload();
            // Return driver to online status
            presenceService.setOnline(evt.getDriverId(), evt.getCity());
        };
    }
}
```

---

## Document expiry job

```java
@Component
public class DocumentExpiryJob {

    private final DriverRepository driverRepo;
    private final DriverDocumentRepository docRepo;
    private final StreamBridge streamBridge;

    @Scheduled(cron = "0 0 2 * * *")   // 2 AM daily
    public void checkExpiredDocuments() {
        List<DriverDocument> expired = docRepo.findByExpiresAtBeforeAndStatus(
            Instant.now(), DocumentStatus.VALID
        );
        for (DriverDocument doc : expired) {
            doc.setStatus(DocumentStatus.EXPIRED);
            docRepo.save(doc);
            // Suspend driver if license or insurance expired
            if (doc.isCritical()) {
                Driver driver = doc.getDriver();
                driver.setStatus(DriverStatus.SUSPENDED);
                driverRepo.save(driver);
                // Remove from geo-index
                redisTemplate.opsForGeo()
                    .remove("drivers:geo:" + driver.getCity(), driver.getId().toString());
                streamBridge.send("driver.events-out-0",
                    DriverSuspendedEvent.of(driver.getId(), "document_expired"));
            }
        }
    }
}
```

---

## Scaling & concerns

- **Location write path is the bottleneck** — the GPS path in the WS Gateway calls
  `GeoOperations.add()` directly; it never goes through JPA or HTTP to this service.
  This service owns the PostgreSQL profile; the WS Gateway owns the hot Redis writes.
- **Reservation atomicity:** `ReserveDriver` gRPC uses `setIfAbsent` (Redis SET NX)
  so a driver can't be double-matched even under concurrent Matching pods.
- **Redis TTLs** on `driver:status:{id}` (60s) and `driver:loc:{id}` (30s) auto-
  expire stale state if the driver's app crashes or loses connectivity.
- **Partition geo-index by city:** `drivers:geo:{city}` keeps each sorted set
  manageable; run Redis Cluster for large fleets.
- Stateless Spring Boot pods registered with Eureka; scale horizontally. Matching
  consumers are partitioned by `driver_id` for ordered processing per driver.
