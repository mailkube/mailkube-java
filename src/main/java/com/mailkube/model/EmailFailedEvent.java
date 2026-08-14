package com.mailkube.model;

import java.util.Map;

/**
 * An accepted send was dropped at dispatch time and will never be transmitted.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param failed why it was dropped
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailFailedEvent(
        String createdAt, MessageContext message, SendFailureContext failed, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.failed";

    @Override
    public String type() {
        return TYPE;
    }
}
