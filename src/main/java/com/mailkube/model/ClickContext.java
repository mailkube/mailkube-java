package com.mailkube.model;

/**
 * A click on a tracked link: an interaction, plus what was clicked.
 *
 * @param ipAddress the address the click came from
 * @param userAgent the client that made it
 * @param timestamp when it happened
 * @param link the URL that was clicked
 */
public record ClickContext(String ipAddress, String userAgent, String timestamp, String link) {}
