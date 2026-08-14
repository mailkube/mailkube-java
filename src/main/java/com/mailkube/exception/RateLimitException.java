package com.mailkube.exception;

/** HTTP 429: the rate limit was exceeded. Read {@link #retryAfter()} before retrying. */
public class RateLimitException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public RateLimitException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
