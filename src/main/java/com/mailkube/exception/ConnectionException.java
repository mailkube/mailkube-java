package com.mailkube.exception;

/** Thrown on a transport-level failure with no HTTP response: DNS, TCP, TLS, timeout or interrupt. */
public class ConnectionException extends MailkubeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param message the detail message
     * @param cause the underlying I/O failure
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
