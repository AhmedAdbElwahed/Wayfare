# 05 — User Service (Spring Boot)

## Responsibility

Owns **rider identity and everything rider-account-related**: registration, login,
profiles, payment method references, saved addresses, ride-history index, and ratings
the rider gives. It is the identity authority that issues JWT access tokens for riders
using **Spring Security**.

> Drivers are intentionally a **separate** Spring Boot service (different lifecycle,
> approval, documents) — see [06-driver-service.md](./06-driver-service.md).

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
  <!-- JWT issuer -->
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
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
  <!-- gRPC server for internal calls -->
  <dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-spring-boot-starter</artifactId>
    <version>3.1.0</version>
  </dependency>
</dependencies>
```

---

## Data model (Spring Data JPA / PostgreSQL)

```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true) private String phone;
    @Column(unique = true) private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING) private UserStatus status;
    private Instant createdAt;
}

@Entity @Table(name = "profiles")
public class Profile {
    @Id private UUID userId;          // same PK as User (1-to-1)
    @MapsId @OneToOne private User user;
    private String name;
    private String photoUrl;
    private String locale;
    private UUID defaultPaymentId;
}

@Entity @Table(name = "payment_methods")
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne @JoinColumn(name = "user_id") private User user;
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

---

## Security — Spring Security + JWT

### Token issuance (login)

The User Service acts as the **JWT issuer**. On successful login it signs a token
with an RSA private key using `spring-security-oauth2-jose`:

```java
@Service
public class AuthService {

    private final RSAKey rsaKey;    // injected from Config Server / Vault secret

    public String issueToken(User user) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole())
            .claim("scope", "rider")
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plus(Duration.ofHours(1))))
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claims
        );
        jwt.sign(new RSASSASigner(rsaKey.toPrivateKey()));
        return jwt.serialize();
    }
}
```

### Token validation in other services

Other services validate the token using the public key exposed by User Service at
`/oauth2/jwks` (Spring Authorization Server endpoint or a custom one):

```yaml
# Other services' application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://user-service/oauth2/jwks
```

The Spring Cloud Gateway also validates at the edge so downstream services can trust
the forwarded `Authorization` header.

---

## REST API

```java
@RestController
@RequestMapping
public class UserController {

    @PostMapping("/auth/register")
    public ResponseEntity<UserDto> register(@RequestBody @Valid RegisterRequest req) { ... }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest req) { ... }

    @PostMapping("/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest req) { ... }

    @GetMapping("/users/me")
    public UserDto getProfile(@AuthenticationPrincipal Jwt jwt) { ... }

    @PatchMapping("/users/me")
    public UserDto updateProfile(@AuthenticationPrincipal Jwt jwt,
                                 @RequestBody UpdateProfileRequest req) { ... }

    @GetMapping("/users/me/trips")
    public Page<TripSummaryDto> getTripHistory(@AuthenticationPrincipal Jwt jwt,
                                               Pageable pageable) {
        // Proxy call to Trip Service read model via Feign/RestClient
        return tripClient.getHistoryForUser(jwt.getSubject(), pageable);
    }
}
```

### Spring Cloud Gateway route for this service

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/auth/**, /users/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
```

---

## Internal gRPC API

Used by other services that need to validate a user or payment method without going
through the public REST layer:

```protobuf
service UserService {
  rpc GetUser (GetUserRequest) returns (UserResponse);
  rpc ValidatePaymentMethod (ValidatePaymentMethodRequest) returns (ValidationResponse);
}
```

```java
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUser(GetUserRequest req, StreamObserver<UserResponse> obs) {
        userRepository.findById(UUID.fromString(req.getUserId()))
            .map(UserMapper::toProto)
            .ifPresentOrElse(
                u -> { obs.onNext(u); obs.onCompleted(); },
                () -> obs.onError(Status.NOT_FOUND.asRuntimeException())
            );
    }
}
```

---

## Events

**Produces (Spring Cloud Stream):**
- `UserRegistered` → `user.events` topic
- `PaymentMethodAdded` → `user.events` topic
- `RiderRated` → `user.events` topic

**Consumes:**
- `TripCompleted` → updates `history_index` and optionally triggers a rating prompt
  via Notification Service.

```java
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

- **Auth is a dependency for everyone** — run it highly available (≥2 replicas,
  readiness probe on `/actuator/health/readiness`). Gateway caches the JWKS public
  key locally so JWT validation doesn't hit this service per request.
- **PII compliance:** use Hibernate column encryption (`@Convert`) or application-
  level encryption for sensitive columns; support account deletion/export as
  `@Scheduled` jobs that anonymize data.
- **Stateless pods:** Spring Boot sessions are stateless (JWT-based); scale
  horizontally behind Eureka. PostgreSQL read replicas serve read-heavy profile
  lookups; set `spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true`
  and route queries to the replica datasource with AbstractRoutingDataSource.
- **Password hashing:** BCrypt via Spring Security's `BCryptPasswordEncoder`
  (strength ≥ 12).
