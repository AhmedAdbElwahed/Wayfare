# Wayfare

A multi-module Spring Boot / Spring Cloud project.

## Modules

- [wayfare-config-server](wayfare-config-server) — Spring Cloud Config Server. Serves centralized configuration from a native (classpath) config repo at [wayfare-config-server/src/main/resources/config-repo](wayfare-config-server/src/main/resources/config-repo), on port `8888`.
- [wayfare-server-discovery](wayfare-server-discovery) — Spring Cloud Netflix Eureka Server. Service registry for the other modules, on port `8761`. Fetches its config from the Config Server.

## Requirements

- Java 17
- Maven (or the bundled `mvnw` wrapper)

## Running a service

Use the Maven wrapper from the repo root, targeting the module with `-pl`. Start the Config Server first, since other services fetch their configuration from it on startup:

```bash
./mvnw -pl wayfare-config-server spring-boot:run
./mvnw -pl wayfare-server-discovery spring-boot:run
```

### Config Server

- Runs on port `8888`.
- Active profile: `native`, serving config from `classpath:/config-repo`.
- Exposed actuator endpoints: `health`, `info`, `refresh`.

Example: fetch the shared config for `wayfare-user-service` (dev profile):

```bash
curl http://localhost:8888/wayfare-user-service/dev
```

To add config for a new service, drop a `<service-name>.yml` (and optional `<service-name>-<profile>.yml`) file into [wayfare-config-server/src/main/resources/config-repo](wayfare-config-server/src/main/resources/config-repo).

### Server Discovery (Eureka)

- Runs on port `8761`.
- Eureka dashboard: `http://localhost:8761`.
- Fetches its configuration from the Config Server at startup (`wayfare-server-discovery.yml`).
- `register-with-eureka` / `fetch-registry` are disabled for itself, since it is the registry.
