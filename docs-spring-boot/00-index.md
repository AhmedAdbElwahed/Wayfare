# Ride-Hailing Platform — Spring Boot & Spring Cloud Implementation Plan (Index)

A microservices, event-driven implementation plan for a ride-hailing platform
(Uber / Bolt / Careem style) built entirely on the **Spring ecosystem**.
This index is the entry point; each module and cross-cutting concern has its own
detailed document.

---

## How to read this plan

1. Start with the **Architecture Overview** to understand the system shape and how
   Spring Cloud components fit together.
2. Read the **Spring Cloud Infrastructure** doc to understand Config Server, Eureka,
   and the Gateway before diving into individual services.
3. Read the **Database Strategy** to see every datastore decision and the Spring Data
   module that backs it.
4. Read the **cross-cutting concept** docs (Real-time/WebSockets, CQRS/Event-Driven).
5. Then read each **service** doc — each is self-contained: responsibilities, data
   model, API, events, Spring Boot specifics, and scaling notes.
6. Finish with **Deployment & Infrastructure** and the **Implementation Roadmap**.

---

## Document map

| # | Document | What it covers |
|---|----------|----------------|
| 00 | [Index](./00-index.md) | This file |
| 01 | [Architecture Overview](./01-architecture-overview.md) | System topology, Spring Cloud components, data flow |
| 02 | [Database Strategy](./02-database-strategy.md) | Per-service datastore + Spring Data module choice |
| 03 | [Real-time & WebSockets](./03-realtime-websockets.md) | Spring WebSocket/STOMP, Redis Pub/Sub, location ingestion |
| 04 | [CQRS & Event-Driven Design](./04-cqrs-event-driven.md) | Spring Cloud Stream, Kafka, CQRS in Trip |
| 05 | [User Service](./05-user-service.md) | Rider accounts, Spring Security JWT, payment history |
| 06 | [Driver Service](./06-driver-service.md) | Driver accounts, vehicles, availability, location ingest |
| 07 | [Matching Service](./07-matching-service.md) | Rider↔driver matching, Spring Data Redis geo |
| 08 | [Pricing Service](./08-pricing-service.md) | Fare calculation, surge / dynamic pricing |
| 09 | [Trip Service](./09-trip-service.md) | Trip lifecycle, CQRS with Spring + outbox pattern |
| 10 | [Notification Service](./10-notification-service.md) | Push / SMS / email / WebSocket fan-out |
| 11 | [Deployment & Infrastructure](./11-deployment-infrastructure.md) | Docker, Kubernetes, Actuator, Micrometer, CI/CD |
| 12 | [Implementation Roadmap](./12-implementation-roadmap.md) | Phased delivery plan |
| 13 | [Spring Cloud Infrastructure](./13-spring-cloud-infrastructure.md) | Config Server, Eureka, Gateway, Circuit Breaker deep-dive |

---

## Service overview

| Service | Core responsibility | Sync API | Primary datastore | Spring Data module |
|---------|--------------------|----------|-------------------|--------------------|
| **User** | Rider identity, profile, payment methods, ride history | REST (Spring MVC) + gRPC | PostgreSQL | Spring Data JPA |
| **Driver** | Driver identity, vehicle/docs, availability, location | REST + WebSocket | PostgreSQL + Redis (geo) | Spring Data JPA + Spring Data Redis |
| **Matching** | Find & assign the best driver to a request | Event-driven + gRPC | Redis (geo) | Spring Data Redis |
| **Pricing** | Base fare + dynamic surge multipliers | gRPC | Redis + PostgreSQL/TimescaleDB | Spring Data Redis + Spring Data JPA |
| **Trip** | Authoritative trip lifecycle & state (CQRS) | REST (Spring MVC) | PostgreSQL (write) + MongoDB (read) | Spring Data JPA + Spring Data MongoDB |
| **Notification** | Deliver messages across channels | Async only | Cassandra | Spring Data Cassandra |

> Full reasoning for every datastore is in [02-database-strategy.md](./02-database-strategy.md).

---

## Tech stack at a glance

- **Language / runtime:** Java 21 (LTS) with Spring Boot 3.x on all services.
- **Sync communication:** REST via Spring Web MVC at the edge; gRPC
  (`grpc-spring-boot-starter` by net.devh) for service-to-service calls.
- **Async backbone:** Apache Kafka via **Spring Cloud Stream** (Kafka binder).
  Event schemas validated by Confluent Schema Registry (Avro or JSON Schema).
- **Cache / geo / real-time state:** Redis via **Spring Data Redis** (Lettuce driver).
  Geospatial ops use `RedisTemplate` with `GeoOperations`.
- **API Gateway:** **Spring Cloud Gateway** (reactive, replaces Kong/Envoy at the
  application layer; Kong/Envoy can still sit in front for TLS termination).
- **WebSocket layer:** Spring WebSocket + STOMP broker relay backed by Redis Pub/Sub;
  dedicated stateless gateway service.
- **Service discovery:** **Spring Cloud Netflix Eureka** (or Spring Cloud Kubernetes
  for in-cluster DNS-based discovery).
- **Centralized config:** **Spring Cloud Config Server** backed by a Git repository.
- **Circuit breaker / resilience:** **Spring Cloud Circuit Breaker** with Resilience4j
  (`spring-cloud-starter-circuitbreaker-resilience4j`).
- **Distributed tracing:** Micrometer Tracing + OpenTelemetry bridge → Zipkin/Tempo;
  auto-instrumented with `spring-boot-starter-actuator`.
- **Metrics:** Micrometer → Prometheus; dashboards in Grafana.
- **Security:** Spring Security + Spring Security OAuth2 Resource Server (JWT).
- **Database migrations:** Flyway (preferred) or Liquibase, Spring Boot auto-run.
- **Containerization:** Docker via `spring-boot-maven-plugin` (Buildpacks / Jib);
  Kubernetes for orchestration.
- **Observability:** Spring Boot Actuator endpoints, Micrometer metrics, structured
  logs (Logback JSON encoder → ELK/Loki).

---

## Guiding principles (Spring-specific additions)

1. **One service owns its data.** No service reaches into another's database.
   Data is shared only through Spring MVC/gRPC APIs or Spring Cloud Stream events.
2. **Hot path stays in memory.** High-frequency writes (driver GPS) use
   `GeoOperations.add()` on Redis — never touch a JPA-managed entity on the hot path.
3. **The source of truth is durable; read models are disposable.** Spring Cloud
   Stream consumers rebuild read models by replaying Kafka topics. A `@KafkaListener`
   projector writes to MongoDB; drop the collection and replay to rebuild.
4. **Externalize all config.** Every `application.yml` property that varies by
   environment lives in the Spring Cloud Config Server git repo. Services fetch it
   on startup and refresh via `/actuator/refresh` or Spring Cloud Bus.
5. **Fail soft with Resilience4j.** Every cross-service gRPC/REST call is wrapped in
   a `CircuitBreaker` and a `TimeLimiter`. Fallback methods return sensible defaults
   (cached surge, base fare) so a degraded service never blocks a trip.
