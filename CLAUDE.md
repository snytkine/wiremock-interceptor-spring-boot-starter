# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Role

You are an expert Java developer and an expert with the Spring Boot framework, and you have extensive knowledge of the inner workings of the WireMock project's documentation and Java code.

## Development Rules

- **After creating or modifying any Java file**, run `mvn spotless:apply` to apply the correct license header and code formatting before considering the change done.
- **After any code change**, verify the project compiles cleanly with `mvn compile`. Do not leave the codebase in a broken state.
- **When creating a new class or method, or modifying an existing method**, add or update the corresponding unit tests. Keep coverage above the enforced minimums (80% instruction, 70% branch).

## Build Commands

```bash
./mvnw clean verify          # full build: compile, test, coverage check, format check
./mvnw test                  # run all tests
./mvnw test -Dtest=WMInterceptorTest          # run a single test class
./mvnw test -Dtest=WMInterceptorTest#testFoo  # run a single test method
./mvnw spotless:apply        # auto-format all Java source files
./mvnw spotless:check        # verify formatting without applying
./mvnw clean deploy          # publish to Maven Central (requires GPG key and ~/.m2/settings.xml with <server id="central">)
```

Code formatting is enforced by Spotless (Google Java Format 1.17). `spotless:apply` runs automatically at the `initialize` phase on every full build; `spotless:check` enforces it again at `validate` and fails the build if any file is not formatted correctly.

JaCoCo enforces **80% instruction coverage** and **70% branch coverage** at the `verify` phase.

## Architecture

This is a **Spring Boot auto-configuration starter** (library, not an application). It adds a `ClientHttpRequestInterceptor` to Spring's HTTP client stack that silently routes matching requests to WireMock stubs instead of the real network.

### Activation

The entire starter is gated by a single property:

```yaml
net.snytkine.rest-client-wiremock-interceptor:
  enabled: true
```

`WMInterceptorAutoConfiguration` (`@ConditionalOnProperty`) is the entry point registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. When the condition is satisfied it `@Import`s `WireMockConfigurationFactory` and `WMInterceptorFactory`.

### Bean wiring

```
WireMockProperties  ──►  WireMockConfigurationFactory  ──►  WireMockConfiguration bean
WireMockConfiguration  ┐
WireMockProperties     ├──►  WMInterceptorFactory  ──►  restClientWiremockInterceptor bean (@Order(50))
```

### In-process stub matching — no network

`WMInterceptor` does **not** start a real HTTP server. It uses WireMock's `DirectCallHttpServerFactory` / `DirectCallHttpServer` to do all stub matching in-process:

```java
DirectCallHttpServerFactory factory = new DirectCallHttpServerFactory();
wireMockConfiguration.httpServerFactory(factory);
new WireMockServer(wireMockConfiguration).start(); // wires up stubs, no-op for networking
this.directCallHttpServer = factory.getHttpServer();
// later:
Response r = directCallHttpServer.stubRequest(wiremockRequest);
if (r.wasConfigured()) { /* return mock */ } else { /* pass through */ }
```

### Two-way adapter pattern

Because WireMock and Spring use incompatible request/response types, two private static adapter classes live inside `WMInterceptor`:

| Adapter | Converts | Implements |
|---|---|---|
| `SpringHttpRequestAdapter` | Spring `HttpRequest` + `byte[] body` → WireMock `Request` | `com.github.tomakehurst.wiremock.http.Request` |
| `WiremockClientHttpResponse` | WireMock `Response` → Spring `ClientHttpResponse` | `org.springframework.http.client.ClientHttpResponse` |

Notable adapter details:
- Port defaults: `-1` from `URI.getPort()` is normalised to `80` (HTTP) or `443` (HTTPS).
- `getClientIp()` returns `"0.0.0.0"` — not available in Spring's request model.
- `isMultipart()` checks `Content-Type` string prefix `multipart/form-data`.
- `WiremockClientHttpResponse` copies WireMock headers into a mutable Spring `HttpHeaders` on construction; `setHeader()` is used post-construction to inject the optional mock-identification header.

### Configuration properties

All properties live under the prefix `net.snytkine.rest-client-wiremock-interceptor` (class `WireMockProperties`). Noteworthy ones:

| Property | Default | Purpose |
|---|---|---|
| `enabled` | `false` | Master switch; starter does nothing if false |
| `mappings-class-path` | `null` | Classpath-relative root for stubs (must contain a `mappings/` subdir; optionally `__files/` for body files) |
| `templating-enabled` | `false` | Enables WireMock response templating engine |
| `proxy-pass-through` | `false` | When true, unmatched requests pass to real services; when false they fail |
| `mock-response-header` | `null` | If set, this header name is added to every mocked response |
| `mock-response-header-value` | `null` | Value for that header (falls back to `"mock-middleware"` if header name is set but value is null) |
| `root-directory` | `null` | Filesystem path for stubs (alternative to `mappings-class-path`) |

### Faker extension

`WireMockConfigurationFactory` unconditionally registers `org.wiremock.RandomExtension` (the [WireMock Faker extension](https://github.com/wiremock/wiremock-faker-extension/blob/main/docs/reference.md)), so `{{ random 'Name.first_name' }}` and similar Faker helpers are always available in response templates.

### WireMock version constraint

The `wiremock` dependency is declared with scope `provided` and a version range `[3.6.0, 3.10.0]`. Consumer applications **must** supply their own WireMock dependency. The starter is known to have issues with WireMock versions above `3.10.0` — recommend consumers pin to `3.10.0`.

### `restClientWiremockInterceptor` bean ordering

The interceptor bean is annotated `@Order(50)`. Consumers who have other `ClientHttpRequestInterceptor` beans should be aware that Spring does **not** automatically sort injected lists by `@Order`; they need to sort the list explicitly (e.g. via `AnnotationAwareOrderComparator.sort(interceptors)` in a `@PostConstruct`).
