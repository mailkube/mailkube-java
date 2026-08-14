package com.mailkube.model;

/**
 * A delivery failure, with what the receiving mail server said about it.
 *
 * <p>Flat rather than a delivery outcome plus a failure block, because that is how the wire sends
 * it. A model mirrors the wire and nothing else.
 *
 * @param recipient the address the failure is about
 * @param timestamp when it happened
 * @param code the SMTP status code the receiving server returned
 * @param reason the server's reason text; a plain string, so a new one never breaks parsing
 */
public record FailureContext(String recipient, String timestamp, int code, String reason) {}
