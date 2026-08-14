package com.mailkube.model;

import java.util.Map;

/**
 * A message was accepted by the receiving mail server.
 *
 * @param createdAt when the event was raised
 * @param message the message the event is about
 * @param delivery the delivery outcome
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record EmailDeliveredEvent(
        String createdAt, MessageContext message, DeliveryContext delivery, Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "email.delivered";

    @Override
    public String type() {
        return TYPE;
    }
}
