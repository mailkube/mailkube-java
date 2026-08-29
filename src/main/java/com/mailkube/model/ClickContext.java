package com.mailkube.model;

/**
 * A click on a tracked link: an interaction, plus what was clicked.
 *
 * <p><b>{@code ipAddress} and {@code userAgent} are deprecated</b>, for the reason given on {@link
 * EngagementContext}: the platform no longer records either, so a current server omits both keys.
 *
 * @param ipAddress the address the click came from, or {@code null} on a current server
 * @param userAgent the client that made it, or {@code null} on a current server
 * @param timestamp when it happened
 * @param link the URL that was clicked
 */
public record ClickContext(String ipAddress, String userAgent, String timestamp, String link) {}
