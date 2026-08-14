package com.mailkube.model;

/**
 * A recipient interaction with a delivered message.
 *
 * <p>These are the only nested keys the API sends in camelCase ({@code ipAddress},
 * {@code userAgent}); everything else in the event payload is snake_case.
 *
 * @param ipAddress the address the interaction came from
 * @param userAgent the client that made it
 * @param timestamp when it happened
 */
public record EngagementContext(String ipAddress, String userAgent, String timestamp) {}
