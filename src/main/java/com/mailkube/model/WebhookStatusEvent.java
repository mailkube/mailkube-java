package com.mailkube.model;

import java.util.Map;

/**
 * A webhook endpoint's status changed.
 *
 * <p>Delivered to the endpoints still active. An endpoint the platform has just disabled will not
 * receive the event announcing it, which is why {@code disabledReason} also appears on the endpoint
 * itself.
 *
 * @param createdAt when the event was raised
 * @param endpointUrl the endpoint whose state changed
 * @param isActive whether it is now active
 * @param isDeleted whether it is now deleted
 * @param disabledReason why it was disabled, or empty
 * @param previous the state before the change
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record WebhookStatusEvent(
        String createdAt,
        String endpointUrl,
        boolean isActive,
        boolean isDeleted,
        String disabledReason,
        WebhookStatusPrevious previous,
        Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "webhook.status";

    @Override
    public String type() {
        return TYPE;
    }
}
