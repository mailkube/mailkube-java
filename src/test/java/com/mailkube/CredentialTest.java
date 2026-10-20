package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mailkube.exception.ApiException;
import com.mailkube.exception.AuthenticationException;
import com.mailkube.exception.ErrorName;
import com.mailkube.exception.MailkubeException;
import com.mailkube.exception.RateLimitException;
import com.mailkube.model.ApiKey;
import com.mailkube.model.ApiKeyDomain;
import com.mailkube.model.ApiKeyOrganization;
import com.mailkube.model.ApiKeyPlan;
import com.mailkube.model.ApiKeyQuota;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The credential namespace: the calling key describing itself, over the real transport. */
class CredentialTest {

    private static final String MONTHLY =
            "{\"name\":\"emails_per_month\",\"limit\":50000,\"used\":9310,\"remaining\":40690,\"period\":\"2026-10\"}";

    private static final String FULL_PLAN = ",\"plan\":{\"code\":\"00000000-0000-0000-0000-000000000003\","
            + "\"name\":\"Momentum\",\"status\":\"active\",\"quotas\":[{\"name\":\"emails_per_day\",\"limit\":5000,"
            + "\"used\":120,\"remaining\":4880,\"period\":\"2026-10-19\"}," + MONTHLY + "]}";

    /** The calling key's description, with the given plan JSON spliced in. */
    private static String key(String plan) {
        return "{\"object\":\"api_key\",\"key_id\":\"k3v9x2m8q1w7r4t6y0p5s2a8\",\"name\":\"production-backend\","
                + "\"description\":null,\"created_at\":\"2026-09-02T08:14:03Z\","
                + "\"last_auth_at\":\"2026-10-19T16:41:22Z\",\"allowed_source_ips\":[\"203.0.113.0/24\"],"
                + "\"domain\":{\"name\":\"example.com\"},\"organization\":{\"name\":\"Example Ltd\",\"alias\":\"example\"}"
                + plan + "}";
    }

    private static ApiKey info(StubServer server) {
        try (MailkubeClient client = ClientTest.client(server)) {
            return client.credential().info();
        }
    }

    @Test
    void getsTheCallingKeyWithNoBodyAndNoIdempotencyKey() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok(key(FULL_PLAN)))) {
            info(server);

            StubServer.Request request = server.received().getFirst();
            assertEquals("GET", request.method());
            assertEquals("/api-keys/me", request.path());
            assertEquals("", request.body());
            assertNull(request.header("Idempotency-Key"));
        }
    }

    @Test
    void decodesTheKeyItsDomainOrganizationAndPlan() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok(key(FULL_PLAN)))) {
            ApiKey expected = new ApiKey(
                    "api_key",
                    "k3v9x2m8q1w7r4t6y0p5s2a8",
                    "production-backend",
                    null,
                    "2026-09-02T08:14:03Z",
                    "2026-10-19T16:41:22Z",
                    List.of("203.0.113.0/24"),
                    new ApiKeyDomain("example.com"),
                    new ApiKeyOrganization("Example Ltd", "example"),
                    new ApiKeyPlan(
                            "00000000-0000-0000-0000-000000000003",
                            "Momentum",
                            "active",
                            List.of(
                                    new ApiKeyQuota("emails_per_day", 5000, 120, 4880, "2026-10-19"),
                                    new ApiKeyQuota("emails_per_month", 50000, 9310, 40690, "2026-10"))));

            assertEquals(expected, info(server));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {",\"plan\":null", ""})
    void readsANullOrAbsentPlanAsNoPlan(String plan) throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok(key(plan)))) {
            assertNull(info(server).plan());
        }
    }

    @Test
    void keepsAMonthlyOnlyPlanMonthlyOnly() throws IOException {
        String plan = ",\"plan\":{\"code\":\"c\",\"name\":\"Free\",\"status\":\"active\",\"quotas\":[" + MONTHLY + "]}";
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok(key(plan)))) {
            List<ApiKeyQuota> quotas = info(server).plan().quotas();

            assertEquals(
                    List.of("emails_per_month"),
                    quotas.stream().map(ApiKeyQuota::name).toList());
        }
    }

    @Test
    void mapsARefusedKeyToAnAuthenticationException() throws IOException {
        String body = "{\"name\":\"invalid_api_key\",\"message\":\"API key is invalid\"}";
        try (StubServer server = new StubServer(request -> new StubServer.Reply(403, body, Map.of()))) {
            AuthenticationException refused = assertThrows(AuthenticationException.class, () -> info(server));

            assertEquals(ErrorName.INVALID_API_KEY, refused.errorName());
        }
    }

    @Test
    void carriesRetryAfterOnARateLimit() throws IOException {
        String body = "{\"name\":\"rate_limit_exceeded\",\"message\":\"slow down\"}";
        try (StubServer server =
                new StubServer(request -> new StubServer.Reply(429, body, Map.of("Retry-After", "59")))) {
            RateLimitException limited = assertThrows(RateLimitException.class, () -> info(server));

            assertEquals(59, limited.retryAfter());
        }
    }

    @Test
    void refusesANonObjectSuccessBodyAsAnSdkError() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("[]"))) {
            MailkubeException failure = assertThrows(MailkubeException.class, () -> info(server));

            assertFalse(failure instanceof ApiException);
        }
    }
}
