package com.mailkube.model;

import java.util.List;

/**
 * The message fields every {@code email.*} event carries.
 *
 * <p>{@code domain}, {@code subject}, {@code to} and {@code from} are always sent as keys but their
 * values may be null: the server resolves them through the sending transaction, which a
 * per-recipient event can briefly outlive.
 *
 * @param emailId the message's UUID, the same id the send acknowledgement returned
 * @param createdAt when the message was accepted
 * @param domain the sending domain, or null
 * @param subject the message subject, or null
 * @param to the recipients, or null
 * @param from the sender, or null
 * @param tags the tags attached at send time; empty from a server predating message tags
 */
public record MessageContext(
        String emailId, String createdAt, String domain, String subject, List<String> to, String from, List<Tag> tags) {

    /** Freeze the lists so an event handed to several threads cannot be mutated underneath them. */
    public MessageContext {
        to = to == null ? null : List.copyOf(to);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
