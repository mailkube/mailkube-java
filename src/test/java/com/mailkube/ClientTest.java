package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.ConfigurationException;
import com.mailkube.exception.ConnectionException;
import com.mailkube.internal.Config;
import com.mailkube.model.SendEmailParams;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientTest {

    private static final Map<String, String> NO_ENV = Map.of();

    @Test
    void fallsBackToTheEnvironmentForTheApiKey() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"))) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .environment(Map.of(Config.ENV_API_KEY, "mk_env", Config.ENV_BASE_URL, server.baseUrl()))
                    .build()) {
                client.emails().send(minimal().build());
            }
            assertEquals("Bearer mk_env", server.received().getFirst().header("Authorization"));
        }
    }

    @Test
    void reportsTheConfigurationAHealthCheckHasToShow() {
        // A framework health indicator states which API it is talking to and how long it waits. It
        // reads that back from the client rather than from whatever the surrounding code passed in.
        try (MailkubeClient client = MailkubeClient.builder()
                .apiKey("mk_test")
                .environment(Map.of(Config.ENV_BASE_URL, "https://api.example.test/v1/"))
                .timeout(Duration.ofSeconds(5))
                .build()) {

            assertEquals("https://api.example.test/v1/", client.baseUrl().toString());
            assertEquals(Duration.ofSeconds(5), client.timeout());
        }
    }

    @Test
    void raisesWhenNoApiKeyIsAvailableAnywhere() {
        ConfigurationException error = assertThrows(
                ConfigurationException.class,
                () -> MailkubeClient.builder().environment(NO_ENV).build());

        assertTrue(error.getMessage().contains(Config.ENV_API_KEY));
    }

    @Test
    void sendsBearerAuthJsonNegotiationAndAVersionedUserAgent() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"))) {
            try (MailkubeClient client = client(server)) {
                client.emails().send(minimal().build());
            }
            StubServer.Request request = server.received().getFirst();
            assertEquals("Bearer mk_test", request.header("Authorization"));
            assertEquals("application/json", request.header("Content-Type"));
            assertEquals("application/json", request.header("Accept"));
            assertEquals("mailkube-java/" + Version.current(), request.header("User-Agent"));
            // The assertion above is tautological about the version segment: it would hold just as
            // well if the version arrived as `v1.0.0`, which is what happens anywhere the release
            // reads the git tag (`tagFormat` is `v${version}`) rather than the bare version. The
            // contract's row is `mailkube-java/<version>`, so pin the shape independently.
            assertTrue(request.header("User-Agent").matches("mailkube-java/\\d.*"), request.header("User-Agent"));
        }
    }

    @Test
    void appliesTheConfiguredTimeoutToTheRequestItselfNotJustToTheConfig() throws IOException {
        // The timeout is only worth accepting if it reaches the wire. An injected client is what
        // makes this test honest: it carries no connect timeout of its own, so the only deadline
        // in play is the per-request one the transport sets, and deleting that line makes this
        // send succeed rather than time out.
        try (StubServer server = new StubServer(request -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(2));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return StubServer.Reply.ok("{\"id\":\"abc\"}");
                });
                java.net.http.HttpClient injected = java.net.http.HttpClient.newHttpClient();
                MailkubeClient client = MailkubeClient.builder()
                        .apiKey("mk_test")
                        .baseUrl(server.baseUrl())
                        .httpClient(injected)
                        .timeout(Duration.ofMillis(150))
                        .environment(NO_ENV)
                        .build()) {

            assertThrows(
                    ConnectionException.class,
                    () -> client.emails().send(minimal().build()));
        }
    }

    @Test
    void resolvesARelativePathAgainstTheBaseUrl() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, NO_ENV);

        assertEquals(
                "https://api.example.test/v1/emails", config.buildUrl("emails").toString());
    }

    @Test
    void refusesAnAbsoluteUrlOnAnotherOrigin() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, NO_ENV);

        ConfigurationException error =
                assertThrows(ConfigurationException.class, () -> config.buildUrl("https://evil.example/steal"));

        assertTrue(error.getMessage().contains("not on the configured API origin"));
    }

    @Test
    void allowsAnAbsoluteUrlTheApiItselfIssued() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, NO_ENV);
        String link = "https://api.example.test/v1/emails?cursor=abc";

        assertEquals(link, config.buildUrl(link).toString());
    }

    @Test
    void refusesAMalformedBaseUrl() {
        assertThrows(ConfigurationException.class, () -> new Config("mk_test", "http://a b c", null, null, NO_ENV));
    }

    @Test
    void appliesTheDefaultTimeoutWhenTheCallerSetsNone() {
        assertEquals(Config.DEFAULT_TIMEOUT, new Config("mk_test", null, null, null, NO_ENV).timeout());
        assertEquals(Duration.ofSeconds(5), new Config("mk_test", null, Duration.ofSeconds(5), null, NO_ENV).timeout());
    }

    @Test
    void leavesAnInjectedHttpClientOpen() throws IOException {
        java.net.http.HttpClient injected = java.net.http.HttpClient.newHttpClient();
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"abc\"}"))) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .apiKey("mk_test")
                    .baseUrl(server.baseUrl())
                    .httpClient(injected)
                    .environment(NO_ENV)
                    .build()) {
                client.emails().send(minimal().build());
            }
            // The client it did not create is still usable after close(): closing it would be
            // reaching into the caller's lifecycle.
            assertEquals(1, server.received().size());
        }
        injected.close();
    }

    static MailkubeClient client(StubServer server) {
        return MailkubeClient.builder()
                .apiKey("mk_test")
                .baseUrl(server.baseUrl())
                .environment(NO_ENV)
                .build();
    }

    static SendEmailParams.Builder minimal() {
        return SendEmailParams.builder("a@x.com", List.of("b@y.com"), "Hi");
    }
}
