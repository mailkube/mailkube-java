package com.mailkube.model;

import java.util.Map;

/**
 * A message was suppressed, through a prior hard bounce or a topic opt-out.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param suppression who was suppressed
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailSuppressedEvent(
        String createdAt, MessageContext message, SuppressionContext suppression, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.suppressed";

    @Override
    public String type() {
        return TYPE;
    }
}
