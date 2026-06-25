# 01 — Architecture Overview (Spring Boot & Spring Cloud)

## 1. System shape

The platform is a set of independently deployable **Spring Boot** services behind a
**Spring Cloud Gateway**, coordinated through synchronous calls (REST/gRPC) for
request/response work and an asynchronous event log (Kafka via **Spring Cloud
Stream**) for state propagation. A dedicated Spring WebSocket gateway handles all
persistent client connections (live location, trip updates).

```mermaid
flowchart TB
    subgraph Clients
        RA[Rider App]
        DA[Driver App]
    end

    subgraph Edge
        GW[Spring Cloud Gateway<br/>REST/JWT/Rate-limit]
        WS[WebSocket Gateway<br/>Spring WebSocket + STOMP]
    end

    subgraph SpringCloudInfra
        EUREKA[Eureka Server<br/>Service Discovery]
        CONFIG[Config Server<br/>Spring Cloud Config]
    end

    subgraph Services
        US[User Service<br/>Spring Boot]
        DS[Driver Service<br/>Spring Boot]
        MS[Matching Service<br/>Spring Boot]
        PS[Pricing Service<br/>Spring Boot]
        TS[Trip Service<br/>Spring Boot]
        NS[Notification Service<br/>Spring Boot]
    end

    subgraph Infra
        K[(Kafka<br/>Spring Cloud Stream)]
        R[(Redis<br/>Spring Data Redis)]
    end

    RA -->|REST| GW
    DA -->|REST| GW
    RA <-->|WSS/STOMP| WS
    DA <-->|WSS/STOMP| WS

    GW --> US
    GW --> DS
    GW --> TS
    GW --> PS

    WS --> DS
    WS --> R

    MS --> R
    DS --> R
    PS --> R

    US <--> K
    DS <--> K
    MS <--> K
    PS <--> K
    TS <--> K
    NS <--> K
    WS <--> K

    EUREKA -.->|registers| US
    EUREKA -.->|registers| DS
    EUREKA -.->|registers| MS
    EUREKA -.->|registers| PS
    EUREKA -.->|registers| TS
    EUREKA -.->|registers| NS
    CONFIG -.->|config| US
    CONFIG -.->|config| DS
```

---

## 2. Spring Cloud infrastructure services

Before the application services, two Spring Cloud infrastructure services must be
running. Every Spring Boot service is a client of both.

### Spring Cloud Config Server
Centralized configuration source. All `application.yml` files live in a Git
repository; services pull their config on startup. Config changes are pushed to
running instances via Spring Cloud Bus (Kafka-backed) or individual
`/actuator/refresh` calls.

```yaml
# Each service's bootstrap.yml
spring:
  config:
    import: "configserver:http://config-server:8888"
  application:
    name: user-service
```

### Spring Cloud Netflix Eureka Server
Service registry for peer-to-peer discovery. Every service registers itself and
discovers peers by logical name (e.g. `http://matching-service/...`). Spring Cloud
LoadBalancer resolves the name to an actual host.

```yaml
# Each service's application.yml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

> In Kubernetes, you can replace Eureka with **Spring Cloud Kubernetes** discovery,
> which uses K8s Service DNS natively and drops the Eureka cluster.

---

## 3. Communication patterns

### a. REST (Spring Web MVC) — edge-facing
Used at the API Gateway boundary and for endpoints that external clients call.
Spring Cloud Gateway routes JWT-validated, rate-limited requests to downstream
services using Spring Cloud LoadBalancer for instance selection.

```yaml
# Spring Cloud Gateway route example
spring:
  cloud:
    gateway:
      routes:
        - id: trip-service
          uri: lb://trip-service
          predicates:
            - Path=/trips/**
          filters:
            - AuthJwtFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
```

### b. gRPC — service-to-service
Used when the caller needs an immediate answer to proceed. Each service exposes a
gRPC server via `grpc-spring-boot-starter` (net.devh). Stubs are generated from
`.proto` files shared in a common module.

```java
// gRPC server side (e.g. Pricing Service)
@GrpcService
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {
    @Override
    public void estimateFare(EstimateFareRequest req, StreamObserver<FareResponse> obs) {
        // ...
    }
}

// gRPC client side (e.g. Trip Service calls Pricing)
@GrpcClient("pricing-service")
private PricingServiceGrpc.PricingServiceBlockingStub pricingStub;
```

Resilience4j circuit breaker wraps every stub call:

```java
CircuitBreaker cb = circuitBreakerFactory.create("pricing");
FareResponse fare = cb.run(
    () -> pricingStub.estimateFare(request),
    ex -> fallbackFare()
);
```

### c. Spring Cloud Stream (Kafka) — async events
Used to propagate facts that already happened. Producers declare `Supplier<Message>`
beans; consumers declare `Consumer<Message>` beans. Spring Cloud Stream wires these
to Kafka topics automatically.

```java
// Producer (Trip Service publishes RideRequested)
@Bean
public Supplier<Message<RideRequestedEvent>> rideRequested() {
    return () -> MessageBuilder
        .withPayload(event)
        .setHeader(KafkaHeaders.KEY, event.getTripId())
        .build();
}

// Consumer (Matching Service reacts)
@Bean
public Consumer<Message<RideRequestedEvent>> onRideRequested() {
    return message -> matchingService.dispatch(message.getPayload());
}
```

```yaml
# application.yml bindings
spring:
  cloud:
    stream:
      bindings:
        rideRequested-out-0:
          destination: ride.requests
          contentType: application/json
        onRideRequested-in-0:
          destination: ride.requests
          group: matching-service
```

### d. Spring WebSocket + STOMP — real-time push
Used for continuous, low-latency flows to connected clients. The dedicated WebSocket
Gateway service uses Spring's `WebSocketMessageBrokerConfigurer` with STOMP over
WebSocket, backed by a Redis Pub/Sub message relay for horizontal scaling.

See [03-realtime-websockets.md](./03-realtime-websockets.md) for the full design.

---

## 4. End-to-end flow: requesting a ride

```mermaid
sequenceDiagram
    participant Rider
    participant GW as Spring Cloud Gateway
    participant Trip as Trip Service
    participant Pricing as Pricing Service (gRPC)
    participant Redis
    participant Kafka as Kafka (Spring Cloud Stream)
    participant Matching as Matching Service
    participant Notif as Notification Service
    participant WS as WebSocket Gateway

    Rider->>GW: POST /trips (pickup, dropoff)
    GW->>Trip: route via LoadBalancer (JWT validated)
    Trip->>Pricing: gRPC EstimateFare(route, zone)
    Pricing->>Redis: GeoOperations / ValueOperations surge(zone)
    Pricing-->>Trip: fare + multiplier
    Trip-->>Rider: estimate (await confirm)
    Rider->>Trip: POST /trips/{id}/confirm
    Trip->>Kafka: publish RideRequested (via StreamBridge)
    Matching->>Kafka: consume RideRequested (@StreamListener)
    Matching->>Redis: GEOSEARCH nearby drivers (GeoOperations.radius)
    Matching->>Kafka: publish DriverMatched
    Trip->>Kafka: consume DriverMatched (update state + outbox)
    Notif->>Kafka: consume DriverMatched
    Notif->>WS: push via Redis Pub/Sub channel
    WS-->>Rider: STOMP frame — driver assigned + ETA
    WS-->>Driver: STOMP frame — new trip offer
```

---

## 5. Why these boundaries (unchanged reasoning, Spring mapping)

| Service | Isolation reason | Spring boundary |
|---------|-----------------|-----------------|
| **User vs Driver** | Different identity flows and lifecycles | Separate Spring Boot apps, separate DBs, separate JWT scopes |
| **Matching** | Most latency-sensitive, algorithmic volatility | No JPA on hot path — pure `RedisTemplate` calls |
| **Pricing** | Surge logic changes independently, must be A/B-testable | Isolated Spring Boot app; feature flags via Config Server |
| **Trip** | Transactional heart with CQRS | Spring Data JPA write side + Spring Data MongoDB read side |
| **Notification** | Pure side-effect, must never block core flows | Spring Cloud Stream consumer only; no synchronous API |

---

## 6. Cross-cutting concerns (Spring implementations)

| Concern | Implementation |
|---------|---------------|
| **AuthN at gateway** | `spring-cloud-gateway` + custom `GlobalFilter` validating RS256 JWT via Spring Security OAuth2 Resource Server |
| **AuthZ in services** | `spring-boot-starter-oauth2-resource-server`; method-level `@PreAuthorize` |
| **Service-to-service auth** | mTLS configured in Spring Boot via `server.ssl.*` + client keystore |
| **Idempotency** | Idempotency key in HTTP header; `@Transactional` + unique constraint on `idempotency_key` column |
| **Distributed tracing** | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin`; `trip_id` propagated as Baggage |
| **Structured logging** | Logback JSON encoder; MDC populated with `traceId`, `spanId`, `tripId` |
| **Config refresh** | Spring Cloud Bus over Kafka; `@RefreshScope` on beans that read surge caps or fare rules |
| **Health & readiness** | Spring Boot Actuator `/actuator/health` with liveness + readiness probes for Kubernetes |
