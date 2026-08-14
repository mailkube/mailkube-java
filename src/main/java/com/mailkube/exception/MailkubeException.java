package com.mailkube.exception;

/**
 * The supertype of every exception this SDK raises.
 *
 * <p>Catch this to handle anything the SDK can throw, or a subtype to branch on the category.
 * These are unchecked on purpose: a checked exception would force every caller of every verb to
 * write a {@code try} block for failures most applications handle once, at the edge.
 */
public class MailkubeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create an exception with a message.
     *
     * @param message the detail message
     */
    public MailkubeException(String message) {
        super(message);
    }

    /**
     * Create an exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    public MailkubeException(String message, Throwable cause) {
        super(message, cause);
    }
}
