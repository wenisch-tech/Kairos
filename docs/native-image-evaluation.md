# GraalVM Native Image Evaluation

Kairos ships a JVM image by default. Native images are experimental and should be published under separate tags, for example `2.9.5-native`, until runtime parity is proven.

The goal is to compare GraalVM native images against cheaper JVM options before changing production defaults.

## Build Commands

Build and test the regular JVM artifact:

```bash
mvn test
```

Build the AOT-processed JVM artifact:

```bash
mvn -Pnative -DskipTests package
```

Build an experimental Linux native container image with Spring Boot buildpacks:

```bash
mvn -Pnative -DskipTests spring-boot:build-image \
  "-Dspring-boot.build-image.imageName=ghcr.io/wenisch-tech/kairos-native:2.9.5-native"
```

The buildpack path is preferred for evaluation because it builds the Linux container image that Kubernetes will run and does not require a local GraalVM installation.
Quote the `spring-boot.build-image.imageName` property in PowerShell because the dotted property name can otherwise be parsed incorrectly.

The Maven build passes `BP_NATIVE_IMAGE_BUILD_ARGUMENTS=--initialize-at-run-time=sun.security.util.Password$ConsoleHolder` to buildpacks. This prevents GraalVM from storing JDK console state in the native image heap.

To build a native executable directly, install a GraalVM distribution with `native-image` support and run:

```bash
mvn -Pnative -DskipTests native:compile
```

On Windows, this requires a GraalVM or Liberica Native Image Kit JDK plus the Visual Studio Build Tools and Windows SDK.

## Evaluation Matrix

| Candidate | Purpose |
|-----------|---------|
| `jvm-current` | Existing Dockerfile and Helm settings. |
| `jvm-tuned` | Existing JVM image with smaller memory limits and lower JVM RAM percentages. |
| `jvm-aot` | AOT-processed JAR built with `mvn -Pnative package` and run with `-Dspring.aot.enabled=true`. |
| `jvm-cds` | JVM image using CDS, or AOT cache when running on Java 24 or newer. |
| `native-buildpack` | Native executable container built with Spring Boot buildpacks. |
| `native-slimmed` | Later native variant with optional production-disabled features if benchmarks justify it. |

Run each candidate with `512Mi`, `768Mi`, and `1Gi` memory limits.

## Runtime Validation

Validate these areas before treating the native image as production-ready:

| Area | Scenarios |
|------|-----------|
| Persistence | H2 file mode, PostgreSQL mode, Flyway SQL migrations, and Flyway Java migrations. |
| Web UI | Dashboard, admin pages, Thymeleaf templates, WebJars assets, and static resources. |
| Security | Local login, API key authentication, and OIDC login with a real or test issuer. |
| API | `/actuator/health`, `/api/resources`, `/api`, `/h2-console`, `/sse`, and `/mcp/message`. |
| Checks | HTTP, TCP, Docker image, Docker repository discovery, and OpenShift route discovery. |
| Integrations | Import/export, email, Discord, generic webhook, GitLab notifications, and MCP tools. |

Native image builds use closed-world analysis. If a runtime path fails because reflection, resource loading, serialization, or proxy use was not discovered at build time, add targeted runtime hints and re-run the native build.

## Benchmark Metrics

Record the following for every candidate:

| Metric | Notes |
|--------|-------|
| Idle RSS | Primary metric for replica memory cost. |
| Heap and non-heap | JVM candidates only. |
| Startup time | Time until readiness passes. |
| First-request latency | Include `/` and `/api/resources`. |
| Check latency | Include HTTP, TCP, and Docker checks. |
| CPU usage | Idle and during scheduled checks. |
| Image size | Compare pull/storage impact. |
| Build duration | Include CI time impact. |

## Decision Criteria

Prefer the tuned JVM path if native saves less than roughly 30-40% RSS compared with `jvm-tuned`.

Keep native as a separate image tag until all runtime validation passes and build time is acceptable for the release cadence.

If native is adopted, keep publishing JVM images for at least one release cycle as a rollback path.

## References

- [Spring Boot GraalVM native applications](https://docs.spring.io/spring-boot/3.5/how-to/native-image/developing-your-first-application.html)
- [Spring Boot native image advanced topics](https://docs.spring.io/spring-boot/3.5/reference/packaging/native-image/advanced-topics.html)
- [Spring Boot Class Data Sharing and AOT cache](https://docs.spring.io/spring-boot/3.5/reference/packaging/class-data-sharing.html)
- [Spring AI MCP native image hints](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html)
