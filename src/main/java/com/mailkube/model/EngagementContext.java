package com.mailkube.model;

/**
 * A recipient interaction with a delivered message.
 *
 * <p>These are the only nested keys the API sends in camelCase ({@code ipAddress},
 * {@code userAgent}); everything else in the event payload is snake_case. {@code country} is
 * lowercase in both conventions and needs no rename.
 *
 * <p><b>{@code ipAddress}, {@code country} and {@code userAgent} are elected by the sending
 * domain</b> and are {@code null} whenever it has not elected them, which is the default. The
 * server omits the key entirely rather than sending an empty value, so {@code null} here means the
 * sender did not record it and never that the sender recorded a blank. {@code country} can be
 * {@code null} even where the address was recorded: it is resolved at the edge and is not
 * available on every path.
 *
 * @param ipAddress the address the interaction came from, or {@code null} where not elected
 * @param userAgent the client that made it, or {@code null} where not elected
 * @param timestamp when it happened
 * @param country the two-letter country the address resolves to, or {@code null}
 */
public record EngagementContext(String ipAddress, String userAgent, String timestamp, String country) {}
