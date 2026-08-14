package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.ConfigurationException;
import com.mailkube.exception.NotFoundException;
import com.mailkube.internal.RequestSpec;
import com.mailkube.internal.ResponseMapper;
import com.mailkube.internal.ScheduledTransport;
import com.mailkube.model.CanceledScheduledEmail;
import com.mailkube.model.ScheduledEmail;
import com.mailkube.model.ScheduledEmailBatchCancel;
import com.mailkube.model.ScheduledEmailBatchUpdate;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.ScheduledEmailPage;
import com.mailkube.model.ScheduledEmailUpdateParams;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The scheduled-emails resource: what each verb puts on the wire, what it makes of the answer, and
 * how the pagination walk behaves when a caller stops early.
 */
class ScheduledEmailsTest {

    /** Records every spec it is handed and answers each with a body supplied by the test. */
    private static final class RecordingTransport implements ScheduledTransport {

        private final List<RequestSpec> specs = new ArrayList<>();
        private final Map<String, Object> reply;

        private RecordingTransport(Map<String, Object> reply) {
            this.reply = reply;
        }

        @Override
        public <T> T request(RequestSpec spec, ResponseMapper<T> mapper) {
            specs.add(spec);
            return mapper.map(reply);
        }

        private RequestSpec only() {
            assertEquals(1, specs.size());
            return specs.getFirst();
        }
    }

    private static MailkubeClient clientFor(RecordingTransport transport) {
        return MailkubeClient.builder()
                .apiKey("mk_test")
                .environment(Map.of())
                .scheduledTransport(transport)
                .build();
    }

    private static RecordingTransport drive(Consumer<ScheduledEmails> verb) {
        RecordingTransport transport = new RecordingTransport(Map.of());
        verb.accept(clientFor(transport).scheduledEmails());
        return transport;
    }

    @Test
    void listsWithTheFiltersAsQueryParameters() {
        RecordingTransport transport = drive(resource -> resource.list(ScheduledEmailListParams.builder()
                .status(List.of("scheduled", "canceled"))
                .batchId("welcome")
                .scheduledAtGte(Instant.parse("2026-03-01T00:00:00Z"))
                .build()));

        RequestSpec spec = transport.only();
        assertEquals("GET", spec.method());
        assertEquals("scheduled-emails", spec.path());
        assertEquals(
                Map.of(
                        "status", "scheduled,canceled",
                        "batch_id", "welcome",
                        "scheduled_at_gte", "2026-03-01T00:00:00Z"),
                spec.query());
        // A GET sends no bytes at all, which is not the same as sending `{}`.
        assertNull(spec.body());
    }

    @Test
    void escapesAnIdentifierIntoTheItemPath() {
        // An id carrying a slash must not walk out of the collection and re-target the request.
        assertEquals("scheduled-emails/a%2Fb", drive(r -> r.get("a/b")).only().path());
        assertEquals(
                "scheduled-emails/batches/q%3Fx",
                drive(r -> r.batches().cancel("q?x")).only().path());
    }

    @Test
    void reschedulesOneEmailWithTheOptionalBatchMove() {
        RequestSpec spec = drive(r -> r.update(
                        "sch_1",
                        ScheduledEmailUpdateParams.builder(Instant.parse("2026-03-02T09:00:00Z"))
                                .batchId("welcome")
                                .build()))
                .only();

        assertEquals("PATCH", spec.method());
        assertEquals("scheduled-emails/sch_1", spec.path());
        assertEquals(Map.of("scheduled_at", "2026-03-02T09:00:00Z", "batch_id", "welcome"), spec.body());
    }

    @Test
    void omitsTheBatchWhenTheRescheduleDoesNotMoveTheEmail() {
        RequestSpec spec = drive(r -> r.update(
                        "sch_1",
                        ScheduledEmailUpdateParams.builder("2026-03-02T09:00:00Z")
                                .batchId(null)
                                .build()))
                .only();

        assertEquals(Map.of("scheduled_at", "2026-03-02T09:00:00Z"), spec.body());
    }

    @Test
    void sendsOnlyADueTimeOnTheBatchRoute() {
        // The single-item route may also move an email into a batch; the batch route takes a due
        // time and nothing else. Sharing one params type would put a field here that is ignored.
        RequestSpec spec = drive(r -> r.batches().update("welcome", Instant.parse("2026-03-03T10:00:00Z")))
                .only();

        assertEquals("PATCH", spec.method());
        assertEquals("scheduled-emails/batches/welcome", spec.path());
        assertEquals(Map.of("scheduled_at", "2026-03-03T10:00:00Z"), spec.body());
    }

    @Test
    void rendersAnInstantAndAnEquivalentStringIdentically() {
        Instant due = Instant.parse("2026-03-03T10:00:00Z");

        assertEquals(
                drive(r -> r.batches().update("b", due)).only().body(),
                drive(r -> r.batches().update("b", "2026-03-03T10:00:00Z"))
                        .only()
                        .body());
    }

    @Test
    void cancelsWithNoBody() {
        assertEquals("DELETE", drive(r -> r.cancel("sch_1")).only().method());
        assertNull(drive(r -> r.cancel("sch_1")).only().body());
    }

    @Test
    void parsesOneScheduledEmailIncludingItsTags() {
        RecordingTransport transport = new RecordingTransport(Map.of(
                "id",
                "sch_1",
                "object",
                "scheduled_email",
                "status",
                "scheduled",
                "scheduled_at",
                "2026-03-01T00:00:00Z",
                "recipients",
                "a@b.com +2",
                "tags",
                List.of(Map.of("name", "campaign", "value", "spring"))));

        ScheduledEmail email = clientFor(transport).scheduledEmails().get("sch_1");

        assertEquals("sch_1", email.id());
        assertEquals("scheduled", email.status());
        // A summary string, not a list: the full recipient set stays server-side.
        assertEquals("a@b.com +2", email.recipients());
        assertEquals("campaign", email.tags().getFirst().name());
        assertEquals("spring", email.tags().getFirst().value());
        assertNull(email.batchId());
    }

    @Test
    void parsesTheBatchResultsAndTheCancellationAcknowledgement() {
        ScheduledEmailBatchUpdate update = clientFor(new RecordingTransport(
                        Map.of("object", "scheduled_email.batch", "batch_id", "welcome", "rescheduled_count", 4L)))
                .scheduledEmails()
                .batches()
                .update("welcome", "2026-03-03T10:00:00Z");
        assertEquals(4, update.rescheduledCount());
        assertNull(update.scheduledAt());

        ScheduledEmailBatchCancel cancel = clientFor(new RecordingTransport(Map.of("canceled_count", 2L)))
                .scheduledEmails()
                .batches()
                .cancel("welcome");
        assertEquals(2, cancel.canceledCount());

        CanceledScheduledEmail canceled = clientFor(new RecordingTransport(Map.of("id", "sch_1", "status", "canceled")))
                .scheduledEmails()
                .cancel("sch_1");
        assertEquals("canceled", canceled.status());
    }

    @Test
    void defaultsACountThatIsAbsentOrNotANumberToZero() {
        // An already-released client must not break on a payload it has not seen.
        assertEquals(
                0,
                clientFor(new RecordingTransport(Map.of("canceled_count", "many")))
                        .scheduledEmails()
                        .batches()
                        .cancel("welcome")
                        .canceledCount());
    }

    @Test
    void parsesAPageWithItsMetadata() {
        ScheduledEmailPage page = clientFor(new RecordingTransport(Map.of(
                        "pagination",
                        Map.of(
                                "steps",
                                Map.of("next", "https://api.example.test/v1/scheduled-emails?page=2"),
                                "total_count",
                                3L,
                                "current_page",
                                1L),
                        "data",
                        List.of(Map.of("id", "sch_1"), Map.of("id", "sch_2")))))
                .scheduledEmails()
                .list(ScheduledEmailListParams.none());

        assertEquals(2, page.data().size());
        assertEquals(3, page.pagination().totalCount());
        assertEquals(1, page.pagination().currentPage());
        assertNull(page.pagination().steps().previous());
        assertTrue(page.hasMore());
    }

    @Test
    void treatsAnEmptyPageAsTheLastOne() {
        ScheduledEmailPage page =
                clientFor(new RecordingTransport(Map.of())).scheduledEmails().list(ScheduledEmailListParams.none());

        assertEquals(List.of(), page.data());
        assertEquals(1, page.pagination().currentPage());
        assertFalse(page.hasMore());
    }

    // --- The pagination walk, over a real server so the `next` link goes through the transport ---

    /**
     * A two-page listing whose {@code next} link is absolute, as the real API's is.
     *
     * <p>The link is built from the request's own Host header rather than from the server object,
     * which does not exist yet when the handler is written. That is also the realistic shape: the
     * client has to accept an absolute URL it did not construct.
     */
    private static StubServer pagedServer() throws IOException {
        return new StubServer(request -> {
            boolean second = "page=2".equals(request.query());
            String next = second ? "" : "\"next\":\"http://" + request.header("Host") + "/scheduled-emails?page=2\",";
            return StubServer.Reply.ok("{\"pagination\":{\"steps\":{" + next + "\"previous\":null},"
                    + "\"total_count\":2,\"current_page\":" + (second ? 2 : 1) + "},"
                    + "\"data\":[{\"id\":\"" + (second ? "sch_2" : "sch_1") + "\"}]}");
        });
    }

    @Test
    void followsTheServersNextLinkAcrossPages() throws IOException {
        try (StubServer server = pagedServer()) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .apiKey("mk_test")
                    .baseUrl(server.baseUrl())
                    .environment(Map.of())
                    .build()) {

                List<String> ids = client.scheduledEmails()
                        .iterateAll(ScheduledEmailListParams.none())
                        .map(ScheduledEmail::id)
                        .toList();

                assertEquals(List.of("sch_1", "sch_2"), ids);
                assertEquals(2, server.received().size());
            }
        }
    }

    @Test
    void fetchesNoFurtherPageWhenTheCallerStopsEarly() throws IOException {
        try (StubServer server = pagedServer()) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .apiKey("mk_test")
                    .baseUrl(server.baseUrl())
                    .environment(Map.of())
                    .build()) {

                Stream<ScheduledEmail> all = client.scheduledEmails().iterateAll(ScheduledEmailListParams.none());
                // Building the stream must perform no I/O at all.
                assertEquals(0, server.received().size());

                assertEquals(1, all.limit(1).toList().size());
                assertEquals(1, server.received().size());
            }
        }
    }

    @Test
    void refusesANextLinkOnAForeignOrigin() throws IOException {
        try (StubServer server = new StubServer(request -> StubServer.Reply.ok(
                "{\"pagination\":{\"steps\":{\"next\":\"https://evil.example/v1/scheduled-emails?page=2\"}},"
                        + "\"data\":[{\"id\":\"sch_1\"}]}"))) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .apiKey("mk_test")
                    .baseUrl(server.baseUrl())
                    .environment(Map.of())
                    .build()) {

                // A pagination link is server-controlled. Following one off the configured origin
                // would hand the API key to whoever it points at.
                assertThrows(
                        ConfigurationException.class,
                        () -> client.scheduledEmails()
                                .iterateAll(ScheduledEmailListParams.none())
                                .toList());
            }
        }
    }

    @Test
    void mapsAnErrorStatusOnAScheduledRoute() throws IOException {
        try (StubServer server = new StubServer(request -> new StubServer.Reply(
                404, "{\"name\":\"not_found\",\"message\":\"no such scheduled email\"}", Map.of()))) {
            try (MailkubeClient client = MailkubeClient.builder()
                    .apiKey("mk_test")
                    .baseUrl(server.baseUrl())
                    .environment(Map.of())
                    .build()) {

                NotFoundException error = assertThrows(
                        NotFoundException.class, () -> client.scheduledEmails().get("sch_missing"));
                assertEquals(404, error.statusCode());
                assertEquals("not_found", error.errorName());
            }
        }
    }
}
