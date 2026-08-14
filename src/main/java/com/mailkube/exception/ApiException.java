package com.mailkube.exception;

import java.util.Map;

/**
 * Thrown for any non-2xx response, carrying the API's error envelope.
 *
 * <p>Catch a subtype to branch on the category, and read the accessors for the detail. The
 * envelope's {@code name} stays a plain string, so a value this release has never heard of is
 * reported verbatim rather than coerced into an enum that would have to be updated in lockstep
 * with the server.
 */
public class ApiException extends MailkubeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorEnvelope envelope;

    /**
     * Create the exception from an envelope.
     *
     * <p>There is deliberately one constructor for the whole hierarchy, and no subtype overrides
     * it.
     *
     * @param envelope the API's error body and response metadata
     */
    public ApiException(ErrorEnvelope envelope) {
        super(describe(envelope));
        this.envelope = envelope;
    }

    /**
     * Build the right exception for a response status.
     *
     * <p>This is the single status-to-class table, and the only place a status becomes a type.
     *
     * @param envelope the API's error body and response metadata
     * @return the exception to throw
     */
    public static ApiException forStatus(ErrorEnvelope envelope) {
        return switch (envelope.statusCode()) {
            case 400 -> new BadRequestException(envelope);
            case 403 -> new AuthenticationException(envelope);
            case 404 -> new NotFoundException(envelope);
            case 409 -> new ConflictException(envelope);
            case 422 -> new InvalidRequestException(envelope);
            case 429 -> new RateLimitException(envelope);
            default -> envelope.statusCode() >= 500 ? new ServerException(envelope) : new ApiException(envelope);
        };
    }

    /**
     * The machine-readable name from the envelope, e.g. {@code quota_exceeded}.
     *
     * @return the error name, or an empty string when the server sent none
     */
    public String errorName() {
        return envelope.name();
    }

    /**
     * The HTTP status code.
     *
     * @return the status code
     */
    public int statusCode() {
        return envelope.statusCode();
    }

    /**
     * The decoded response body.
     *
     * @return the body, or an empty map
     */
    public Map<String, Object> body() {
        return envelope.body();
    }

    /**
     * The Retry-After header in seconds.
     *
     * @return the value, or null when the server sent none
     */
    public Integer retryAfter() {
        return envelope.retryAfter();
    }

    /**
     * The server's request id, to quote to support.
     *
     * @return the request id, or null
     */
    public String requestId() {
        return envelope.requestId();
    }

    private static String describe(ErrorEnvelope envelope) {
        if (envelope.message() != null && !envelope.message().isEmpty()) {
            return envelope.message();
        }
        return envelope.name().isEmpty() ? "HTTP " + envelope.statusCode() : envelope.name();
    }
}
