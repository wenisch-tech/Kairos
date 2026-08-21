# Configuration: Database

This page covers database setup for Kairos.

## H2 (default)

H2 file mode works out of the box and is suitable for local and small setups.

```properties
spring.datasource.url=jdbc:h2:file:./kairos;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

!!! warning "One writer only"

    An H2 file database must be opened by exactly one process. Running two Kairos instances
    (or a rolling update that briefly runs two pods) against the same `kairos.mv.db` corrupts
    the file and produces `MVStoreException: Chunk <n> not found`. Keep `replicaCount: 1`, keep
    the `Recreate` update strategy that the chart applies when `persistence.enabled=true`, and
    leave `AUTO_SERVER=TRUE` out of containerized datasource URLs — it is meant for local
    development, where a second tool on the same machine connects to the running database.
    Use PostgreSQL when you need more than one instance.

The H2 web console is available at `http://localhost:8080/h2-console` when `spring.h2.console.enabled=true`.

## PostgreSQL

For production workloads, PostgreSQL is recommended.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kairos
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=kairos
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

Environment variable equivalent:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/kairos
SPRING_DATASOURCE_USERNAME=kairos
SPRING_DATASOURCE_PASSWORD=secret
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
```

When deploying with the Helm chart, set `env.SPRING_JPA_DATABASE_PLATFORM` to
`org.hibernate.dialect.PostgreSQLDialect` together with the PostgreSQL datasource
settings. The chart otherwise defaults this property to H2.

Example `docker-compose` with PostgreSQL:

```yaml
version: "3.9"
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kairos
      POSTGRES_USER: kairos
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  kairos:
    image: ghcr.io/wenisch-tech/kairos:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/kairos
      SPRING_DATASOURCE_USERNAME: kairos
      SPRING_DATASOURCE_PASSWORD: secret
      SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.PostgreSQLDialect
    depends_on:
      - db

volumes:
  pgdata:
```

## Flyway Migrations

Kairos uses Flyway for startup migrations.

- Existing databases without a Flyway history table are baselined automatically.
- Pending migrations are then applied in order.
- No manual SQL migration steps are required for normal upgrades.
