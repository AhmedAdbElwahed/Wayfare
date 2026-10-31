# CLAUDE.md

Wayfare is a ride-hailing backend built as a learning project: event-driven microservices, gRPC and
OpenTelemetry. It is a multi-module Maven build on Spring Boot 4.1.0, Spring Cloud 2025.1.2, and Java 17.

## Where to read first

- `my-docs/README.md`: current status and an index of working notes (git-ignored, local only). Each
  service has a folder there with guides that describe the **real** code.
- `docs-spring-boot/`: the target design (`00-index.md`) and the roadmap (`12-implementation-roadmap.md`).
  It predates the code. Where it disagrees with `my-docs/` or the code, the code and `my-docs/` win.
- `README.md`: quick start, Make targets, ports, env vars.

## Modules

| Module | Port | Notes |
|---|---|---|
| `wayfare-config-server` | 8888 | Config for every service is in `src/main/resources/config-repo/` |
| `wayfare-server-discovery` | 8761 | Eureka |
| `wayfare-api-gateway` | 8080 | WebFlux gateway: JWT validation, Redis rate limiting. Routes `/auth`, `/riders`, `/drivers`, `/trips` |
| `wayfare-auth-service` | 8081 | Register/login, RS256 JWT + JWKS, admin bootstrap, account deletion |
| `wayfare-rider-service` | 8082 (gRPC 9082) | Profile, payment methods, history index, photo; Redis cache |
| `wayfare-driver-service` | 8083 (gRPC 9083) | Onboarding, vehicle, documents, approval, expiry job |
| `wayfare-trip-service` | 8084 | CQRS: Postgres write model + transactional outbox (`relay/`), Mongo read model (`projection/`) |
| `wayfare-matching-service` | 8086 | **Uncommitted.** `trip.events` → Redis geo search + driver gRPC check → `match.events`. No REST (web only for actuator). `dev` profile seeds drivers |
| `wayfare-common-events` | — | Kafka event contract: `.proto` in `src/main/protobuf/wayfare/event/` |
| `wayfare-common-grpc` | — | gRPC contracts: `src/main/proto/wayfare/{rider,driver}/v1/` |
| `wayfare-common-security` | — | `JwtRoleConverter` (`role` claim → `ROLE_*`) |
| `wayfare-common-storage` | — | MinIO: presigned URLs, upload validation, `deleteByPrefix` |
| `wayfare-common-tracing` | — | OTLP traces/metrics/logs, Logback → Loki |

Infra (`docker-compose.yml`): Postgres (one DB per service), Mongo, Redis, Kafka (KRaft), Confluent Schema
Registry (:8085), MinIO, and Tempo/Loki/Mimir/Grafana.

Kafka topics: `account.events`, `account.deleted`, `driver.events`, `trip.events`, `match.events`.

## Commands

```bash
make up                                   # package all jars (tests skipped) + docker compose up
make rebuild svc=wayfare-trip-service     # repackage and restart one service
make logs svc=...  |  make ps  |  make db  |  make grafana  |  make minio
make clean                                # DROPS Postgres + MinIO volumes, mvn clean

./mvnw -pl <module> -am package                    # always use -am; the common-* jars are needed
./mvnw -pl <module> -am test                       # unit tests (*Test)
./mvnw -pl <module> -am test -Dtest=FooIT -Dsurefire.failIfNoSpecifiedTests=false   # one IT
```

- `*IT` classes use Testcontainers and are **not** run by plain `mvn test` (no Failsafe). Run them by name.
- The JWT key pair must exist in `keys/` (git-ignored); see README quick start.
- The first build needs network access: Maven downloads `protoc`.
- Errors like "Unresolved compilation problem", `Invalid method Code length 0` or `class file version 69.0`
  mean an IDE compiled into `target/`. Run `./mvnw clean` on that module.

## Conventions

- Packages are `com.wayfare.{controller,service,domain,dto,repository,event,security,exception,config,grpc}`,
  per module.
- **Config** belongs in `config-repo/<service-name>.yml`, not the module's `application.yaml`, which only
  imports the config server. Every value is `${ENV_VAR:localhost-default}` so the services also run from an IDE.
- **Events** are Protobuf with Confluent serdes. Evolve them additively: add new field numbers, never reuse
  or renumber, and `reserved` removed fields. IDs are UUID strings, times are `google.protobuf.Timestamp`.
  Consumers must set `specific.protobuf.value.type`. Keep `confluent.version` in step with the
  `cp-schema-registry` image.
- **Spring Cloud Stream consumers** are `Consumer<…>` beans. The bean name must match the binding
  (`onX` ↔ `onX-in-0`), or the consumer silently never subscribes. Consumers must be idempotent.
- **Publishing**: auth/rider/driver publish after commit with no outbox, so a broker outage can lose an
  event. The trip service uses a transactional outbox, which is the pattern for new work.
- **Flyway** per service, with `ddl-auto: validate`: the entity and the migration must agree. Add new
  `V<n>__…` files and never edit applied ones.
- **Security**: services are OAuth2 resource servers that verify through the auth JWKS. Use
  `JwtRoleConverter.create()` for roles. The gRPC ports have no auth and are network-internal only.
- **Files** go client → MinIO through presigned URLs. Services validate the key prefix, size and type on
  register.

## Working notes

When you write a study or build guide, put it in `my-docs/<service-or-topic>/` and add it to
`my-docs/README.md`. Use relative links; repo files are `../../<module>/…` from a topic folder.
