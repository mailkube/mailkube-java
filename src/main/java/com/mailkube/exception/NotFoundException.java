package com.mailkube.exception;

/** HTTP 404: a referenced resource was not found. */
public class NotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public NotFoundException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
