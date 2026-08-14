# mailkube-java

[![CI](https://github.com/mailkube/mailkube-java/actions/workflows/ci.yml/badge.svg)](https://github.com/mailkube/mailkube-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mailkube/mailkube-java.svg)](https://central.sonatype.com/artifact/com.mailkube/mailkube-java)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Code of Conduct](https://img.shields.io/badge/Contributor%20Covenant-2.1-purple.svg)](CODE_OF_CONDUCT.md)

The official Java SDK for mailkube

**Requires JVM 25 or newer.** That is a deliberate floor rather than an oversight: this SDK
ships one synchronous client and relies on virtual threads for concurrency, which only became safe
once JEP 491 removed monitor pinning in JDK 24. See [.rules/SDK_DESIGN.md](.rules/SDK_DESIGN.md).

## Install

Replace `X.Y.Z` with the version on the Maven Central badge above.

Gradle:

```kotlin
dependencies {
    implementation("com.mailkube:mailkube-java:X.Y.Z")
}
```

Maven:

```xml
<dependency>
  <groupId>com.mailkube</groupId>
  <artifactId>mailkube-java</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

## Usage

```java
import com.mailkube.MailkubeClient;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;

try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
    Email email = client.emails().send(
        SendEmailParams.builder("Acme <hello@yourdomain.com>", List.of("customer@example.com"), "Hello world")
            .html("<p>It works!</p>")
            .build());

    System.out.println(email.id());
}
```

**Zero runtime dependencies.** The published POM has no `<dependencies>` block; the standard library
covers everything, including JSON.

### Configuration

| Option | Builder method | Environment | Default |
|---|---|---|---|
| API key | `apiKey(...)` | `MAILKUBE_API_KEY` | required |
| Base URL | `baseUrl(...)` | `MAILKUBE_BASE_URL` | `https://api.mailkube.com/mta/v1/` |
| Timeout | `timeout(...)` | | 30s |
| HTTP client | `httpClient(...)` | | `HttpClient.newBuilder()...` |

Pass your own `HttpClient` to add a proxy, an SSL context or instrumentation. A client you inject
belongs to you: `close()` will not close it.

There are deliberately **no built-in retries**. A `RateLimitException` carries `retryAfter()` and a
`ServerException` is safe to retry with backoff, so the calling application decides. Set
`idempotencyKey` to make a retry safe.

### Concurrency

Create one client and reuse it. It holds no per-request state and is safe to share across threads.

The client is synchronous, and that is the point: on JVM 25 a virtual thread per call costs
almost nothing, so you get concurrency without this SDK shipping a second copy of every method.

```java
try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var params : batch) {
        pool.submit(() -> client.emails().send(params));
    }
}
```

### Errors

Every exception descends from `MailkubeException` and is **unchecked**, so catch the category you
care about at the edge:

```java
try {
    client.emails().send(params);
} catch (RateLimitException e) {
    Thread.sleep(Duration.ofSeconds(e.retryAfter() == null ? 1 : e.retryAfter()));
} catch (ApiException e) {
    log.warn("{} {}: {} (request {})", e.statusCode(), e.errorName(), e.getMessage(), e.requestId());
}
```

Categories: `BadRequestException` (400), `AuthenticationException` (403), `NotFoundException` (404),
`ConflictException` (409), `InvalidRequestException` (422), `RateLimitException` (429),
`ServerException` (5xx), and `ApiException` for anything else, which is also the supertype of all of
them. A transport failure raises `ConnectionException` and is deliberately **not** an API error.

### Webhooks

`Webhooks.verifySignature` is a dependency-free HMAC check over the **raw** request body. Never
parse then re-serialize, or the signature will not match.

```java
byte[] raw = request.getInputStream().readAllBytes();
Webhooks.verifySignature(raw, headers, System.getenv("MAILKUBE_WEBHOOK_SECRET"));
```

## Extending this SDK

This SDK ships one resource (`emails.send`) wired end to end as the worked example. Before
adding a verb, a resource, a paginated listing or a webhook event, read
[`.rules/SDK_CONTRACT.md`](.rules/SDK_CONTRACT.md) (the decisions every mailkube SDK shares) and
[`.rules/SDK_DESIGN.md`](.rules/SDK_DESIGN.md) (how they are realized in Java). Both carry a
step-by-step checklist.

Runnable scripts live in [`examples/`](examples/); every checklist ends with adding one.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup and the quality gates every change
must pass. Security issues: see [SECURITY.md](SECURITY.md).

## License

[Apache-2.0](LICENSE) © 2026 Mailtactic, Corp.
