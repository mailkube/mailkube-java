package com.mailkube;

import com.mailkube.internal.CredentialTransport;
import com.mailkube.internal.Json;
import com.mailkube.internal.RequestSpec;
import com.mailkube.model.ApiKey;
import com.mailkube.model.ApiKeyDomain;
import com.mailkube.model.ApiKeyOrganization;
import com.mailkube.model.ApiKeyPlan;
import com.mailkube.model.ApiKeyQuota;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code credential} namespace, reached as {@code client.credential()}: the API key this client
 * authenticates with.
 *
 * <p>It holds no setter. A client takes its key when it is built and keeps it, which is what makes
 * one client safe to share across threads; a second key is a second client.
 *
 * <p>The wire-to-model mapping lives here rather than on the records, for the reason given on
 * {@link ScheduledEmails}: a {@code from(Map)} factory on an exported record would make the types
 * layer depend on the internal JSON codec.
 */
public final class Credential {

    private static final String INFO_PATH = "api-keys/me";

    private final CredentialTransport transport;

    /**
     * Bind the resource to the transport that performs its requests.
     *
     * @param transport the transport, or a test double satisfying the interface
     */
    Credential(CredentialTransport transport) {
        this.transport = transport;
    }

    /**
     * Describe the API key this client authenticates with: its domain, organization, source-IP
     * allowlist, plan and send-quota usage.
     *
     * <p>The key is checked exactly as a send checks it, so a key that cannot send raises an
     * {@link com.mailkube.exception.AuthenticationException} ({@code invalid_api_key}), which also
     * makes this the way to verify a key. The call counts as a use of the key. Plan and quota
     * figures can be up to 30 seconds old.
     *
     * @return the calling API key
     */
    public ApiKey info() {
        return transport.request(RequestSpec.get(INFO_PATH), Credential::toApiKey);
    }

    private static ApiKey toApiKey(Map<String, Object> body) {
        Map<String, Object> domain = Json.object(body, "domain");
        Map<String, Object> organization = Json.object(body, "organization");
        return new ApiKey(
                Json.text(body, "object"),
                Json.text(body, "key_id"),
                Json.text(body, "name"),
                Json.text(body, "description"),
                Json.text(body, "created_at"),
                Json.text(body, "last_auth_at"),
                Json.strings(body, "allowed_source_ips"),
                new ApiKeyDomain(Json.text(domain, "name")),
                new ApiKeyOrganization(Json.text(organization, "name"), Json.text(organization, "alias")),
                // The one field the API sends as null, meaning "no subscription". Json.object reads
                // null as an empty map, which would build a plan of nulls that looks like a real one.
                body.get("plan") == null ? null : toPlan(Json.object(body, "plan")));
    }

    private static ApiKeyPlan toPlan(Map<String, Object> plan) {
        List<ApiKeyQuota> quotas = new ArrayList<>();
        for (Map<String, Object> quota : Json.objects(plan, "quotas")) {
            quotas.add(new ApiKeyQuota(
                    Json.text(quota, "name"),
                    Json.integer(quota, "limit", 0),
                    Json.integer(quota, "used", 0),
                    Json.integer(quota, "remaining", 0),
                    Json.text(quota, "period")));
        }
        return new ApiKeyPlan(Json.text(plan, "code"), Json.text(plan, "name"), Json.text(plan, "status"), quotas);
    }
}
