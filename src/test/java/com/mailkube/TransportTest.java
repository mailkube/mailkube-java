package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mailkube.exception.ApiException;
import com.mailkube.exception.ConfigurationException;
import com.mailkube.exception.MailkubeException;
import com.mailkube.exception.ServerException;
import com.mailkube.internal.Config;
import com.mailkube.internal.HttpTransport;
import com.mailkube.internal.Json;
import com.mailkube.internal.RequestSpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The transport is exercised indirectly by every other suite. What is left here is the behaviour a
 * one-verb scaffold cannot reach through {@code emails.send}, and which the next verb added to
 * this SDK will rely on.
 */
class TransportTest {

    /** These tests are about request shaping, not about who hears the outcome. */
    private static final RequestObserver SILENT = (method, path, status, requestId, elapsed, error) -> {};

    @Test
    void sendsNoBodyAtAllForABodylessRequest() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            transport.sendEmail(new RequestSpec("emails", "GET", null, Map.of(), Map.of()));

            StubServer.Request received = server.received().getFirst();
            assertEquals("GET", received.method());
            assertEquals("", received.body());
        }
    }

    @Test
    void mergesPerRequestHeadersOverTheClientDefaults() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            // Content-Type COLLIDES with a client default on purpose. HttpRequest.Builder.header
            // adds a value rather than replacing one, so applying defaults and spec headers in two
            // passes sends both and the override never happens. Asserting a single value is what
            // catches that; an assertion on a non-colliding header like X-Trace never would.
            transport.sendEmail(RequestSpec.post("emails", Map.of("a", 1))
                    .withHeader("X-Trace", "1")
                    .withHeader("Content-Type", "application/vnd.mailkube+json"));

            StubServer.Request received = server.received().getFirst();
            assertEquals("Bearer mk_test", received.header("Authorization"));
            assertEquals("1", received.header("X-Trace"));
            assertEquals(
                    List.of("application/vnd.mailkube+json"), received.headers().get("Content-type"));
        }
    }

    @Test
    void withHeaderKeepsHeadersAlreadySet() {
        RequestSpec spec = RequestSpec.post("emails", Map.of())
                .withHeader("Idempotency-Key", "key-1")
                .withHeader("X-Trace", "1");

        // Returning a fresh single-entry map here is invisible while exactly one header is ever
        // added, and silently drops the first the moment a second appears.
        assertEquals(Map.of("Idempotency-Key", "key-1", "X-Trace", "1"), spec.headers());
    }

    @Test
    void sendsFiltersAsAQueryStringAndEncodesThem() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            transport.request(RequestSpec.get("scheduled-emails", Map.of("status", "scheduled,queued")), body -> body);

            StubServer.Request received = server.received().getFirst();
            assertEquals("/scheduled-emails", received.path());
            assertEquals("status=scheduled%2Cqueued", received.query());
        }
    }

    @Test
    void mapsATypedResponseAndRefusesAMalformedSuccessBody() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"total_count\":7}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            int total = transport.request(RequestSpec.get("x"), body -> Json.integer(body, "total_count", 0));
            assertEquals(7, total);
        }

        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("<html>not json</html>"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            // A 2xx of the wrong shape is not an API error, so it must not arrive as one. Decoding
            // it leniently would turn a broken page response into an empty model instead.
            MailkubeException raised =
                    assertThrows(MailkubeException.class, () -> transport.request(RequestSpec.get("x"), body -> body));
            assertFalse(raised instanceof ApiException);
        }
    }

    @Test
    void mapsAnErrorBodyByStatusEvenOnTheTypedPath() throws IOException {
        try (StubServer server =
                        new StubServer(request -> new StubServer.Reply(502, "<html>bad gateway</html>", Map.of()));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, null, Map.of());
            HttpTransport transport = new HttpTransport(config, http, SILENT);

            // Leniency runs the other way for an ERROR body: an HTML 502 must still map by status
            // rather than raise a parse error over the top of the real failure.
            assertEquals(
                    502,
                    assertThrows(ServerException.class, () -> transport.request(RequestSpec.get("x"), body -> body))
                            .statusCode());
        }
    }

    @Test
    void readsAnAbsentOptionalFieldAsNullRatherThanEmpty() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                assertNull(client.emails().send(ClientTest.minimal().build()).messageId());
            }
        }
    }

    @Test
    void refusesALinkOnADifferentPortOfTheSameHost() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, Map.of());

        assertThrows(ConfigurationException.class, () -> config.buildUrl("https://api.example.test:8443/v1/x"));
    }

    @Test
    void refusesALinkOnADifferentScheme() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, Map.of());

        assertThrows(ConfigurationException.class, () -> config.buildUrl("http://api.example.test/v1/x"));
    }

    @Test
    void treatsAnEmptyApiKeyAsNoApiKey() {
        assertThrows(ConfigurationException.class, () -> new Config("", null, null, null, Map.of()));
    }

    @Test
    void readsTheBaseUrlFromTheEnvironmentWhenTheCallerSetsNone() {
        Config config = new Config("mk_test", null, null, null, Map.of(Config.ENV_BASE_URL, "https://env.example/v2/"));

        assertEquals("https://env.example/v2/", config.baseUrl().toString());
    }
}
