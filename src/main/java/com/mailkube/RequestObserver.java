package com.mailkube;

import java.time.Duration;

/**
 * Called once per HTTP exchange, after it has finished, however it finished.
 *
 * <p>This is the metrics and logging seam. It is <b>observe-only</b>: it is handed what happened
 * and can do nothing about it. An observer that could alter a request would be a second header
 * contributor with no defined precedence against the client defaults and the per-request headers,
 * and this SDK already has exactly one place those are merged.
 *
 * <pre>{@code
 * var client = MailkubeClient.builder()
 *     .observer((method, path, status, requestId, elapsed, error) ->
 *         metrics.timer("mailkube", "path", path, "status", String.valueOf(status)).record(elapsed))
 *     .build();
 * }</pre>
 *
 * <h2>What it is never given</h2>
 *
 * <p>No request body, no response body, no headers. A send body carries recipient addresses,
 * subject lines, rendered HTML and template variables, and there is no redaction rule that makes
 * those safe in an application log. Because the signature carries none of them, that property holds
 * by type rather than by discipline, and it is also why this SDK ships no redaction helper: nothing
 * it could redact ever reaches an observer.
 *
 * <p>This signature may gain outcome or correlation parameters in a later release. It will never
 * gain a header map or a body.
 *
 * <h2>Contract</h2>
 *
 * <p>Implementations must be thread-safe: one client is shared across threads, and the notification
 * happens on whichever thread made the call. Keep the work cheap, or hand it to your own executor.
 * An observer that throws does not fail the request; the SDK reports the failure and carries on.
 */
@FunctionalInterface
public interface RequestObserver {

    /**
     * Report one finished exchange.
     *
     * @param method the HTTP method
     * @param path the request path, without the query string
     * @param status the response status, or null when no response was received
     * @param requestId the {@code X-Request-Id} the API returned, or null if there was none
     * @param elapsed how long the exchange took, including reading the body
     * @param error the transport failure, or null when a response was received. A 4xx or 5xx is a
     *     response, so it arrives with a status and no error
     */
    void onResponse(String method, String path, Integer status, String requestId, Duration elapsed, Throwable error);
}
