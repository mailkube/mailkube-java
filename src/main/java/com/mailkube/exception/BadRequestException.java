package com.mailkube.exception;

/** HTTP 400: the request envelope was invalid. */
public class BadRequestException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public BadRequestException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
