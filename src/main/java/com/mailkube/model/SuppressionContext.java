package com.mailkube.model;

import java.util.List;

/**
 * The recipients a send was suppressed for.
 *
 * @param recipients the suppressed addresses
 * @param timestamp when the suppression was applied
 */
public record SuppressionContext(List<String> recipients, String timestamp) {

    /** Freeze the list so an event handed to several threads cannot be mutated underneath them. */
    public SuppressionContext {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }
}
