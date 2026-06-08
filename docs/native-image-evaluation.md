# GraalVM Native Image Evaluation

Kairos continues to ship the JVM image by default. Native images are available as an experimental parallel path and are published under separate `native-distroless-*` tags.

The supported native-container story for this repository is `Dockerfile-native`. The earlier buildpack-native path is no longer the recommended direction because its default `/workspace` runtime layout conflicts with Kairos's default embedded H2 persistence path.

## Native Build Path

The Maven build now has an explicit `native` profile that:

- runs Spring AOT processing
- runs `native-maven-plugin` `compile-no-fork` during `package`
- keeps the required `--initialize-at-run-time=sun.security.util.Password$ConsoleHolder` build argument

Build a native executable directly only when your local JDK includes GraalVM `native-image`:

```bash
mvn -Pnative -DskipTests package
```

If your host JDK is a regular Temurin build, use the Docker-native path instead. That is the expected flow for Kairos development and CI.

Build the experimental native container image:

```bash
docker build -f Dockerfile-native -t kairos-native-local:test .
```

Validate the built image with the shared endpoint check:

```bash
bash scripts/check-container-endpoints.sh \
  kairos-native-local:test \
  18081 \
  native-local-endpoint-check.md \
  "Native Local Endpoint Check"
```

The native runtime image uses:

- `WORKDIR /app`
- a distroless non-root runtime base
- only the native executable plus emitted `.so` sidecar libraries
- `SPRING_DATASOURCE_URL=jdbc:h2:file:./data/kairos;AUTO_SERVER=TRUE`

That layout keeps Kairos's default embedded H2 files under `/app/data/kairos.*` without any manual runtime override.

## CI Flow

Native CI is intentionally parallel and non-blocking.

- `.github/workflows/_docker-native.yml` builds `Dockerfile-native` for `linux/amd64`
- the workflow runs `scripts/check-container-endpoints.sh` against the native image
- Trivy is run against the native image and its report is uploaded
- both endpoint and Trivy reports are uploaded as artifacts and posted to pull requests
- native images are pushed only on `main` and only under separate tags

Published native tags are:

- `native-distroless-<version>`
- `native-distroless-main`
- `native-distroless-latest`
- `native-distroless-sha-<shortsha>`

The existing JVM image and release path remain unchanged. Native publication does not gate the main release workflow.

## Local Validation Notes

The Docker-native path has been locally validated with:

- `docker build -f Dockerfile-native -t kairos-native-local:test .`
- `bash scripts/check-container-endpoints.sh kairos-native-local:test 18081 native-local-endpoint-check.md "Native Local Endpoint Check"`
- default H2 persistence confirmed under `/app/data/kairos.mv.db` and `/app/data/kairos.lock.db`
- `mvn test` on the standard JVM path

Native runtime validation also required targeted runtime hints for Thymeleaf expression helper classes used by the UI templates.

## Thymeleaf Guidance

The main native-runtime regression encountered so far was not application startup, but server-side template rendering.

Two concrete failure modes showed up:

- direct SpEL method calls on model objects such as `someList.isEmpty()` triggered missing reflection registration in the native image
- Thymeleaf expression-object helpers such as `#lists`, `#strings`, `#numbers`, and `#temporals` also need to be available for reflective invocation in native mode
- model object properties used only by server-rendered templates are not guaranteed to be discovered by Spring AOT
- private view helper records and Spring Data `PageImpl` pagination objects also need explicit template reflection hints when templates access their properties

To keep future UI work native-safe:

1. Prefer standard Thymeleaf expression objects over ad hoc Java method calls inside templates.
   Example: prefer `#lists.isEmpty(items)` over `items.isEmpty()`.
   Also prefer `#strings.contains(value, 'needle')` and `#lists.contains(values, item)` over calling `value.contains(...)` or `values.contains(...)` directly.
2. Prefer property-style expressions for records and enums.
   Example: prefer `entry.kind` and `resource.resourceType.name` over `entry.kind()` and `resource.resourceType.name()`.
3. Keep every Thymeleaf expression helper used by templates registered in [NativeRuntimeHintsConfig.java](c:/Git/wenisch.tech/kairos/src/main/java/tech/wenisch/kairos/config/NativeRuntimeHintsConfig.java:1).
   If a future template introduces helpers such as `#maps`, `#sets`, or similar, extend the runtime hints class in the same change.
4. Keep server-rendered model types registered in [NativeRuntimeHintsConfig.java](c:/Git/wenisch.tech/kairos/src/main/java/tech/wenisch/kairos/config/NativeRuntimeHintsConfig.java:1).
   This includes DTOs, JPA entities, enums, view-only helper records, and third-party model objects such as `PageImpl` when templates access their properties.
5. Treat template changes as native-impacting changes.
   Any new page, fragment, or significant `th:*` expression change should be validated with the native Docker build, not only with JVM tests.
6. Re-run endpoint checks after UI changes and manually exercise the affected pages in the native container.
   The minimum smoke check is `/`, `/api/resources`, and `/actuator/health`; for UI-heavy work, also open the changed pages directly.

Concrete findings from native rollout testing:

- the public dashboard failed on `DashboardGroupShell` property access until the DTO was registered for template reflection
- the resource detail page uses `ResourceViewModel`, `TimelineBlockDTO`, `CheckResult`, `Outage`, `PageImpl`, and private `HomeController` summary records
- admin pages use many entity-backed models directly, including announcements, users, API keys, resources, groups, discovery config, notification providers, notification policies, proxy settings, and custom header settings
- the admin sidebar used direct `String.contains(...)`; this was replaced with `#strings.contains(...)`
- resource group multi-select templates used projected-list `.contains(...)`; this was replaced with `#lists.contains(...)`
- admin check history uses `CheckAuditEntry`; record accessors should be used as properties and the record must stay registered for reflection

Recommended workflow after Thymeleaf-related changes:

```bash
docker build -f Dockerfile-native -t kairos-native-local:test .
bash scripts/check-container-endpoints.sh kairos-native-local:test 18081 native-local-endpoint-check.md "Native Local Endpoint Check"
```

If the changed work touches templates beyond the public dashboard, start the native container and verify the concrete page paths you changed as well.

## Flyway And Persistence Guidance

Another native-specific issue showed up only when starting against an existing persisted database, which is the normal Helm/PVC case.

The failure mode was:

- the native image opened the H2 database successfully under `/app/data`
- Flyway then failed validation because Java-based migrations already recorded in `flyway_schema_history` were not being discovered from native classpath scanning
- startup aborted even though the same database worked in the JVM image

In Kairos, several migrations are implemented as Java migrations under `src/main/java/db/migration`. Those must not rely on native classpath scanning alone.

To keep future migrations native-safe:

1. If you add a new Java Flyway migration, also register it in [FlywayMigrationConfig.java](c:/Git/wenisch.tech/kairos/src/main/java/tech/wenisch/kairos/config/FlywayMigrationConfig.java:1).
2. Treat migration changes as persisted-state changes, not only first-boot changes.
   A native image that works against an empty database can still fail against an existing PVC with prior migration history.
3. Validate both cases after Flyway changes:
   - clean database startup
   - startup against a database first initialized by the JVM image or a previous release

Recommended validation flow after migration changes:

```bash
mvn -B -DskipTests package
docker build -f Dockerfile-native -t kairos-native-local:test .
```

Then verify:

1. Native startup on a clean database.
2. Native startup against an existing H2 database directory populated by the JVM application.

For Helm deployments this matters because the PVC preserves `flyway_schema_history`, so native rollout must remain compatible with migration metadata produced by earlier JVM releases.

## JPA Lazy-Loading Guidance

Another native-specific startup failure came from Hibernate lazy loading, not from SQL or schema compatibility.

The failure mode was:

- Kairos started bootstrapping normally against the persisted H2 database
- `MetricsService` loaded the latest `CheckResult` for each resource during startup
- the startup path then touched the lazy `CheckResult.resource` association
- Hibernate tried to generate a runtime proxy and native startup aborted with `Generation of HibernateProxy instances at runtime is not allowed when the configured BytecodeProvider is 'none'`

In practice this means native-safe code must not assume that startup-time service logic can freely traverse lazy JPA relations the way the JVM build often tolerates.

To keep future persistence-related work native-safe:

1. Avoid dereferencing lazy associations in startup hooks such as `@PostConstruct`, application-ready listeners, bootstrap caches, and metric initialization.
2. When startup code already has the owning entity or identifier, pass that state through explicitly instead of re-reading it from a lazily loaded relation later.
   The fix for this regression was to initialize latest-check gauges from the already-known `MonitoredResource` state and avoid calling `result.getResource()` in the startup path.
3. If startup logic truly needs related data, load it explicitly with a query shape that is native-safe.
   Prefer repository methods with fetch joins or projections over incidental lazy traversal.
4. Treat service-layer refactors around metrics, caches, dashboard bootstrapping, and initial synchronization as native-impacting even if they do not change templates or migrations.

Recommended validation flow after JPA/service bootstrap changes:

```bash
mvn -B -Dtest=MetricsServiceTest test
docker build -f Dockerfile-native -t kairos-native-local:test .
```

Then verify both:

1. clean native startup
2. native startup against an existing `/app/data` database directory or Helm PVC-style persisted data

The second case matters because startup bootstrap code often only touches historical entities when real prior data exists.

## Runtime Validation Areas

Validate these areas before treating the native image as production-ready:

| Area | Scenarios |
|------|-----------|
| Persistence | H2 file mode, PostgreSQL mode, Flyway SQL migrations, and Flyway Java migrations. |
| Web UI | Dashboard, admin pages, Thymeleaf templates, WebJars assets, and static resources. |
| Security | Local login, API key authentication, and OIDC login with a real or test issuer. |
| API | `/actuator/health`, `/api/resources`, `/api`, `/h2-console`, `/sse`, and `/mcp/message`. |
| Checks | HTTP, TCP, Docker image, Docker repository discovery, and OpenShift route discovery. |
| Integrations | Import/export, email, Discord, generic webhook, GitLab notifications, and MCP tools. |

Native images use closed-world analysis. If a runtime path fails because reflection, resource loading, serialization, or proxy use was not discovered at build time, add focused runtime hints and rebuild the image.

## References

- [Spring Boot GraalVM native applications](https://docs.spring.io/spring-boot/3.5/how-to/native-image/developing-your-first-application.html)
- [Spring Boot native image advanced topics](https://docs.spring.io/spring-boot/3.5/reference/packaging/native-image/advanced-topics.html)
- [Spring AI MCP native image hints](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html)
