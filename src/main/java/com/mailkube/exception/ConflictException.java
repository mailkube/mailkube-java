package com.mailkube.exception;

/** HTTP 409: an idempotency conflict. The same key was reused with a different payload. */
public class ConflictException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public ConflictException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
