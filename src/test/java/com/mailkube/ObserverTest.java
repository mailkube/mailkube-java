package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.ConnectionException;
import com.mailkube.exception.NotFoundException;
import com.mailkube.internal.Config;
import com.mailkube.model.SendEmailParams;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * What the client tells an observer, and what it deliberately never tells one.
 *
 * <p>The negative assertions matter as much as the positive ones: the reason this SDK can promise
 * that no recipient address reaches a log is that the observer is never handed a body to leak.
 */
class ObserverTest {

    /** One notification, kept as the observer received it. */
    private record Call(
            String method, String path, Integer status, String requestId, Duration elapsed, Throwable error) {}

    /** Collects notifications. Thread-safe, because {@code ConcurrencyTest} shares one client. */
    private static final class Recorder implements RequestObserver {

        private final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public void onResponse(
                String method, String path, Integer status, String requestId, Duration elapsed, Throwable error) {
            calls.add(new Call(method, path, status, requestId, elapsed, error));
        }

        private Call only() {
            assertEquals(1, calls.size(), "expected exactly one notification");
            return calls.getFirst();
        }
    }

    private static SendEmailParams params() {
        return SendEmailParams.builder("a@b.com", List.of("c@d.com"), "Subject")
                .text("Body")
                .build();
    }

    @Test
    void reportsWhatHappenedOnASuccessfulExchange() throws IOException {
        Recorder recorder = new Recorder();
        try (StubServer server = new StubServer(
                        request -> new StubServer.Reply(200, "{\"id\":\"em_1\"}", Map.of("X-Request-Id", "req_42")));
                MailkubeClient client = client(server).observer(recorder).build()) {

            client.emails().send(params());

            Call call = recorder.only();
            assertEquals("POST", call.method());
            assertEquals("/emails", call.path());
            assertEquals(200, call.status());
            assertEquals("req_42", call.requestId());
            assertNull(call.error());
            assertFalse(call.elapsed().isNegative());
        }
    }

    @Test
    void treatsAnApiErrorAsAResponse() throws IOException {
        // A 404 is something the server said, so it arrives with a status and no error. Reporting
        // it as a failure would make every "does this exist?" call look like an outage.
        Recorder recorder = new Recorder();
        try (StubServer server = new StubServer(request ->
                        new StubServer.Reply(404, "{\"name\":\"not_found\",\"message\":\"no such email\"}", Map.of()));
                MailkubeClient client = client(server).observer(recorder).build()) {

            assertThrows(NotFoundException.class, () -> client.emails().send(params()));

            Call call = recorder.only();
            assertEquals(404, call.status());
            assertNull(call.error());
        }
    }

    @Test
    void reportsAnExchangeThatNeverProducedAResponse() {
        // The case a notification on the success path loses entirely, and the one an operator most
        // needs: the request went out and nothing came back.
        Recorder recorder = new Recorder();
        try (MailkubeClient client = MailkubeClient.builder()
                .apiKey("mk_test")
                // Port 1 on loopback: nothing listens, so the connection is refused immediately.
                .baseUrl("http://127.0.0.1:1/v1/")
                .environment(Map.of())
                .observer(recorder)
                .build()) {

            assertThrows(ConnectionException.class, () -> client.emails().send(params()));

            Call call = recorder.only();
            assertEquals("POST", call.method());
            assertNull(call.status());
            assertNull(call.requestId());
            assertInstanceOf(ConnectionException.class, call.error());
        }
    }

    @Test
    void keepsWorkingWhenTheObserverThrows() throws IOException {
        // An observer is application code on the path of every request. A bug in a metrics sink
        // must not turn a delivered email into an exception the caller has to handle.
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"em_1\"}"));
                MailkubeClient client = client(server)
                        .observer((method, path, status, requestId, elapsed, error) -> {
                            throw new IllegalStateException("the sink is down");
                        })
                        .build()) {

            assertEquals("em_1", client.emails().send(params()).id());
        }
    }

    @Test
    void notifiesEveryVerbAndNotOnlyTheSendPath() throws IOException {
        Recorder recorder = new Recorder();
        try (StubServer server = new StubServer(
                        request -> StubServer.Reply.ok("{\"id\":\"se_1\",\"object\":\"scheduled_email\"}"));
                MailkubeClient client = client(server).observer(recorder).build()) {

            client.scheduledEmails().get("se_1");

            assertEquals("GET", recorder.only().method());
        }
    }

    @Test
    void tellsAnObserverNothingItCouldLeak() throws IOException {
        // The PII rule, asserted rather than promised: the send below carries a recipient address,
        // a subject and a body, and none of them can reach a log because none of them is passed.
        Recorder recorder = new Recorder();
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"em_1\"}"));
                MailkubeClient client = client(server).observer(recorder).build()) {

            client.emails()
                    .send(SendEmailParams.builder("a@b.com", List.of("secret@example.com"), "Your invoice")
                            .text("Account 12345")
                            .build());

            String everythingTheObserverSaw = String.valueOf(recorder.only());
            for (String secret : List.of("secret@example.com", "Your invoice", "Account 12345", "Bearer", "mk_test")) {
                assertFalse(everythingTheObserverSaw.contains(secret), "the observer was handed " + secret);
            }
        }
    }

    @Test
    void prefersAnExplicitObserverOverEveryLoggingSetting() throws IOException {
        Recorder recorder = new Recorder();
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"em_1\"}"));
                MailkubeClient client = client(server)
                        .logging(System.Logger.Level.DEBUG)
                        .environment(Map.of(Config.ENV_LOG, "INFO"))
                        .observer(recorder)
                        .build()) {

            client.emails().send(params());

            assertNotNull(recorder.only());
        }
    }

    @Test
    void staysSilentWhenNobodyAsked() throws IOException {
        // No observer, no level, no environment variable: the default path must still work, which
        // is what proves the no-op stands in for a null the transport would otherwise test for.
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"em_1\"}"));
                MailkubeClient client = client(server).build()) {

            assertEquals("em_1", client.emails().send(params()).id());
        }
    }

    @Test
    void readsTheLogLevelFromTheEnvironmentAsALevelNotASwitch() {
        assertNull(config(Map.of()).logLevel());
        assertNull(config(Map.of(Config.ENV_LOG, "  ")).logLevel());
        assertEquals(
                System.Logger.Level.WARNING,
                config(Map.of(Config.ENV_LOG, "warning")).logLevel());
        assertEquals(
                System.Logger.Level.DEBUG,
                config(Map.of(Config.ENV_LOG, "DEBUG")).logLevel());
    }

    @Test
    void refusesToLetALogLevelStopAClientBeingBuilt() {
        // An unrecognized value falls back rather than raising: how loudly to log is not a reason
        // to fail construction, and the variable is often set by someone who is not the developer.
        assertEquals(
                Config.DEFAULT_LOG_LEVEL,
                config(Map.of(Config.ENV_LOG, "chatty")).logLevel());
    }

    @Test
    void letsAnExplicitLevelBeatTheEnvironment() {
        Config config = new Config("mk_test", null, null, System.Logger.Level.ERROR, Map.of(Config.ENV_LOG, "TRACE"));

        assertEquals(System.Logger.Level.ERROR, config.logLevel());
    }

    @Test
    void writesOneRecordPerExchangeThroughTheBuiltInLogger() throws IOException {
        try (LogCapture capture = LogCapture.attach(java.util.logging.Level.ALL);
                StubServer server = new StubServer(
                        request -> new StubServer.Reply(200, "{\"id\":\"em_1\"}", Map.of("X-Request-Id", "req_7")));
                MailkubeClient client =
                        client(server).logging(System.Logger.Level.INFO).build()) {

            client.emails().send(params());

            assertEquals(1, capture.records().size());
            assertTrue(
                    capture.records().getFirst().contains("POST"),
                    capture.records().getFirst());
            assertTrue(
                    capture.records().getFirst().contains("req_7"),
                    capture.records().getFirst());
        }
    }

    @Test
    void reportsAFailedExchangeThroughTheBuiltInLoggerToo() {
        try (LogCapture capture = LogCapture.attach(java.util.logging.Level.ALL);
                MailkubeClient client = MailkubeClient.builder()
                        .apiKey("mk_test")
                        .baseUrl("http://127.0.0.1:1/v1/")
                        .environment(Map.of(Config.ENV_LOG, "INFO"))
                        .build()) {

            assertThrows(ConnectionException.class, () -> client.emails().send(params()));

            assertEquals(1, capture.records().size());
            assertTrue(
                    capture.records().getFirst().contains("no response"),
                    capture.records().getFirst());
        }
    }

    @Test
    void writesNothingAtALevelTheHostIsNotListeningFor() throws IOException {
        // Silent by default by construction: with no LoggerFinder installed the backing
        // implementation sits at INFO, so a TRACE record is dropped before it is ever formatted.
        try (LogCapture capture = LogCapture.attach(java.util.logging.Level.INFO);
                StubServer server = new StubServer(request -> StubServer.Reply.ok("{\"id\":\"em_1\"}"));
                MailkubeClient client =
                        client(server).logging(System.Logger.Level.TRACE).build()) {

            client.emails().send(params());

            assertEquals(List.of(), capture.records());
        }
    }

    private static Config config(Map<String, String> environment) {
        return new Config("mk_test", null, null, null, environment);
    }

    private static MailkubeClient.Builder client(StubServer server) {
        return MailkubeClient.builder()
                .apiKey("mk_test")
                .baseUrl(server.baseUrl())
                .environment(Map.of());
    }

    /**
     * Captures what the built-in observer writes.
     *
     * <p>{@code System.Logger} is a facade with no capture API of its own. With no
     * {@code LoggerFinder} on the module path it is backed by {@code java.util.logging}, so
     * attaching a handler to the same logger name is how a test sees the records, and asserting
     * through that backing is also what proves the facade really does reach a host's framework.
     */
    private static final class LogCapture implements AutoCloseable {

        private final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.mailkube");
        private final List<String> sink = new ArrayList<>();
        private final java.util.logging.Handler handler;
        private final java.util.logging.Level previous = logger.getLevel();

        private LogCapture(java.util.logging.Level level) {
            this.handler = new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    sink.add(new java.util.logging.SimpleFormatter().formatMessage(record));
                }

                @Override
                public void flush() {
                    // Nothing is buffered.
                }

                @Override
                public void close() {
                    // Nothing to release.
                }
            };
            logger.setLevel(level);
            logger.addHandler(handler);
        }

        private static LogCapture attach(java.util.logging.Level level) {
            return new LogCapture(level);
        }

        private List<String> records() {
            return sink;
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
    }
}
