package com.mailkube.model;

import java.util.Map;

/**
 * A sending domain's status or onboarding state changed.
 *
 * <p>Account-level rather than message-level, so it carries no {@code MessageContext}.
 *
 * @param createdAt when the event was raised
 * @param domain the domain whose state changed
 * @param status the new status
 * @param onboardingState the new onboarding state
 * @param previous the state before the change
 * @param raw the whole decoded payload, so a field this release predates is never lost
 */
public record DomainStatusEvent(
        String createdAt,
        String domain,
        String status,
        String onboardingState,
        DomainStatusPrevious previous,
        Map<String, Object> raw)
        implements WebhookEvent {

    /** The {@code type} this event arrives as. */
    public static final String TYPE = "domain.status";

    @Override
    public String type() {
        return TYPE;
    }
}
