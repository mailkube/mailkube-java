package com.mailkube;

import com.mailkube.internal.Config;
import com.mailkube.internal.HttpTransport;
import com.mailkube.internal.LoggingObserver;
import com.mailkube.internal.ScheduledTransport;
import com.mailkube.internal.SendTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * The API client, and this library's composition root.
 *
 * <p>Create one and reuse it. It holds no per-request state and is safe to share across threads,
 * platform or virtual; {@code ConcurrencyTest} proves that rather than asserting it.
 *
 * <pre>{@code
 * var client = MailkubeClient.builder().build();   // reads MAILKUBE_API_KEY
 * var email = client.emails().send(SendEmailParams.builder(
 *         "Acme <hello@yourdomain.com>", List.of("customer@example.com"), "Hello world")
 *     .html("<p>It works!</p>")
 *     .build());
 * }</pre>
 *
 * <p>There are deliberately no built-in retries. A {@code RateLimitException} carries
 * {@code retryAfter()} and a {@code ServerException} is safe to retry with backoff, so the calling
 * application decides. Set {@code idempotencyKey} to make a retry safe.
 *
 * <p>This client is <b>synchronous</b>, which is the contract's sync-only case: on Java 25,
 * concurrency is the caller's to choose (a virtual thread per call costs almost nothing), so a
 * second asynchronous surface would duplicate every verb to deliver what the caller already has.
 * See {@code .rules/SDK_DESIGN.md}.
 */
public final class MailkubeClient implements AutoCloseable {

    private final Emails emails;
    private final ScheduledEmails scheduledEmails;
    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final Config config;

    private MailkubeClient(Builder builder) {
        Config config = new Config(
                builder.apiKey,
                builder.baseUrl,
                builder.timeout,
                builder.logLevel,
                builder.userAgentSuffix,
                builder.environment);
        this.config = config;
        this.ownsHttpClient = builder.httpClient == null;
        this.httpClient = ownsHttpClient ? defaultHttpClient(config) : builder.httpClient;
        // One transport object satisfies both narrow interfaces. The resources still depend on one
        // interface each, so a test can substitute either capability without touching the other.
        HttpTransport http = new HttpTransport(config, this.httpClient, observerFor(builder, config));
        this.emails = new Emails(builder.transport != null ? builder.transport : http);
        this.scheduledEmails =
                new ScheduledEmails(builder.scheduledTransport != null ? builder.scheduledTransport : http);
    }

    /**
     * Start building a client.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The emails namespace.
     *
     * @return the resource
     */
    public Emails emails() {
        return emails;
    }

    /**
     * The scheduled-emails namespace.
     *
     * @return the resource
     */
    public ScheduledEmails scheduledEmails() {
        return scheduledEmails;
    }

    /**
     * The API origin this client was configured with.
     *
     * <p>Read-only, and here for the benefit of the thing that has to report it: a framework health
     * indicator or a startup check states which API it is talking to, and reading it back beats
     * having the surrounding application remember what it passed in.
     *
     * @return the resolved base URL, whether it came from the builder, the environment or the default
     */
    public URI baseUrl() {
        return config.baseUrl();
    }

    /**
     * The timeout applied to every exchange.
     *
     * <p>Every request carries it, whichever {@link HttpClient} sends it, so a framework binding a
     * timeout setting gets that timeout even when it also injects its own instrumented client.
     *
     * @return the resolved per-request timeout
     */
    public Duration timeout() {
        return config.timeout();
    }

    /**
     * Release the underlying HTTP client, if this client created it.
     *
     * <p>{@code HttpClient} became {@link AutoCloseable} in Java 21. An <b>injected</b> client
     * belongs to whoever passed it in and is deliberately left open here, which is the Java
     * realization of the contract's "does not close a client it did not create" rule.
     */
    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    /**
     * Decide who hears about each exchange: your observer, the built-in logger, or nobody.
     *
     * <p>Precedence is the same one every other setting follows. An observer you supplied wins,
     * because it is the most explicit thing you said; otherwise a level, from
     * {@link Builder#logging} or from {@code MAILKUBE_LOG}, installs the built-in logger; otherwise
     * nothing is notified at all. There is no static enable, so two clients in one process can
     * differ, and nothing a library does can turn logging on for somebody else's client.
     */
    private static RequestObserver observerFor(Builder builder, Config config) {
        if (builder.observer != null) {
            return builder.observer;
        }
        // A no-op rather than a null the transport would have to test for on every request.
        return config.logLevel() == null
                ? (method, path, status, requestId, elapsed, error) -> {}
                : new LoggingObserver(config.logLevel());
    }

    private static HttpClient defaultHttpClient(Config config) {
        // No .executor(...) call. The default executor is what lets this client behave correctly
        // under virtual threads; supplying a fixed pool reintroduces exactly the bottleneck the
        // Java 25 floor exists to avoid.
        return HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    /** Builds a {@link MailkubeClient}. */
    public static final class Builder {

        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private HttpClient httpClient;
        private RequestObserver observer;
        private System.Logger.Level logLevel;
        private String userAgentSuffix;
        private SendTransport transport;
        private ScheduledTransport scheduledTransport;
        private Map<String, String> environment = System.getenv();

        private Builder() {}

        /**
         * The API key. Falls back to {@code MAILKUBE_API_KEY}.
         *
         * @param apiKey the key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Override the API base URL. Falls back to {@code MAILKUBE_BASE_URL}.
         *
         * @param baseUrl the base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The timeout for each request/response exchange.
         *
         * <p>It is applied to every request regardless of which {@link HttpClient} sends it, so
         * supplying your own through {@link #httpClient(HttpClient)} does not disable it. Only that
         * client's <em>connect</em> timeout is then yours to set.
         *
         * @param timeout the timeout
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Inject the {@link HttpClient} used for every request.
         *
         * <p>This is the public dependency-inversion seam, and it is the supported one for three
         * different jobs: a proxy or SSL context, outbound instrumentation, and substituting the
         * whole exchange in a test. A delegating client that rebuilds each request through
         * {@code HttpRequest.newBuilder(HttpRequest, BiPredicate)} is how you add a
         * {@code traceparent} header without this SDK growing a request-mutation hook of its own.
         *
         * <p>Two rules for a decorator: it must not drop the {@code Authorization} header, and it
         * must not drop or overwrite the {@code User-Agent}, which the API requires and which
         * identifies the SDK version in server-side traffic.
         *
         * <p>The caller owns its lifecycle, so {@link MailkubeClient#close()} will not close it.
         *
         * @param httpClient the client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Be told about every finished exchange.
         *
         * <p>This is the metrics seam: method, path, status, request id, elapsed time and outcome,
         * and deliberately nothing else. See {@link RequestObserver} for what it is never given and
         * why. Supplying one replaces the built-in logger, so {@link #logging} has no effect
         * alongside it.
         *
         * @param observer the observer, called on the calling thread
         * @return this builder
         */
        public Builder observer(RequestObserver observer) {
            this.observer = observer;
            return this;
        }

        /**
         * Log one line per exchange at this level. Falls back to {@code MAILKUBE_LOG}.
         *
         * <p>Off unless asked for, and per-client rather than per-process: a static switch would be
         * global mutable state that any library on the classpath could flip for everyone.
         *
         * <p>Records go to {@link System.Logger}, so they arrive in whatever logging framework the
         * host application has installed. {@code MAILKUBE_LOG} names a level rather than acting as
         * an on/off switch, so {@code MAILKUBE_LOG=WARNING} suppresses the quieter records; a value
         * naming no level falls back to {@code DEBUG} rather than refusing to build a client.
         *
         * @param level the level to log at
         * @return this builder
         */
        public Builder logging(System.Logger.Level level) {
            this.logLevel = level;
            return this;
        }

        /**
         * Append a token to the {@code User-Agent} this SDK sends.
         *
         * <p>For software that wraps this SDK — a CLI, an internal service, a framework
         * integration — so a request can be attributed to the tool the user actually ran while
         * still identifying the SDK underneath. This library's token stays leading:
         * {@code mailkube-java/1.2.3 my-cli/1.0.0}.
         *
         * <p>Give it the conventional {@code name/version} form. Surrounding whitespace is
         * trimmed, and a value containing CR or LF is ignored rather than sanitized: a header
         * value that could be split is not one this library will send.
         *
         * @param suffix the token to append
         * @return this builder
         */
        public Builder userAgentSuffix(String suffix) {
            this.userAgentSuffix = suffix;
            return this;
        }

        /**
         * Read configuration from this map instead of the process environment.
         *
         * <p>This replaces the configuration <em>source</em>; it does not disable the fallbacks. A
         * caller that has its own configuration system, a framework's property resolution for
         * instance, can point the same rules at it and keep one precedence order for everybody.
         *
         * <p>It also means the fallback rules can be tested without mutating the real environment,
         * which is process-global and hostile to parallel tests.
         *
         * <p>An empty map is not the way to make a setting explicit. Leave the setting unset and let
         * the fallback answer, or pass the value itself: passing {@code Map.of()} only turns off
         * every environment variable at once, including the ones the caller never meant to disable.
         *
         * @param environment the lookup
         * @return this builder
         */
        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Replace the transport wholesale.
         *
         * <p>The narrower internal seam, and deliberately <b>not public</b>: {@code SendTransport}
         * lives in a package the module does not export, so a public method taking one would be
         * unusable to consumers. Applications use {@link #httpClient(HttpClient)}, which keeps the
         * real request building, error mapping and parsing in play. This exists so a test can
         * drive resource behaviour without HTTP at all.
         *
         * @param transport the transport
         * @return this builder
         */
        Builder transport(SendTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Replace the transport backing the scheduled-emails resource.
         *
         * <p>A second setter rather than one taking both capabilities: they are separate interfaces
         * precisely so a caller can depend on one of them, and a test that substitutes sending has
         * no business also substituting listing.
         *
         * @param transport the transport
         * @return this builder
         */
        Builder scheduledTransport(ScheduledTransport transport) {
            this.scheduledTransport = transport;
            return this;
        }

        /**
         * Finish building.
         *
         * @return the client
         */
        public MailkubeClient build() {
            return new MailkubeClient(this);
        }
    }
}
