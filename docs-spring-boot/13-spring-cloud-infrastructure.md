# 13 — Spring Cloud Infrastructure Deep-Dive

This document covers the three platform-level Spring Cloud services that every
application service depends on: **Config Server**, **Eureka (Service Discovery)**,
and **Spring Cloud Gateway**. These run before the application services and are
treated as infrastructure, not features.

---

## 1. Spring Cloud Config Server

### What it does

Centralizes all `application.yml` configuration in a **Git repository**. Services
pull their config on startup and can refresh at runtime without redeployment.

### Bootstrap

```xml
<!-- config-server/pom.xml -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-config-server</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# config-server/src/main/resources/application.yml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/platform-config
          default-label: main
          search-paths:
            - services/{application}    # per-service subdirectories
          clone-on-start: true
```

### Config Git repo layout

```
platform-config/
├── application.yml              # shared defaults for all services
├── services/
│   ├── user-service/
│   │   ├── application.yml      # user-service defaults
│   │   └── application-prod.yml # prod overrides
│   ├── trip-service/
│   │   └── application.yml
│   ├── matching-service/
│   │   └── application.yml
│   └── pricing-service/
│       └── application.yml
```

### Client configuration in every service

```yaml
# Each service's bootstrap.yml (loaded before application.yml)
spring:
  application:
    name: user-service          # must match the config repo subdirectory
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: "optional:configserver:http://config-server:8888"
```

### Runtime config refresh

Properties annotated with `@RefreshScope` are re-injected after a refresh event:

```java
@ConfigurationProperties(prefix = "pricing.surge")
@RefreshScope
@Component
public class SurgeConfig {
    private double maxMultiplier = 3.0;
    private double sensitivity = 0.5;
}
```

**Manual refresh (single instance):**
```bash
curl -X POST http://pricing-service:8080/actuator/refresh
```

**Broadcast refresh via Spring Cloud Bus (Kafka-backed — all instances at once):**
```bash
curl -X POST http://config-server:8888/monitor   # triggers Bus RefreshRemoteApplicationEvent
```

```yaml
# Add to each service to enable Bus
spring:
  cloud:
    bus:
      enabled: true
    stream:
      bindings:
        springCloudBusInput:
          destination: spring-cloud-bus
        springCloudBusOutput:
          destination: spring-cloud-bus
```

### Secrets

Do **not** store secrets in the config Git repo. Use **HashiCorp Vault** as a
Config Server backend for secrets:

```yaml
# config-server application.yml — composite backend
spring:
  cloud:
    config:
      server:
        composite:
          - type: vault
            host: vault
            port: 8200
            kvVersion: 2
          - type: git
            uri: https://github.com/your-org/platform-config
```

Service properties like `spring.datasource.password` and `jwt.private-key` come
from Vault; all non-secret config comes from Git.

---

## 2. Spring Cloud Netflix Eureka Server

### What it does

Service registry: every Spring Boot service registers its hostname and port on
startup; peers discover each other by logical name via Spring Cloud LoadBalancer.

### Bootstrap

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# eureka-server/application.yml
server:
  port: 8761

eureka:
  instance:
    hostname: eureka-server
  client:
    register-with-eureka: false     # server doesn't register with itself
    fetch-registry: false
  server:
    enable-self-preservation: true  # protects against network partitions
```

### High-availability Eureka (peer replication)

Run two Eureka instances and have them replicate to each other:

```yaml
# eureka-server-1
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://eureka-server-2:8761/eureka/

# eureka-server-2
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server-1:8761/eureka/
```

### Client configuration (every application service)

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    metadata-map:
      version: ${project.version}
      zone: ${ZONE:default}
```

### Service-to-service calls via Spring Cloud LoadBalancer

REST calls between services use the logical service name with the `lb://` scheme:

```java
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced                         // enables client-side load balancing
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

@Service
public class TripHistoryClient {

    private final RestClient restClient;

    public Page<TripSummaryDto> getHistory(String userId, Pageable pageable) {
        return restClient.get()
            .uri("http://trip-service/trips/history?userId=" + userId)
            .retrieve()
            .body(new ParameterizedTypeReference<Page<TripSummaryDto>>() {});
    }
}
```

### Kubernetes alternative — Spring Cloud Kubernetes

In a pure-K8s environment, replace Eureka with **Spring Cloud Kubernetes Discovery**,
which uses K8s Service DNS natively:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-kubernetes-client-all</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        enabled: true
      config:
        enabled: true    # can also replace Config Server with K8s ConfigMaps
```

With this dependency, `lb://trip-service` resolves to the `trip-service` K8s
Service DNS entry — no Eureka pods needed.

---

## 3. Spring Cloud Gateway (deep-dive)

### What it does

The reactive API Gateway built on Spring WebFlux + Project Reactor. It handles TLS
termination (or sits behind Ingress), JWT validation, rate limiting, routing, and
request/response transformation.

### Bootstrap

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

### Full route configuration

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods: "*"
            allowedHeaders: "*"

      default-filters:
        - AddRequestHeader=X-Gateway-Version, 1.0
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin

      routes:
        # --- Public (no JWT required) ---
        - id: auth-register
          uri: lb://user-service
          predicates:
            - Path=/auth/register, /auth/login
          # No AuthJwtFilter here

        # --- Protected REST routes ---
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/auth/refresh, /users/**
          filters:
            - AuthJwtFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
                key-resolver: "#{@userKeyResolver}"

        - id: trip-service
          uri: lb://trip-service
          predicates:
            - Path=/trips/**
          filters:
            - AuthJwtFilter
            - name: CircuitBreaker
              args:
                name: trip-cb
                fallbackUri: forward:/fallback/trip
            - name: Retry
              args:
                retries: 2
                statuses: SERVICE_UNAVAILABLE

        - id: driver-service
          uri: lb://driver-service
          predicates:
            - Path=/drivers/**
          filters:
            - AuthJwtFilter

        - id: pricing-ops
          uri: lb://pricing-service
          predicates:
            - Path=/pricing/**
            - Header=X-Internal-Token, ${internal.token}  # ops endpoint, not public
```

### JWT validation filter

```java
@Component
public class AuthJwtFilter implements GatewayFilter, Ordered {

    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        return jwtDecoder.decode(token)
            .flatMap(jwt -> {
                ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id", jwt.getSubject())
                    .header("X-User-Role", jwt.getClaimAsString("role"))
                    .header("X-User-Scope", jwt.getClaimAsString("scope"))
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            })
            .onErrorResume(JwtException.class, e -> {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            });
    }

    @Override
    public int getOrder() { return -2; }
}
```

### Rate limiter with Redis

```java
@Configuration
public class GatewayConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Rate-limit by user id (from X-User-Id header set by JWT filter)
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null ? userId : "anonymous");
        };
    }
}
```

### Fallback controllers

```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/trip")
    public Map<String, String> tripFallback() {
        return Map.of(
            "error", "Trip service temporarily unavailable",
            "suggestion", "Please try again in a few seconds"
        );
    }

    @GetMapping("/pricing")
    public Map<String, Object> pricingFallback() {
        return Map.of(
            "error", "Pricing service temporarily unavailable",
            "surgeMultiplier", 1.0,     // base fare fallback
            "note", "Fare estimate based on base rates"
        );
    }
}
```

### CORS and security headers

```java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)   // stateless JWT
            .authorizeExchange(auth -> auth
                .pathMatchers("/auth/register", "/auth/login").permitAll()
                .pathMatchers("/actuator/health/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
            )
            .build();
    }
}
```

---

## 4. Summary — startup order

```
1. Kafka + Redis + PostgreSQL + MongoDB + Cassandra   (infra, not Spring Boot)
2. Config Server                                       (spring-boot app, port 8888)
3. Eureka Server                                       (spring-boot app, port 8761)
4. API Gateway                                         (spring-boot app, port 8080)
5. WebSocket Gateway                                   (spring-boot app, port 8085)
6. Application services (any order, they retry on Eureka/Config until ready)
   user-service | driver-service | matching-service
   pricing-service | trip-service | notification-service
```

All application services declare `spring.cloud.config.fail-fast=true` and
`eureka.client.initial-instance-info-replication-interval-seconds=5` so they retry
gracefully during the boot sequence rather than crashing immediately.
