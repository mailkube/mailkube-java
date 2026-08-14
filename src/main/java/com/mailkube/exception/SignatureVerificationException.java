package com.mailkube.exception;

/** Thrown when a webhook signature or its timestamp cannot be verified. */
public class SignatureVerificationException extends MailkubeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param message the detail message
     */
    public SignatureVerificationException(String message) {
        super(message);
    }
}
