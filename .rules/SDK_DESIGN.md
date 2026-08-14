# SDK Design: the Java realization of the cross-SDK contract

Load this alongside [`SDK_CONTRACT.md`](SDK_CONTRACT.md) when adding a **resource, verb, response
model, paginated listing, or webhook event**.

`SDK_CONTRACT.md` is the shared, language-neutral constitution: configuration, layering, naming,
response-model rules, pagination, the error model, and the webhook contract, all of which every
mailkube SDK implements identically. It is shared verbatim across every mailkube SDK; treat it as
read-only here.

**This file covers only what is specific to Java.** A deliberate deviation from the contract belongs
here, never in the shared file.

## The layers, in packages

| Layer | Package | May know about |
|---|---|---|
| **Client / IO** | `com.mailkube.internal.HttpTransport` | `java.net.http` |
| **Core** | `com.mailkube.internal` (`Config`, `RequestSpec`, `Json`) | the transport interface |
| **Resources** | `com.mailkube.Emails` | the one transport interface its verbs need |
| **Types** | `com.mailkube.model`, `.exception` | nothing |

`MailkubeClient` is the composition root: it resolves config, wires the collaborators and exposes
the resources. It performs no I/O itself.

**Only `HttpTransport` imports `java.net.http`.** A resource or model that does is a bug.

### The module descriptor is the layering gate

`module-info.java` exports `com.mailkube`, `.exception` and `.model`, and deliberately **does not
export `.internal`**. That turns "resources depend on a narrow seam, callers depend on the public
surface" from a convention into a compile error. Keep it that way: the day `internal` is exported,
every internal type becomes public API you cannot change without a major version.

Two consequences worth knowing before they surprise you:

- **`requires transitive java.net.http`**, not a plain `requires`. `Builder.httpClient(HttpClient)`
  is public API, so a consumer must be able to name `HttpClient`. javac says so itself:
  `-Xlint:exports` fails the build on a plain `requires`.
- **Tests compile and run on the classpath**, via `modularity.inferModulePath = false` on the test
  tasks. They reach into `internal` on purpose. Without that setting Gradle infers a module path for
  the test source set and the internal package stops being visible.

## One client, and it is synchronous

This is the contract's **sync-only** case, and specifically its first clause: concurrency is the
caller's concern rather than an API-surface decision.

On Java 25 a virtual thread per call costs almost nothing, so
`Executors.newVirtualThreadPerTaskExecutor()` gives a caller everything an async surface would,
without this SDK shipping a second copy of every verb that could drift from the first. There is
deliberately no `CompletableFuture` variant.

### The Java 25 floor is load-bearing, not a preference

This matters because it is also the only honest justification for forcing 25 on everyone
downstream, including every Spring app that uses the starter.

On JDK 21 through 23, `synchronized` blocks and `Object.wait()` **pinned** the carrier thread, and
the JDK's own `HttpClient` used both internally. A virtual thread blocked in an HTTP call could
therefore hold its carrier, and "one synchronous client plus virtual threads" quietly degraded to a
thread pool the size of the ForkJoin common pool. **JEP 491 (JDK 24) removed monitor pinning.** The
design in this file is only defensible at 24 or above, and 25 is the LTS above that line.

**The accepted cost:** Java 25 class files cannot load on an earlier JVM, so every consumer
must run JVM 25+. That is eight versions above Spring Boot's own baseline.

### Three rules about the JDK client that are easy to get wrong

1. **Read the body with `BodyHandlers.ofByteArray()`, never `ofInputStream()`.** The streaming
   handler is the documented case where a virtual thread pins its carrier, and this contract reads
   the whole body anyway.
2. **Never call `.executor(...)` on the `HttpClient` builder.** The default executor is what lets
   the client behave correctly under virtual threads; a fixed pool reintroduces the bottleneck the
   floor exists to avoid.
3. **Do not add `-Djdk.tracePinnedThreads`.** It was removed in JDK 24. The replacement is the JFR
   `jdk.VirtualThreadPinned` event.

`HttpClient` became `AutoCloseable` in 21, so `MailkubeClient` implements it too and closes only the
client it created. An **injected** client belongs to whoever passed it in, which is the Java
realization of the contract's "does not close a client it did not create" rule.

## Concurrency safety is proven, not asserted

Nothing in the request path holds mutable state: `HttpTransport.sendEmail` keeps everything in
locals, and `Config` is immutable after construction. That is not a style preference. A field on the
transport is shared by every caller of a shared client, and two of them will swap responses.

`ConcurrencyTest` proves it: 32 concurrent calls on virtual threads against a real
`com.sun.net.httpserver` server that **answers nobody until all 32 have arrived**. Filling the
barrier proves the calls genuinely overlap (a client that serialized them would time out rather than
quietly pass), and releasing everyone at once makes them contend. Each request carries a distinct
`Idempotency-Key`, the server echoes it back as the response `id`, and every caller must receive its
own.

**Verified by breaking it on purpose:** stashing the response in a `HttpTransport` field produced
`idem-000 received the response for "idem-020"`. Response cross-talk does not throw; it silently
hands one caller another's body, which is why the assertion is written on identity rather than on
the absence of an exception.

## Zero runtime dependencies, including JSON

Java is the only language in this SDK family with no JSON in its standard library, so
`internal/Json` is hand-written. That is a deliberate trade, and the reasoning should survive the
next person who wants to delete it:

- This library is installed into other people's applications, where a Jackson or Gson version
  becomes *their* version conflict.
- Spring Boot 4 moved to Jackson 3 (`tools.jackson`). An SDK compiled against Jackson 2
  (`com.fasterxml.jackson`) would put a second Jackson on every Boot 4 classpath. The two can
  coexist, because the package names differ, but it is a question every reviewer asks forever.
- The cost is roughly 200 lines with its own test suite, not an open-ended maintenance burden,
  because the scope is fixed: encode what the API accepts, decode what it returns. It does not
  stream and it does not bind to types. **Do not grow it into a general-purpose library.**

## Java idioms that realize the contract

- **Builders replace keyword arguments.** `SendEmailParams.builder(from, to, subject)` takes the
  three required fields as parameters, so an incomplete send does not compile, and everything else
  is a chained setter.
- **The builder assembles the wire body directly**, rather than holding eighteen fields and mapping
  them later. One private `put` is the single place a null becomes an absent key, and the class has
  no accessors to write, test or keep in step.
- **Models are records**, so they are immutable and value-comparable for free. JaCoCo filters the
  generated members, so records do not sink the coverage gate.
- **One base constructor for the whole error hierarchy.** `ErrorEnvelope` carries the six fields, so
  the seven subclasses are one delegating constructor each. Eight subtypes repeating six parameters
  is how the duplication gate starts failing and how two categories start reporting different
  fields.
- **`ApiException.forStatus` is the single status-to-class table**, and the only place a status
  becomes a type.
- **Exceptions are unchecked.** A checked exception would force every caller of every verb to write
  a `try` block for failures most applications handle once, at the edge.

## PMD, not Error Prone

PMD gives cyclomatic **and** cognitive complexity, unused parameters **and** required Javadoc from
one config, which is the whole gate set this repo needs from a single tool.

Error Prone hooks javac internals, needs `--add-exports`, and has a painful JDK-compatibility
history: it was the largest green-on-birth risk in this toolchain and stays out. `-Xlint:all
-Werror` plus PMD's `errorprone` category covers most of its value.

**A PMD ruleset that fails to load does not fail the build.** With a bad property name it prints
`Cannot load ruleset ... XML validation errors occurred`, then `No files to analyze`, and Gradle
reports success with the gate silently switched off. After editing `config/pmd/ruleset.xml`, check
the `pmdMain` output for those lines: a green build is not proof the rules ran.

Three related details, all worth knowing before you edit the ruleset: `CommentRequired` has no
`headerCommentRequirement` property in PMD 7; its values are lowercase (`ignored`, not `Ignored`);
and **`config/pmd/ruleset-test.xml` cannot reference `ruleset.xml`**. PMD resolves a `ref` against
the classpath rather than against the referencing file, so the DRY form fails, and it fails in
exactly the silent way described above. The test ruleset therefore restates its rules on purpose.

## Two more toolchain notes

- **Spotless uses palantir-java-format, not google-java-format.** The latter needs
  `--add-exports jdk.compiler/...` since JEP 396, which on a build means JVM arguments affecting
  everything. Verified: palantir formats Java 25 sources, records and text blocks with no JVM
  args at all.
- **JaCoCo's `COMPLEXITY` counter is not a complexity gate.** It measures how much complexity is
  *covered*, not a maximum. Complexity is PMD's job; JaCoCo does `LINE` and `BRANCH` ratios at
  `BUNDLE` scope. `check.dependsOn(jacocoTestCoverageVerification)` is not automatic — without it,
  `check` runs the report and never the gate.

## Where the shared rules are enforced

| Contract rule | Enforced in |
|---|---|
| Key/base-URL resolution, default headers | `Config` constructor, `Config.defaultHeaders()` |
| Origin guard and URL joining | `Config.buildUrl()` |
| One place maps non-2xx to an exception | `HttpTransport.decodeOrThrow()` |
| Status-to-class table | `ApiException.forStatus()` |
| Idempotency key lifted to a header | `Emails.send()` |
| ISO-8601 rendering, base64 attachments | `SendEmailParams.Builder` |
| One version source, read by the User-Agent | `gradle.properties` → jar manifest → `Version.current()` |
| HTTP client injection and ownership | `Builder.httpClient(...)`, `MailkubeClient.close()` |
| Webhook signature verification | `Webhooks` (no client instance needed) |
| Concurrency safety, proven not asserted | `ConcurrencyTest` |
| Layering | `module-info.java` (`internal` is not exported) |

## Tests

There are two seams, and they are used for different things:

- **`Builder.httpClient(HttpClient)`** is the public one. Most tests drive the client against
  `StubServer`, a real `com.sun.net.httpserver` on a loopback port, so the request building, error
  mapping and response parsing are all genuinely exercised.
- **`Builder.transport(SendTransport)`** is package-private, and is how `EmailsTest` checks request
  shaping without HTTP at all. It is not public because `SendTransport` lives in a package the module
  does not export, so a public method taking one would be unusable to consumers.

`StubServer` looks headers up **case-insensitively**, which is not a nicety:
`com.sun.net.httpserver` rewrites header names to its own capitalisation (`Idempotency-key`,
`Content-type`), and an exact-match lookup silently returns null and fails the assertions somewhere
else entirely.

Coverage gates **line and branch at 90%**.

## What this SDK does not implement yet

One resource (`emails.send`) is wired end to end as the worked example. Still to add, each following
its checklist in `SDK_CONTRACT.md`:

- **A paginated listing.** Add a verb to `SendTransport`'s sibling interface, a page record, and an
  `Iterator` or `Stream` over pages. Follow the server's `next` link; `Config.buildUrl` already
  refuses off-origin links.
- **The typed webhook event catalogue.** `Webhooks.verifySignature` is complete; add the event
  records and a `parseEvent` helper.
