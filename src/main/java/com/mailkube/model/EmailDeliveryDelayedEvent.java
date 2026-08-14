package com.mailkube.model;

import java.util.Map;

/**
 * A message was temporarily deferred and will be retried.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param delay what the receiving server said
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailDeliveryDelayedEvent(
        String createdAt, MessageContext message, FailureContext delay, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.delivery_delayed";

    @Override
    public String type() {
        return TYPE;
    }
}
