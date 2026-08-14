package com.mailkube;

import com.mailkube.internal.Config;
import com.mailkube.internal.HttpTransport;
import com.mailkube.internal.ScheduledTransport;
import com.mailkube.internal.SendTransport;
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

    private MailkubeClient(Builder builder) {
        Config config = new Config(builder.apiKey, builder.baseUrl, builder.timeout, builder.environment);
        this.ownsHttpClient = builder.httpClient == null;
        this.httpClient = ownsHttpClient ? defaultHttpClient(config) : builder.httpClient;
        // One transport object satisfies both narrow interfaces. The resources still depend on one
        // interface each, so a test can substitute either capability without touching the other.
        HttpTransport http = new HttpTransport(config, this.httpClient);
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
         * <p>This is the public dependency-inversion seam: pass a client configured with your own
         * proxy, SSL context or instrumentation. The caller owns its lifecycle, so
         * {@link MailkubeClient#close()} will not close it.
         *
         * @param httpClient the client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Read configuration from this map instead of the process environment.
         *
         * <p>Present so the environment-fallback rules can be tested without mutating the real
         * environment, which is process-global and hostile to parallel tests.
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
