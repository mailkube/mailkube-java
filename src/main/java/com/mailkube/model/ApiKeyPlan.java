package com.mailkube.model;

import java.util.List;

/**
 * The organization's plan, its status and its send quotas.
 *
 * @param code the plan's UUID
 * @param name the plan's name
 * @param status the subscription status, such as {@code active}; a plain string rather than an
 *     enum, for the same reason as {@link ApiKeyQuota#name()}
 * @param quotas the send quotas the plan carries. A plan without a daily limit lists the monthly
 *     quota only: an absent quota is a missing limit, never a limit of zero
 */
public record ApiKeyPlan(String code, String name, String status, List<ApiKeyQuota> quotas) {

    /** Freeze the quota list so a model handed to several threads cannot be mutated underneath them. */
    public ApiKeyPlan {
        quotas = quotas == null ? List.of() : List.copyOf(quotas);
    }
}
