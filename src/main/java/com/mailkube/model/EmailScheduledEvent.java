package com.mailkube.model;

import java.util.Map;

/**
 * A send was accepted for later transmission.
 *
 * <p>{@code message.emailId()} correlates this to the {@link EmailSentEvent} raised when the send
 * is eventually transmitted.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param scheduled when it is due
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailScheduledEvent(
        String createdAt, MessageContext message, ScheduledContext scheduled, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.scheduled";

    @Override
    public String type() {
        return TYPE;
    }
}
