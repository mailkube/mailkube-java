package com.mailkube.exception;

/** Thrown when no API key is available, or the configuration is otherwise unusable. */
public class ConfigurationException extends MailkubeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create the exception.
     *
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }
}
