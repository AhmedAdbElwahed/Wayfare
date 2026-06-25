# Wayfare

A multi-module Spring Boot / Spring Cloud project.

## Modules

- [wayfare-config-server](wayfare-config-server) — Spring Cloud Config Server. Serves centralized configuration from a native (classpath) config repo at [wayfare-config-server/src/main/resources/config-repo](wayfare-config-server/src/main/resources/config-repo), on port `8888`.

## Requirements

- Java 17
- Maven (or the bundled `mvnw` wrapper)

## Running a service

Use the Maven wrapper from the repo root, targeting the module with `-pl`:

```bash
./mvnw -pl wayfare-config-server spring-boot:run
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
