package com.mailkube.exception;

/** HTTP 403: authentication failed, or the key is forbidden from this action. */
public class AuthenticationException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param envelope the API's error body and response metadata
     */
    public AuthenticationException(ErrorEnvelope envelope) {
        super(envelope);
    }
}
