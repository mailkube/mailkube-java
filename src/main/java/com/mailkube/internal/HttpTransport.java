package com.mailkube.internal;

import com.mailkube.RequestObserver;
import com.mailkube.exception.ApiException;
import com.mailkube.exception.ConnectionException;
import com.mailkube.exception.ErrorEnvelope;
import com.mailkube.model.Email;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Performs one HTTP round trip and turns the result into a model or a mapped exception.
 *
 * <p>This is the only class in the SDK that imports {@code java.net.http}. A resource or model
 * that does is a bug.
 *
 * <h2>Three rules about the JDK client that are not obvious</h2>
 *
 * <ul>
 *   <li><b>Read the body with {@code ofByteArray}, never {@code ofInputStream}.</b> The streaming
 *       handler is the documented case where a virtual thread pins its carrier, and this contract
 *       reads the whole body anyway.
 *   <li><b>Do not call {@code .executor(...)} on the builder.</b> The default executor is what
 *       lets the client behave correctly under virtual threads; replacing it with a fixed pool
 *       reintroduces the bottleneck the Java 25 floor exists to avoid.
 *   <li><b>Do not close a client you did not create.</b> {@code HttpClient} became
 *       {@link AutoCloseable} in 21, so an injected client belongs to the caller's lifecycle.
 * </ul>
 */
public final class HttpTransport implements SendTransport, ScheduledTransport {

    private static final System.Logger LOGGER = System.getLogger("com.mailkube");

    private final Config config;
    private final HttpClient httpClient;
    private final RequestObserver observer;

    /**
     * Bind the transport to its configuration and the client it drives.
     *
     * @param config the resolved configuration
     * @param httpClient the client, injected or built by {@code MailkubeClient}
     * @param observer notified once per exchange; never null, use a no-op instead
     */
    public HttpTransport(Config config, HttpClient httpClient, RequestObserver observer) {
        this.config = config;
        this.httpClient = httpClient;
        this.observer = observer;
    }

    @Override
    public Email sendEmail(RequestSpec spec) {
        // Everything about this call lives in locals. This class holds no per-request state, and
        // that is not a stylistic preference: a field here would be shared by every caller of a
        // shared client, and two of them would swap responses. `ConcurrencyTest` is the proof.
        HttpResponse<byte[]> response = roundTrip(spec);
        Map<String, Object> payload = decodeOrThrow(response);
        String id = Json.text(payload, "id");
        if (id == null) {
            throw new ApiException(
                    new ErrorEnvelope("", "expected a JSON body with an 'id'", 200, payload, null, null));
        }
        return new Email(
                id,
                Json.text(payload, "message_id"),
                "true".equalsIgnoreCase(header(response, "Idempotent-Replayed")),
                Json.text(payload, "status"),
                Json.text(payload, "scheduled_at"),
                Json.text(payload, "batch_id"));
    }

    @Override
    public <T> T request(RequestSpec spec, ResponseMapper<T> mapper) {
        HttpResponse<byte[]> response = roundTrip(spec);
        // Strict, unlike the send path's tolerant decode: a malformed 2xx body must not become an
        // empty model. See Json.decodeObjectOrThrow.
        throwIfNotOk(response);
        return mapper.map(
                Json.decodeObjectOrThrow(new String(response.body(), StandardCharsets.UTF_8), response.statusCode()));
    }

    /**
     * Decode the body, mapping any non-2xx status to the matching exception.
     *
     * <p>This is the single place a status becomes an exception, so every verb, present and
     * future, reports failures identically.
     */
    private static Map<String, Object> decodeOrThrow(HttpResponse<byte[]> response) {
        String raw = new String(response.body(), StandardCharsets.UTF_8);
        Map<String, Object> payload = Json.decodeObject(raw);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw ApiException.forStatus(envelope(response, payload));
        }
        return payload;
    }

    /**
     * Raise the mapped exception for a non-2xx response, decoding the error body tolerantly.
     *
     * <p>Tolerant on purpose: a proxy answering 502 with an HTML page still has to map by status
     * rather than raise a parse error over the top of the real failure.
     */
    private static void throwIfNotOk(HttpResponse<byte[]> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String raw = new String(response.body(), StandardCharsets.UTF_8);
            throw ApiException.forStatus(envelope(response, Json.decodeObject(raw)));
        }
    }

    private HttpResponse<byte[]> roundTrip(RequestSpec spec) {
        URI url = config.buildUrl(spec.path(), spec.query());
        HttpRequest.Builder request =
                HttpRequest.newBuilder(url).timeout(config.timeout()).method(spec.method(), bodyPublisher(spec));
        // Merged into one map and applied once, because HttpRequest.Builder.header ADDS a value
        // rather than replacing one. Applying defaults and then spec headers separately sends both
        // values for any name they share, so a per-request Content-Type never actually overrides
        // the default — it just arrives alongside it.
        mergedHeaders(spec).forEach(request::header);

        long startedAt = System.nanoTime();
        HttpResponse<byte[]> response = null;
        ConnectionException failure = null;
        try {
            // ofByteArray, not ofInputStream: see the class comment.
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return response;
        } catch (IOException e) {
            failure = new ConnectionException(e.getMessage(), e);
            throw failure;
        } catch (InterruptedException e) {
            // Restore the flag rather than swallowing it: a caller cancelling a virtual thread
            // must not have that cancellation eaten by this SDK.
            Thread.currentThread().interrupt();
            failure = new ConnectionException("interrupted while awaiting a response", e);
            throw failure;
        } finally {
            // In a `finally`, so an exchange that produced no response is reported too. That is the
            // case an observer most needs, and the one a notification on the success path loses.
            notifyObserver(spec.method(), url, response, failure, System.nanoTime() - startedAt);
        }
    }

    /**
     * Tell the observer what happened, and never let it change the answer.
     *
     * <p>An observer is application code on the path of every request. If it throws, the caller
     * still gets the response or the transport failure that actually occurred: a metrics sink with
     * a bug must not turn a delivered email into an exception.
     */
    private void notifyObserver(
            String method, URI url, HttpResponse<byte[]> response, Throwable failure, long elapsedNanos) {
        try {
            observer.onResponse(
                    method,
                    url.getPath(),
                    response == null ? null : response.statusCode(),
                    response == null ? null : header(response, "X-Request-Id"),
                    Duration.ofNanos(elapsedNanos),
                    failure);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "a RequestObserver threw and was ignored", e);
        }
    }

    /**
     * The client defaults with the per-request headers laid over them, last writer winning.
     *
     * <p>Case matters here: HTTP header names are case-insensitive but a {@code LinkedHashMap} is
     * not, so a spec header differing only in case would be added rather than overriding. Matching
     * a default's spelling is the caller's job, and the defaults use the canonical one.
     */
    private Map<String, String> mergedHeaders(RequestSpec spec) {
        Map<String, String> merged = new LinkedHashMap<>(config.defaultHeaders());
        merged.putAll(spec.headers());
        return merged;
    }

    private static HttpRequest.BodyPublisher bodyPublisher(RequestSpec spec) {
        return spec.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.encode(spec.body()), StandardCharsets.UTF_8);
    }

    private static ErrorEnvelope envelope(HttpResponse<byte[]> response, Map<String, Object> payload) {
        String name = Json.text(payload, "name");
        return new ErrorEnvelope(
                name == null ? "" : name,
                Json.text(payload, "message"),
                response.statusCode(),
                payload,
                intHeader(response, "Retry-After"),
                header(response, "X-Request-Id"));
    }

    private static String header(HttpResponse<byte[]> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static Integer intHeader(HttpResponse<byte[]> response, String name) {
        return response.headers().firstValue(name).map(Integer::valueOf).orElse(null);
    }
}
