package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.ApiException;
import com.mailkube.exception.AuthenticationException;
import com.mailkube.exception.BadRequestException;
import com.mailkube.exception.ConflictException;
import com.mailkube.exception.ConnectionException;
import com.mailkube.exception.ErrorName;
import com.mailkube.exception.InvalidRequestException;
import com.mailkube.exception.MailkubeException;
import com.mailkube.exception.NotFoundException;
import com.mailkube.exception.RateLimitException;
import com.mailkube.exception.ServerException;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    static Stream<Arguments> statuses() {
        // One table, one row per status. A new status becomes a row here and a branch in
        // ApiException.forStatus, and nowhere else.
        return Stream.of(
                Arguments.of(400, BadRequestException.class),
                Arguments.of(403, AuthenticationException.class),
                Arguments.of(404, NotFoundException.class),
                Arguments.of(409, ConflictException.class),
                Arguments.of(422, InvalidRequestException.class),
                Arguments.of(429, RateLimitException.class),
                Arguments.of(500, ServerException.class),
                Arguments.of(503, ServerException.class),
                Arguments.of(418, ApiException.class));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void mapsEachStatusToItsCategory(int status, Class<? extends ApiException> expected) throws IOException {
        try (StubServer server = new StubServer(
                request -> new StubServer.Reply(status, "{\"name\":\"boom\",\"message\":\"it broke\"}", Map.of()))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                ApiException error = assertThrows(
                        ApiException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertInstanceOf(expected, error);
                assertEquals("it broke", error.getMessage());
                assertEquals(status, error.statusCode());
            }
        }
    }

    @Test
    void carriesTheWholeEnvelopeNotJustTheMessage() throws IOException {
        String body = "{\"name\":\"" + ErrorName.RATE_LIMIT_EXCEEDED + "\",\"message\":\"slow down\"}";
        Map<String, String> headers = Map.of("Retry-After", "12", "X-Request-Id", "req_1");
        try (StubServer server = new StubServer(request -> new StubServer.Reply(429, body, headers))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                RateLimitException error = assertThrows(
                        RateLimitException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertEquals("rate_limit_exceeded", error.errorName());
                assertEquals(12, error.retryAfter());
                assertEquals("req_1", error.requestId());
                assertEquals("slow down", error.body().get("message"));
            }
        }
    }

    @Test
    void stillMapsByStatusWhenTheErrorBodyIsNotJsonAtAll() throws IOException {
        try (StubServer server =
                new StubServer(request -> new StubServer.Reply(500, "<html>502 Bad Gateway</html>", Map.of()))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                ServerException error = assertThrows(
                        ServerException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertEquals("HTTP 500", error.getMessage());
                assertEquals("", error.errorName());
                assertNull(error.retryAfter());
            }
        }
    }

    @Test
    void reportsAnUnrecognizedErrorNameVerbatimRatherThanCoercingIt() throws IOException {
        try (StubServer server =
                new StubServer(request -> new StubServer.Reply(400, "{\"name\":\"invented_next_year\"}", Map.of()))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                BadRequestException error = assertThrows(
                        BadRequestException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertEquals("invented_next_year", error.errorName());
                assertEquals("invented_next_year", error.getMessage());
            }
        }
    }

    @Test
    void raisesWhenATwoHundredBodyCarriesNoId() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{}"))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                ApiException error = assertThrows(
                        ApiException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertTrue(error.getMessage().contains("expected a JSON body with an 'id'"));
            }
        }
    }

    @Test
    void reportsAnIdempotentReplayFromTheResponseHeader() throws IOException {
        try (StubServer server = new StubServer(
                request -> new StubServer.Reply(200, "{\"id\":\"abc\"}", Map.of("Idempotent-Replayed", "true")))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                assertTrue(client.emails()
                        .send(ClientTest.minimal().idempotencyKey("k").build())
                        .idempotentReplayed());
            }
        }
    }

    @Test
    void wrapsATransportFailureAsAConnectionExceptionNotAnApiException() {
        // Port 1 on loopback refuses connections; nothing is listening there.
        try (MailkubeClient client = MailkubeClient.builder()
                .apiKey("mk_test")
                .baseUrl("http://127.0.0.1:1/")
                .environment(Map.of())
                .build()) {
            assertThrows(
                    ConnectionException.class,
                    () -> client.emails().send(ClientTest.minimal().build()));
        }
    }

    @Test
    void fallsBackToTheErrorNameWhenTheEnvelopeCarriesAnEmptyMessage() throws IOException {
        try (StubServer server = new StubServer(
                request -> new StubServer.Reply(403, "{\"name\":\"invalid_api_key\",\"message\":\"\"}", Map.of()))) {
            try (MailkubeClient client = ClientTest.client(server)) {
                AuthenticationException error = assertThrows(
                        AuthenticationException.class,
                        () -> client.emails().send(ClientTest.minimal().build()));

                assertEquals("invalid_api_key", error.getMessage());
            }
        }
    }

    @Test
    void putsEveryErrorUnderOneCatchableBaseType() {
        assertTrue(MailkubeException.class.isAssignableFrom(RateLimitException.class));
        assertTrue(ApiException.class.isAssignableFrom(RateLimitException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(MailkubeException.class));
    }
}
