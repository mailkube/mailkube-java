package com.mailkube.internal;

import com.mailkube.exception.ApiException;
import com.mailkube.exception.ConnectionException;
import com.mailkube.exception.ErrorEnvelope;
import com.mailkube.model.Email;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
public final class HttpTransport implements SendTransport {

    private final Config config;
    private final HttpClient httpClient;

    /**
     * Bind the transport to its configuration and the client it drives.
     *
     * @param config the resolved configuration
     * @param httpClient the client, injected or built by {@code MailkubeClient}
     */
    public HttpTransport(Config config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
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

    private HttpResponse<byte[]> roundTrip(RequestSpec spec) {
        HttpRequest.Builder request = HttpRequest.newBuilder(config.buildUrl(spec.path()))
                .timeout(config.timeout())
                .method(spec.method(), bodyPublisher(spec));
        config.defaultHeaders().forEach(request::header);
        spec.headers().forEach(request::header);

        try {
            // ofByteArray, not ofInputStream: see the class comment.
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (InterruptedException e) {
            // Restore the flag rather than swallowing it: a caller cancelling a virtual thread
            // must not have that cancellation eaten by this SDK.
            Thread.currentThread().interrupt();
            throw new ConnectionException("interrupted while awaiting a response", e);
        }
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
