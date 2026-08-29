package com.mailkube.model;

/**
 * A click on a tracked link: an interaction, plus what was clicked.
 *
 * <p><b>{@code ipAddress}, {@code country} and {@code userAgent} are elected by the sending
 * domain</b>, for the reason given on {@link EngagementContext}: they are {@code null} wherever the
 * sender has not turned the corresponding per-domain setting on, which is the default.
 *
 * @param ipAddress the address the click came from, or {@code null} where not elected
 * @param userAgent the client that made it, or {@code null} where not elected
 * @param timestamp when it happened
 * @param link the URL that was clicked
 * @param country the two-letter country the address resolves to, or {@code null}
 */
public record ClickContext(String ipAddress, String userAgent, String timestamp, String link, String country) {}
