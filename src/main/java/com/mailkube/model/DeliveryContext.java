package com.mailkube.model;

/**
 * A single-recipient delivery outcome.
 *
 * @param recipient the address the outcome is about
 * @param timestamp when it happened
 */
public record DeliveryContext(String recipient, String timestamp) {}
