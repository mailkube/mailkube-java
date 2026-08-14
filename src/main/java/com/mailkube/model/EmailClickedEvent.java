package com.mailkube.model;

import java.util.Map;

/**
 * A recipient clicked a tracked link.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param click the interaction, and the link
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailClickedEvent(String createdAt, MessageContext message, ClickContext click, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.clicked";

    @Override
    public String type() {
        return TYPE;
    }
}
