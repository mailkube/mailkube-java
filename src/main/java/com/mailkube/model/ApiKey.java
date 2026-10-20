package com.mailkube.model;

import java.util.List;

/**
 * The API key the client authenticates with, as it describes itself.
 *
 * <p>Timestamps are the server's own strings, verbatim; call {@link java.time.Instant#parse}
 * yourself if you want an object. Plan and quota figures can be up to 30 seconds old.
 *
 * @param object the resource discriminator, always {@code "api_key"}
 * @param keyId the key's public identifier
 * @param name the key's name
 * @param description the key's description, or null when it has none
 * @param createdAt when the key was created
 * @param lastAuthAt the key's last successful authentication, this call included
 * @param allowedSourceIps the networks the key may be used from, in CIDR notation; empty means any
 *     address
 * @param domain the sending domain the key belongs to
 * @param organization the organization owning the domain
 * @param plan the organization's plan, or null when it has no subscription
 */
public record ApiKey(
        String object,
        String keyId,
        String name,
        String description,
        String createdAt,
        String lastAuthAt,
        List<String> allowedSourceIps,
        ApiKeyDomain domain,
        ApiKeyOrganization organization,
        ApiKeyPlan plan) {

    /** Freeze the allowlist so a model handed to several threads cannot be mutated underneath them. */
    public ApiKey {
        allowedSourceIps = allowedSourceIps == null ? List.of() : List.copyOf(allowedSourceIps);
    }
}
