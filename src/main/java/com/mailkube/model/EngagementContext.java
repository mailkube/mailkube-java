package com.mailkube.model;

/**
 * A recipient interaction with a delivered message.
 *
 * <p>These are the only nested keys the API sends in camelCase ({@code ipAddress},
 * {@code userAgent}); everything else in the event payload is snake_case.
 *
 * <p><b>{@code ipAddress} and {@code userAgent} are deprecated.</b> The platform no longer records
 * either, so a current server omits both keys and they arrive as {@code null}. They are kept as
 * components rather than removed so that code written against an earlier version still compiles,
 * and so an event replayed from an archive still parses.
 *
 * @param ipAddress the address the interaction came from, or {@code null} on a current server
 * @param userAgent the client that made it, or {@code null} on a current server
 * @param timestamp when it happened
 */
public record EngagementContext(String ipAddress, String userAgent, String timestamp) {}
