package com.mailkube.model;

/**
 * An accepted send dropped at dispatch time, before transmission.
 *
 * <p>Distinct from {@link FailureContext}, which reports what a receiving mail server said about
 * one recipient. This is message-level and names no recipient: the send never left.
 *
 * @param reason a stable server-side code such as {@code suppressed_at_dispatch}; a plain string,
 *     so a newly added reason never breaks parsing
 * @param timestamp when the send was dropped
 */
public record SendFailureContext(String reason, String timestamp) {}
