package com.mailkube.exception;

import java.util.Map;

/**
 * The API's error body, plus the response metadata a caller needs to act on it.
 *
 * <p>This exists so there is exactly one constructor signature across the whole error hierarchy.
 * Eight subtypes each repeating six parameters is how a duplication gate starts failing, and how
 * two categories start reporting different fields.
 *
 * @param name the machine-readable name from the envelope, e.g. {@code quota_exceeded}, or empty
 * @param message the human-readable message, or null
 * @param statusCode the HTTP status code
 * @param body the decoded response body, or an empty map
 * @param retryAfter the Retry-After header in seconds, or null when the server sent none
 * @param requestId the server's request id, to quote to support, or null
 */
public record ErrorEnvelope(
        String name, String message, int statusCode, Map<String, Object> body, Integer retryAfter, String requestId) {}
