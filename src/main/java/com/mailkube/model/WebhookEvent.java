package com.mailkube.model;

import java.util.Map;

/**
 * An inbound webhook event, parsed into its concrete type.
 *
 * <p>The interface is <b>sealed</b>, which is what makes the catalogue checkable rather than
 * merely documented: a {@code switch} over an event with no {@code default} arm is a compile error
 * until it handles every permitted type, so an event added here cannot be silently dropped by code
 * that thought it handled everything.
 *
 * <pre>{@code
 * WebhookEvent event = Webhooks.parseEvent(Webhooks.verifySignature(raw, headers, secret));
 * switch (event) {
 *     case EmailBouncedEvent e -> suppress(e.message().to(), e.bounce().reason());
 *     case EmailClickedEvent e -> record(e.message().emailId(), e.click().link());
 *     default -> {  }
 * }
 * }</pre>
 *
 * <p>Type strings stay {@code String} and never become an enum. A {@code type} this release has
 * never heard of parses as {@link UnknownEvent} rather than raising, so the platform can introduce
 * an event without forcing an upgrade on every receiver.
 */
public sealed interface WebhookEvent
        permits EmailSentEvent,
                EmailDeliveredEvent,
                EmailBouncedEvent,
                EmailDeliveryDelayedEvent,
                EmailSuppressedEvent,
                EmailScheduledEvent,
                EmailFailedEvent,
                EmailOpenedEvent,
                EmailClickedEvent,
                DomainStatusEvent,
                WebhookStatusEvent,
                UnknownEvent {

    /**
     * The event type, as sent.
     *
     * @return the type, e.g. {@code "email.delivered"}
     */
    String type();

    /**
     * When the event was raised.
     *
     * @return the verbatim ISO-8601 string the server sent
     */
    String createdAt();

    /**
     * The whole decoded payload, exactly as it arrived.
     *
     * <p>On the interface rather than on each record, so no event type can forget it. This is how
     * the SDK keeps the contract's promise that unknown fields are preserved rather than dropped:
     * a field added to an event after this release still reaches you here, even though no typed
     * accessor for it exists yet.
     *
     * @return the decoded body, unmodifiable
     */
    Map<String, Object> raw();
}
