package com.mailkube.model;

/**
 * The prior state in a webhook endpoint status change.
 *
 * @param isActive whether the endpoint was active
 * @param isDeleted whether the endpoint was deleted
 * @param disabledReason why it was disabled, or empty
 */
public record WebhookStatusPrevious(boolean isActive, boolean isDeleted, String disabledReason) {}
