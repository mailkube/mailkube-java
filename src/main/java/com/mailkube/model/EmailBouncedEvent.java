package com.mailkube.model;

import java.util.Map;

/**
 * A message permanently failed to deliver.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param bounce what the receiving server said
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailBouncedEvent(
        String createdAt, MessageContext message, FailureContext bounce, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.bounced";

    @Override
    public String type() {
        return TYPE;
    }
}
