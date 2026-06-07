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

To keep future UI work native-safe:

1. Prefer standard Thymeleaf expression objects over ad hoc Java method calls inside templates.
   Example: prefer `#lists.isEmpty(items)` over `items.isEmpty()`.
2. Keep every Thymeleaf expression helper used by templates registered in [NativeRuntimeHintsConfig.java](c:/Git/wenisch.tech/kairos/src/main/java/tech/wenisch/kairos/config/NativeRuntimeHintsConfig.java:1).
   If a future template introduces helpers such as `#maps`, `#sets`, or similar, extend the runtime hints class in the same change.
3. Treat template changes as native-impacting changes.
   Any new page, fragment, or significant `th:*` expression change should be validated with the native Docker build, not only with JVM tests.
4. Re-run endpoint checks after UI changes and manually exercise the affected pages in the native container.
   The minimum smoke check is `/`, `/api/resources`, and `/actuator/health`; for UI-heavy work, also open the changed pages directly.

Recommended workflow after Thymeleaf-related changes:

```bash
docker build -f Dockerfile-native -t kairos-native-local:test .
bash scripts/check-container-endpoints.sh kairos-native-local:test 18081 native-local-endpoint-check.md "Native Local Endpoint Check"
```

If the changed work touches templates beyond the public dashboard, start the native container and verify the concrete page paths you changed as well.

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
