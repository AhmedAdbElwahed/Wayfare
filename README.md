# Wayfare ( This Project to learn about event-driven and micro services grpc and OPTL )  

A ride-hailing backend built as a multi-module Spring Boot 4 / Spring Cloud project.
Phase 0 (foundations) and Phase 1 (identity and accounts) are in place, plus
the trip service and a first version of matching; pricing is not started. The target design is in
[docs-spring-boot](docs-spring-boot/00-index.md), and the roadmap is
[12-implementation-roadmap.md](docs-spring-boot/12-implementation-roadmap.md).

## Services

| Module | Port | Role |
|---|---|---|
| [wayfare-config-server](wayfare-config-server) | 8888 | Spring Cloud Config; config lives in [`config-repo/`](wayfare-config-server/src/main/resources/config-repo) |
| [wayfare-server-discovery](wayfare-server-discovery) | 8761 | Eureka registry |
| [wayfare-api-gateway](wayfare-api-gateway) | 8080 | JWT validation, routing, Redis rate limiting, Swagger UI |
| [wayfare-auth-service](wayfare-auth-service) | 8081 | Register/login, RS256 JWTs, JWKS; publishes `AccountRegistered` |
| [wayfare-rider-service](wayfare-rider-service) | 8082 | Rider profile, payment methods, trip-history index, photo; gRPC rider lookup on 9082 (internal) |
| [wayfare-driver-service](wayfare-driver-service) | 8083 | Driver onboarding, vehicle, documents, approval, expiry job; gRPC driver lookup on 9083 (internal) |
| [wayfare-trip-service](wayfare-trip-service) | 8084 | Trips: Postgres write model + transactional outbox, Mongo read model |
| [wayfare-matching-service](wayfare-matching-service) | 8086 | No REST API. Reads `RideRequested`, finds nearby drivers in Redis, checks them over driver gRPC, reserves one, publishes the match |

Shared libraries (plain jars, no ports):

| Module | Provides |
|---|---|
| [wayfare-common-grpc](wayfare-common-grpc) | gRPC contracts (`.proto`) and generated stubs for the rider and driver APIs |
| [wayfare-common-events](wayfare-common-events) | The Kafka event contract: `.proto` schemas, the classes generated from them, and the Confluent Protobuf serializer |
| [wayfare-common-security](wayfare-common-security) | `JwtRoleConverter`: maps the JWT `role` claim to `ROLE_*` |
| [wayfare-common-storage](wayfare-common-storage) | MinIO helper: presigned URLs and upload validation |
| [wayfare-common-tracing](wayfare-common-tracing) | OpenTelemetry setup: traces, metrics and logs over OTLP |

Infrastructure (Docker Compose): Postgres (one database per service), Mongo, Redis,
Kafka (KRaft, single broker), Confluent Schema Registry, MinIO, and an
observability stack of Tempo, Loki, Mimir and Grafana.

## Requirements

- Java 17
- Docker with Compose v2
- `openssl` (once, to generate the JWT keys)
- Network access on the first build: Maven downloads `protoc` to compile the
  event schemas

The Maven wrapper (`./mvnw`) is bundled.

## Quick start

1. Generate the JWT signing key pair. `keys/` is git-ignored and mounted
   read-only into auth-service.

   ```bash
   mkdir -p keys
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
   openssl rsa -pubout -in keys/private.pem -out keys/public.pem
   ```

2. Build the jars and start everything:

   ```bash
   make up
   ```

   To also create an admin account (needed to approve drivers), set both
   variables; the password must be at least 8 characters:

   ```bash
   ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='change-me-now' make up
   ```

3. (Optional) Give the matching service drivers to match. Nothing writes
   driver locations yet, so in the `dev` profile (the Compose default) matching
   seeds the drivers listed in `SEED_DRIVER_IDS` into Redis at start-up. They
   must be registered and approved by the admin, or matching skips them:

   ```bash
   SEED_DRIVER_IDS=<driver-uuid>,<driver-uuid> make rebuild svc=wayfare-matching-service
   ```

   They are placed 0.5 km apart, going north from `SEED_LAT`/`SEED_LNG`
   (default 30.0444, 31.2357). Request trips near that point.

4. Use it through the gateway on `http://localhost:8080`:

   ```bash
   curl -H 'Content-Type: application/json' \
        -d '{"email":"ann@example.com","password":"password1","role":"RIDER"}' \
        http://localhost:8080/auth/register

   curl -H 'Content-Type: application/json' \
        -d '{"email":"ann@example.com","password":"password1"}' \
        http://localhost:8080/auth/login          # -> {"token":"..."}

   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/riders/me
   ```

   Registering creates the rider's profile (or the driver's record) through a
   Kafka event, so it can take a moment to appear. `role` is `RIDER` or
   `DRIVER`; `ADMIN` cannot be self-registered.

   To delete your own account (soft-deletes it in auth; rider and driver data
   are erased through the `account.deleted` event):

   ```bash
   curl -H 'Content-Type: application/json' \
        -d '{"email":"ann@example.com","password":"password1"}' \
        http://localhost:8080/auth/delete-account
   ```

Swagger UI for the auth, rider, driver and trip APIs is at
`http://localhost:8080/swagger-ui.html`.

## Requesting a ride

A trip moves `PENDING → REQUESTED → MATCHED → ARRIVING → IN_PROGRESS →
COMPLETED`, or ends in `CANCELLED` or `NO_DRIVERS`. Confirming a trip publishes
`RideRequested`; the matching service answers on `match.events` and the trip
becomes `MATCHED` or `NO_DRIVERS`.

| Endpoint | Who | What |
|---|---|---|
| `POST /trips` `{"pickup":{"lat":..,"lng":..},"dropoff":{..}}` | rider | Create a trip (`PENDING`) with a locked fare |
| `POST /trips/{id}/confirm` | rider | Request a driver (`REQUESTED`) |
| `POST /trips/{id}/cancel` `{"reason":"..."}` (body optional) | rider or driver | Cancel |
| `POST /trips/{id}/arriving` | driver | Driver on the way |
| `POST /trips/{id}/start` | driver | Rider picked up |
| `POST /trips/{id}/complete` `{"distanceKm":..}` (body optional) | driver | Finish; without a distance the straight line is used |
| `GET /trips/{id}` | rider or driver of the trip | One trip (read model) |
| `GET /trips?page=0&size=20` | rider or driver | Your trips |

```bash
curl -H "Authorization: Bearer $RIDER_TOKEN" -H 'Content-Type: application/json' \
     -d '{"pickup":{"lat":30.0450,"lng":31.2357},"dropoff":{"lat":30.0600,"lng":31.2500}}' \
     http://localhost:8080/trips                      # -> {"id":"...", ...}

curl -X POST -H "Authorization: Bearer $RIDER_TOKEN" http://localhost:8080/trips/$TRIP_ID/confirm
curl -H "Authorization: Bearer $RIDER_TOKEN" http://localhost:8080/trips/$TRIP_ID   # MATCHED or NO_DRIVERS
```

To get a match, a driver must be approved by the admin
(`POST /drivers/admin/{driverId}/approve`) and seeded into matching (quick
start step 3), and the pickup must be within 8 km of them.

## Make targets

| Command | What it does |
|---|---|
| `make up` | Build every jar (tests skipped), then start the stack |
| `make down` | Stop the stack, keeping the database volume |
| `make rebuild svc=wayfare-driver-service` | Repackage and restart one service |
| `make restart svc=...` | Restart one service without rebuilding |
| `make logs [svc=...]` | Tail logs for everything, or one service |
| `make ps` | Container status and health |
| `make db` | `psql` shell on the dev database |
| `make minio` | MinIO console URL and credentials |
| `make grafana` | Grafana URL (traces, logs, metrics) |
| `make clean` | Stop the stack, **drop the Postgres and MinIO volumes**, `mvn clean` |

If a default host port is taken, override it: `DB_PORT=5433`, `MINIO_PORT=9100`,
`SCHEMA_REGISTRY_PORT=8087`, `GRAFANA_PORT=3001`.

### Ports

| Port | What |
|---|---|
| 8080 | API gateway |
| 8081 / 8082 / 8083 / 8084 | auth / rider / driver / trip |
| 8086 | Matching (actuator only) |
| 9082 / 9083 | rider / driver gRPC (Compose network only, not published) |
| 8761 | Eureka dashboard |
| 8888 | Config server |
| 8085 | Schema Registry |
| 5432 | Postgres |
| 6379 | Redis |
| 9000 / 9001 | MinIO API / console |
| 3000 | Grafana (anonymous admin, dev only) |
| 3100 / 3200 / 9009 | Loki / Tempo / Mimir |

## Configuration

Service configuration comes from the Config Server. To add config for a service,
put `<service-name>.yml` (and optionally `<service-name>-<profile>.yml`) in
[`config-repo/`](wayfare-config-server/src/main/resources/config-repo);
`application.yml` there applies to every service. Each value can be overridden
by an environment variable, with a localhost default so a service can also run
from an IDE.

| Variable | Used for |
|---|---|
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Creates the single ADMIN account at start-up (unset = none) |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | MinIO credentials (dev defaults `wayfare` / `wayfare-secret`) |
| `SCHEMA_REGISTRY_URL` | Schema Registry address |
| `SEED_DRIVER_IDS` | Matching, `dev` profile: approved driver UUIDs (comma-separated) to seed into the Redis geo set |
| `SEED_LAT`, `SEED_LNG` | Matching, `dev` profile: where the seeded drivers are placed |
| `MATCHING_PROFILES` | Matching's Spring profiles in Compose (default `dev`; set it empty to turn the seeder off) |
| `DRIVER_GRPC_ADDRESS` | Driver gRPC target for matching (default `static://localhost:9083`) |
| `OTLP_TRACING_ENDPOINT`, `OTLP_LOGS_ENDPOINT`, `OTLP_METRICS_ENDPOINT` | Where telemetry is exported |
| `TRACING_SAMPLING_PROBABILITY` | Trace sampling (1.0 is dev only) |

## Building and testing

```bash
./mvnw package -DskipTests                 # everything
./mvnw -pl wayfare-driver-service -am package   # one module; -am builds the shared jars it needs
./mvnw test                                # unit tests
```

Build a single module with `-am`, otherwise the shared `wayfare-common-*` jars
are not found. Integration tests (`*IT`, which use Testcontainers) are skipped
by a plain `mvn test`; run them with `-Dtest=...`, for example
`./mvnw -pl wayfare-rider-service -am test -Dtest=PaymentMethodDefaultIT -Dsurefire.failIfNoSpecifiedTests=false`
or `./mvnw -pl wayfare-matching-service -am test -Dtest=RedisMatchingIT -Dsurefire.failIfNoSpecifiedTests=false`.

If an IDE has built a module and you see "Unresolved compilation problem" at
runtime, run `./mvnw clean`.

## Events

Services talk over Kafka using Protobuf messages registered in the Schema
Registry:

| Topic | Event | Published by | Consumed by |
|---|---|---|---|
| `account.events` | `AccountRegisteredEvent` | auth | rider, driver |
| `account.deleted` | `AccountDeletedEvent` | auth | rider, driver |
| `driver.events` | `DriverStatusChangedEvent` | driver | (none yet) |
| `trip.events` | `TripEvent` (`RideRequested`, `TripCompleted`, ...) | trip (outbox) | trip (read model), matching |
| `match.events` | `MatchEvent` (`DriverMatched`, `NoDriversAvailable`) | matching | trip |

The schemas are in
[`wayfare-common-events/src/main/protobuf`](wayfare-common-events/src/main/protobuf).
Change them additively: add fields with new numbers, never reuse or renumber
one, and the registry rejects incompatible versions. Keep `confluent.version`
in [`wayfare-common-events/pom.xml`](wayfare-common-events/pom.xml) in step with
the `cp-schema-registry` image in `docker-compose.yml`.

## Observability

Every service exports traces, metrics and logs over OTLP to Tempo, Mimir and
Loki. Open Grafana (`make grafana`) and use Explore with the Tempo, Loki or
Mimir data source; a provisioned dashboard covers service performance and logs.
The stack is optional: services run without it.

## Known limitations

- Auth, rider and driver publish events after the database commit, with no
  outbox, so a broker outage right after a commit can lose an event. The trip
  service uses a transactional outbox.
- The gRPC ports have no authentication; they are reachable only on the
  Compose network.
- Rider PII (name, phone, email) is stored in plaintext.
- No ratings or rider trip details yet.
- Matching treats every request as one city with no ride type, and a reserved
  driver counts as matched (no offer/accept step yet). Driver locations come
  from the dev seeder only.
- No CI/CD pipeline.
- Dev defaults (MinIO credentials, anonymous Grafana admin, 100% trace
  sampling) are not safe for production.
