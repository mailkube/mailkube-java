package com.mailkube.exception;

/** HTTP 422: the request was rejected by a send-policy check. */
public class InvalidRequestException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public InvalidRequestException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
