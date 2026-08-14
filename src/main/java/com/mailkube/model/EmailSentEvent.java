package com.mailkube.model;

import java.util.Map;

/**
 * A message was accepted and spooled by the sending infrastructure.
 *
 * <p>The moment of acceptance, not a delivery outcome: {@link EmailDeliveredEvent} reports whether
 * the receiving mail server took it.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param sent the acceptance
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailSentEvent(String createdAt, MessageContext message, DeliveryContext sent, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.sent";

    @Override
    public String type() {
        return TYPE;
    }
}
