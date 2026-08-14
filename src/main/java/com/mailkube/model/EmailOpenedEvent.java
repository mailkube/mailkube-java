package com.mailkube.model;

import java.util.Map;

/**
 * A recipient opened a message.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param open the interaction
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailOpenedEvent(
        String createdAt, MessageContext message, EngagementContext open, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.opened";

    @Override
    public String type() {
        return TYPE;
    }
}
