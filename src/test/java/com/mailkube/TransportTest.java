package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mailkube.exception.ConfigurationException;
import com.mailkube.internal.Config;
import com.mailkube.internal.HttpTransport;
import com.mailkube.internal.RequestSpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The transport is exercised indirectly by every other suite. What is left here is the behaviour a
 * one-verb scaffold cannot reach through {@code emails.send}, and which the next verb added to
 * this SDK will rely on.
 */
class TransportTest {

    @Test
    void sendsNoBodyAtAllForABodylessRequest() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, Map.of());
            HttpTransport transport = new HttpTransport(config, http);

            transport.sendEmail(new RequestSpec("emails", "GET", null, Map.of()));

            StubServer.Request received = server.received().getFirst();
            assertEquals("GET", received.method());
            assertEquals("", received.body());
        }
    }

    @Test
    void mergesPerRequestHeadersOverTheClientDefaults() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"));
                HttpClient http = HttpClient.newHttpClient()) {
            Config config = new Config("mk_test", server.baseUrl(), null, Map.of());
            HttpTransport transport = new HttpTransport(config, http);

            transport.sendEmail(RequestSpec.post("emails", Map.of("a", 1)).withHeader("X-Trace", "1"));

            StubServer.Request received = server.received().getFirst();
            assertEquals("Bearer mk_test", received.header("Authorization"));
            assertEquals("1", received.header("X-Trace"));
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
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, Map.of());

        assertThrows(ConfigurationException.class, () -> config.buildUrl("https://api.example.test:8443/v1/x"));
    }

    @Test
    void refusesALinkOnADifferentScheme() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, Map.of());

        assertThrows(ConfigurationException.class, () -> config.buildUrl("http://api.example.test/v1/x"));
    }

    @Test
    void treatsAnEmptyApiKeyAsNoApiKey() {
        assertThrows(ConfigurationException.class, () -> new Config("", null, null, Map.of()));
    }

    @Test
    void readsTheBaseUrlFromTheEnvironmentWhenTheCallerSetsNone() {
        Config config = new Config("mk_test", null, null, Map.of(Config.ENV_BASE_URL, "https://env.example/v2/"));

        assertEquals("https://env.example/v2/", config.baseUrl().toString());
    }
}
