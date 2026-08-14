package com.mailkube.exception;

/** HTTP 5xx: an unexpected server error. Safe to retry with backoff. */
public class ServerException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public ServerException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
