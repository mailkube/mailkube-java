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
| User-Agent suffix | `userAgentSuffix(...)` | | none |

Set `userAgentSuffix` when your software wraps this SDK — a CLI, an internal service, a framework
integration — so requests can be attributed to the tool the user actually ran. This library's token
stays leading, giving `mailkube-java/1.1.0 my-cli/1.0.0`. Give it the conventional `name/version`
form; surrounding whitespace is trimmed, and a value containing CR or LF is ignored rather than
sanitized, since a header value that could be split is not one this library will send.

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

### Logging

Silent unless you ask. Turn on one line per exchange with a level:

```java
var client = MailkubeClient.builder()
    .logging(System.Logger.Level.INFO)
    .build();
```

or set `MAILKUBE_LOG` to a **level name**: `MAILKUBE_LOG=DEBUG`, `MAILKUBE_LOG=WARNING`. It is a
level and not an on/off switch, so `WARNING` really does suppress the quieter records. A value that
names no level falls back to `DEBUG` rather than refusing to build a client. Records go through
`System.Logger`, so they land in whatever logging framework your application already runs, with no
adapter and no extra dependency.

Enabling is **per client**, never per process. Two clients in one application can differ, and no
library on your classpath can turn logging on for a client it does not own.

Each line carries the method, path, status, request id and elapsed time. **Nothing else, ever.** No
request body, no response body, no headers. A send body carries recipient addresses, subject lines,
rendered HTML and template variables, and there is no redaction rule that makes those safe in an
application log, so this SDK does not log them and ships no redaction helper.

For metrics rather than text, take the same information as a callback:

```java
var client = MailkubeClient.builder()
    .observer((method, path, status, requestId, elapsed, error) ->
        registry.timer("mailkube", "path", path, "status", String.valueOf(status)).record(elapsed))
    .build();
```

`error` is non-null only when no response arrived at all. A 4xx or 5xx is a response, so it comes
through with a status and no error. The observer is called on the thread that made the request, so
keep it cheap; one that throws is reported and ignored rather than failing your send.

### Webhooks

`Webhooks.verifySignature` is a dependency-free HMAC check over the **raw** request body. Never
parse then re-serialize, or the signature will not match. `Webhooks.verify` is the combinator most
handlers want — it verifies, then builds the typed event out of the bytes verification returned,
which is the order that makes "verify a re-serialization" impossible to write by accident:

```java
byte[] raw = request.getInputStream().readAllBytes();
WebhookEvent event = Webhooks.verify(raw, headers, System.getenv("MAILKUBE_WEBHOOK_SECRET"));

switch (event) {
    case EmailBouncedEvent e -> suppress(e.message().to(), e.bounce().reason());
    case EmailClickedEvent e -> record(e.message().emailId(), e.click().link());
    default -> { }
}
```

Call `verifySignature` and `parseEvent` separately instead if you need the verified bytes in hand —
to log or forward them, say — before deciding to parse.

`WebhookEvent` is a **sealed** interface, so a `switch` with no `default` arm will not compile until
it handles every event type — the compiler tells you when a new one appears rather than your logs.

`Webhooks.sign` is the mirror, so your own tests can build a valid request without reimplementing
the HMAC from this page:

```java
String timestamp = Instant.now().toString();
String signature = Webhooks.sign("wh_1", timestamp, body, secret);
```

| Event | Type | Carries |
|---|---|---|
| `EmailSentEvent` | `email.sent` | `message()`, `sent()` — accepted and spooled |
| `EmailDeliveredEvent` | `email.delivered` | `message()`, `delivery()` — the receiving server took it |
| `EmailBouncedEvent` | `email.bounced` | `message()`, `bounce()` — permanent failure, with code and reason |
| `EmailDeliveryDelayedEvent` | `email.delivery_delayed` | `message()`, `delay()` — deferred, will retry |
| `EmailSuppressedEvent` | `email.suppressed` | `message()`, `suppression()` — prior hard bounce or topic opt-out |
| `EmailScheduledEvent` | `email.scheduled` | `message()`, `scheduled()` — accepted for later |
| `EmailFailedEvent` | `email.failed` | `message()`, `failed()` — dropped at dispatch, never transmitted |
| `EmailOpenedEvent` | `email.opened` | `message()`, `open()` |
| `EmailClickedEvent` | `email.clicked` | `message()`, `click()` — with the link |
| `DomainStatusEvent` | `domain.status` | the domain, its new state, and `previous()` |
| `WebhookStatusEvent` | `webhook.status` | the endpoint, its new state, and `previous()` |
| `UnknownEvent` | anything else | `type()` and `raw()` |

Two rules keep a receiver working across releases. A `type` this version does not recognize parses
as `UnknownEvent` instead of raising, so a new platform event never breaks you. And `raw()` on every
event returns the whole decoded payload, so a field added after this release still reaches you even
though no typed accessor exists for it yet.

Event `type`, `status` and `reason` values stay `String` and never become enums, for the same
reason: a value the server introduces must not turn into a parse error on a client that was working.

## Schedule an email

Set `scheduledAt` and the message is accepted now and delivered later. Pass either an `Instant` or
an ISO-8601 string with a timezone offset; it must be in the future and within your plan's
scheduling horizon (30 days by default):

```java
Email email = client.emails().send(
    SendEmailParams.builder("Acme <hello@yourdomain.com>", List.of("customer@example.com"), "Your weekly digest")
        .html("<p>Here's what happened.</p>")
        .scheduledAt(Instant.now().plus(Duration.ofHours(2)))
        .batchId("digest-2026-08")   // optional: group sends so you can move or cancel them together
        .build());

email.isScheduled();   // true
email.status();        // "scheduled"
email.scheduledAt();   // "2026-08-20T07:00:00Z"
email.id();            // use this to retrieve, reschedule or cancel it
```

An immediate send is unaffected: `isScheduled()` is `false` and `status()` / `scheduledAt()` /
`batchId()` stay null. `batchId` is only valid alongside `scheduledAt`.

## Manage scheduled emails

Until it is due, a scheduled email lives in `client.scheduledEmails()`:

```java
ScheduledEmail email = client.scheduledEmails().get(emailId);
email = client.scheduledEmails().update(emailId, ScheduledEmailUpdateParams.builder(newTime).build());
client.scheduledEmails().cancel(emailId);
```

### Listing

`list` returns one page. `iterateAll` walks every page lazily, following the links the API returns,
so stopping early costs no further request:

```java
ScheduledEmailPage page = client.scheduledEmails().list(
    ScheduledEmailListParams.builder().status("scheduled").batchId("digest-2026-08").build());

page.data();                       // List<ScheduledEmail>
page.pagination().totalCount();
page.hasMore();

client.scheduledEmails()
    .iterateAll(ScheduledEmailListParams.builder().status(List.of("scheduled", "failed")).build())
    .forEach(e -> System.out.println(e.id() + " " + e.scheduledAt() + " " + e.subject()));
```

| Filter | Accepts |
|---|---|
| `status` | `"scheduled"`, `"canceled"`, `"failed"` — one, or a list. A sent email has left the collection, so `"sent"` is a validation error, not an empty result. |
| `batchId` | The batch label used at send time. |
| `scheduledAtGte` / `scheduledAtLte` | An `Instant`, or ISO-8601 text with an offset. |
| `page` | 1-based page number. Prefer `iterateAll`. |

Timestamps come back as the verbatim ISO-8601 strings the API sent — call `Instant.parse` if you
want objects. `recipients` is a summary string (`"a@b.com +2"`), not a list: the full recipient set
stays server-side with the frozen payload.

### Batches

Everything sent under one `batchId` moves or cancels together:

```java
var moved = client.scheduledEmails().batches().update("digest-2026-08", Instant.parse("2026-08-21T07:00:00Z"));
moved.rescheduledCount();   // 2

var canceled = client.scheduledEmails().batches().cancel("digest-2026-08");
canceled.canceledCount();   // 2
```

An unknown batch is a no-op reporting `0`, not an error.

### Scheduling errors

The names specific to this surface:

```java
try {
    client.scheduledEmails().cancel(emailId);
} catch (NotFoundException e) {
    // scheduled_email_not_found
} catch (InvalidRequestException e) {
    if (ErrorName.SCHEDULED_EMAIL_NOT_PENDING.equals(e.errorName())) {
        // already sent or canceled
    }
}
```

Every API error also carries `requestId()` — quote it when contacting support.

## More examples

Runnable scripts, each a single file you can read top to bottom:

| Example | Shows |
|---|---|
| [`examples/SimpleSend.java`](examples/SimpleSend.java) | The smallest useful program: send one email, handle the errors that matter. |
| [`examples/ManageScheduledEmails.java`](examples/ManageScheduledEmails.java) | Schedule a send, list and walk the collection, reschedule, and cancel a batch. |
| [`examples/WebhookReceiver.java`](examples/WebhookReceiver.java) | A working endpoint: verify the signature, parse the event, handle every type. |

## Extending this SDK

Before adding a verb, a resource, a paginated listing or a webhook event, read
[`.rules/SDK_CONTRACT.md`](.rules/SDK_CONTRACT.md) (the decisions every mailkube SDK shares) and
[`.rules/SDK_DESIGN.md`](.rules/SDK_DESIGN.md) (how they are realized in Java). Both carry a
step-by-step checklist.

Runnable scripts live in [`examples/`](examples/); every checklist ends with adding one.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup and the quality gates every change
must pass. Security issues: see [SECURITY.md](SECURITY.md).

## License

[Apache-2.0](LICENSE) © 2026 Mail Tactic Corporation
