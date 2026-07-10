# 05 — Rider Service (Spring Boot)

## Responsibility

Owns **rider profile data**: profiles, payment method references, saved addresses,
ride-history index, and ratings the rider gives.

> Identity and credentials (registration, login, password hashing, JWT issuance,
> JWKS) are **not** owned here — they live in the shared `wayfare-auth-service`
> module, used by both riders and drivers. This service only *validates* tokens
> issued by auth-service (resource server) and stores profile data keyed by the
> account id from the JWT `sub` claim.

> Drivers are intentionally a **separate** Spring Boot service (different lifecycle,
> approval, documents) — see [06-driver-service.md](./06-driver-service.md). Both
> rider and driver services share the same auth-service for identity.

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
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <!-- JWT validation only — tokens are issued by wayfare-auth-service -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream</artifactId>
  </dependency>
  <!-- gRPC SERVER: Rider Service exposes internal RPCs that other services call -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-server</artifactId>
  </dependency>
</dependencies>
```

---

## Data model (Spring Data JPA / PostgreSQL)

There is **no local user/credentials table**. `userId` below is the `Account.id`
owned by `wayfare-auth-service` — the same UUID that appears as the JWT `sub`.
Rows here are cross-service references keyed by that id, not foreign keys into a
local table.

```java
@Entity @Table(name = "profiles")
public class Profile {
    @Id private UUID userId;          // = Account.id from wayfare-auth-service (JWT sub)
    private String name;
    private String photoUrl;
    private String locale;
    private UUID defaultPaymentId;
    private Instant createdAt;
}

@Entity @Table(name = "payment_methods")
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID userId;             // = Account.id — no local user table to join to
    private String providerToken;    // tokenized ref only — no raw card numbers
    private String brand;
    private String last4;
    private boolean isDefault;
}

@Entity @Table(name = "history_index")
public class HistoryIndex {
    @EmbeddedId private HistoryKey key;  // (userId, tripId)
    private Instant occurredAt;
    // Full trip detail is owned by the Trip service
}
```

**Database migrations:** Flyway in `src/main/resources/db/migration/`.

The first Flyway migration should create an empty `profiles` row per account (see
[Events](#events) below) rather than a `users` table — credentials and account
lifecycle (`email`, `passwordHash`, `status`) stay in auth-service's `accounts` table.

---

## Security — Spring Security (resource server only)

This service does **not** issue tokens. Login, registration, and JWT signing are
handled by the `wayfare-auth-service` module (`AuthService`/`JwtService` there).
Here, Spring Security only validates the `Authorization` header on incoming
requests.

```yaml
# Rider Service application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-service/auth/.well-known/jwks.json
```

The Spring Cloud Gateway also validates at the edge (caching the JWKS locally) so
downstream services can trust the forwarded `Authorization` header.

---

## REST API

`/auth/**` is routed to the `wayfare-auth-service` module, not this service — see
that module's `AuthController` for `register`/`login`.

```java
@RestController
@RequestMapping("/riders")
public class RiderController {

    @GetMapping("/me")
    public RiderDto getProfile(@AuthenticationPrincipal Jwt jwt) { ... }

    @PatchMapping("/me")
    public RiderDto updateProfile(@AuthenticationPrincipal Jwt jwt,
                                  @RequestBody UpdateProfileRequest req) { ... }

    @GetMapping("/me/trips")
    public Page<TripSummaryDto> getTripHistory(@AuthenticationPrincipal Jwt jwt,
                                               Pageable pageable) {
        // Proxy call to Trip Service read model via Feign/RestClient
        return tripClient.getHistoryForUser(jwt.getSubject(), pageable);
    }
}
```

### Spring Cloud Gateway routes

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: rider-service
          uri: lb://rider-service
          predicates:
            - Path=/riders/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
```

---

## Internal gRPC API

### Who is the server, who is the client?

The Rider Service **owns** rider profile and payment-method data, so it is the
**gRPC server**. Any other service (Trip, Payment, Driver, …) that needs profile
or payment-method info is a **gRPC client** that calls into this service — the
call always flows *toward the data owner*.

```
Trip / Payment / Driver service        Rider Service
   (gRPC CLIENT, @GrpcClient)  ──────▶  (gRPC SERVER, @GrpcService)
```

> Rule of thumb: whoever **implements** the `service {}` block from the `.proto`
> (`extends *ImplBase`) is the server; whoever **injects a stub** to invoke it is
> the client. The `.proto` contract below is shared by both sides (e.g. published as
> a small `*-grpc-api` artifact or a shared proto module).
>
> Credential/identity checks (does this account exist, is it active) are a
> separate concern owned by `wayfare-auth-service`, not this RPC.

### Server side — this service

```protobuf
// Shared contract (rider.proto) — compiled by both server and clients
service RiderService {
  rpc GetProfile (GetProfileRequest) returns (ProfileResponse);
  rpc ValidatePaymentMethod (ValidatePaymentMethodRequest) returns (ValidationResponse);
}
```

```java
@GrpcService  // registers this as a gRPC endpoint (SERVER)
public class RiderGrpcService extends RiderServiceGrpc.RiderServiceImplBase {

    private final ProfileRepository profileRepository;

    @Override
    public void getProfile(GetProfileRequest req, StreamObserver<ProfileResponse> obs) {
        profileRepository.findById(UUID.fromString(req.getUserId()))
            .map(ProfileMapper::toProto)
            .ifPresentOrElse(
                p -> { obs.onNext(p); obs.onCompleted(); },
                () -> obs.onError(Status.NOT_FOUND.asRuntimeException())
            );
    }
}
```

```yaml
# Rider Service application.yml — the gRPC server listens on its own port
grpc:
  server:
    port: 9090          # separate from the 8080 HTTP/REST port
```

### Client side — the calling services (for reference)

A service that calls the Rider Service adds a gRPC client starter and injects a
stub — it does **not** implement the service:

```java
// In e.g. Trip Service (gRPC CLIENT)
@GrpcClient("rider-service")
private RiderServiceGrpc.RiderServiceBlockingStub riderStub;

ProfileResponse profile = riderStub.getProfile(
    GetProfileRequest.newBuilder().setUserId(riderId).build());
```

```yaml
# Calling service application.yml — where to reach the Rider Service gRPC server
grpc:
  client:
    rider-service:
      address: 'discovery:///rider-service'   # resolve via Eureka
      negotiation-type: plaintext
```

---

## Events

**Produces (Spring Cloud Stream):**
- `PaymentMethodAdded` → `user.events` topic
- `RiderRated` → `user.events` topic

**Consumes:**
- `AccountRegistered` (from `wayfare-auth-service`) → creates the initial (empty)
  `profiles` row for the new account.
- `TripCompleted` → updates `history_index` and optionally triggers a rating prompt
  via Notification Service.

```java
@Bean
public Consumer<Message<AccountRegisteredEvent>> onAccountRegistered(ProfileRepository repo) {
    return message -> {
        AccountRegisteredEvent evt = message.getPayload();
        if (evt.getRole() == AccountRole.RIDER) {
            repo.save(new Profile(evt.getAccountId()));
        }
    };
}

@Bean
public Consumer<Message<TripCompletedEvent>> onTripCompleted(HistoryIndexRepository repo) {
    return message -> {
        TripCompletedEvent evt = message.getPayload();
        repo.save(new HistoryIndex(evt.getRiderId(), evt.getTripId(), evt.getOccurredAt()));
    };
}
```

---

## Redis caching

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}

@Service
public class ProfileService {

    @Cacheable(value = "profiles", key = "#userId")
    public ProfileDto getProfile(UUID userId) { ... }

    @CacheEvict(value = "profiles", key = "#userId")
    public void updateProfile(UUID userId, UpdateProfileRequest req) { ... }
}
```

---

## Scaling & concerns

- **Depends on auth-service for every request** — the Gateway and this service
  cache the JWKS public key locally so JWT validation doesn't hit auth-service
  per request. Run auth-service highly available (≥2 replicas, readiness probe
  on `/actuator/health/readiness`).
- **PII compliance:** use Hibernate column encryption (`@Convert`) or application-
  level encryption for sensitive columns; support account deletion/export as
  `@Scheduled` jobs that anonymize data. Account deletion itself is coordinated
  with auth-service (which owns the `accounts` row).
- **Stateless pods:** Spring Boot sessions are stateless (JWT-based); scale
  horizontally behind Eureka. PostgreSQL read replicas serve read-heavy profile
  lookups; set `spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true`
  and route queries to the replica datasource with AbstractRoutingDataSource.
