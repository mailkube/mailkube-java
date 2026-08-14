package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.internal.Json;
import com.mailkube.internal.RequestSpec;
import com.mailkube.internal.SendTransport;
import com.mailkube.model.Attachment;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import com.mailkube.model.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The resource layer, driven through the narrow transport seam rather than HTTP: what is under
 * test here is request shaping, and a server would only slow it down.
 */
class EmailsTest {

    /** Records the spec it was handed and answers with a fixed result. */
    private static final class RecordingTransport implements SendTransport {
        private RequestSpec spec;

        @Override
        public Email sendEmail(RequestSpec spec) {
            this.spec = spec;
            return new Email("abc123", null, false, null, null, null);
        }
    }

    private RequestSpec capture(SendEmailParams params) {
        RecordingTransport transport = new RecordingTransport();
        MailkubeClient.builder()
                .apiKey("mk_test")
                .environment(Map.of())
                .transport(transport)
                .build()
                .emails()
                .send(params);
        return transport.spec;
    }

    @Test
    void postsToTheEmailsPathWithTheRequiredFields() {
        RequestSpec spec = capture(ClientTest.minimal().html("<p>Hi</p>").build());

        assertEquals("POST", spec.method());
        assertEquals("emails", spec.path());
        assertEquals(
                Map.of("from", "a@x.com", "to", List.of("b@y.com"), "subject", "Hi", "html", "<p>Hi</p>"), spec.body());
    }

    @Test
    void omitsUnsetFieldsEntirelyRatherThanSendingThemAsNull() {
        RequestSpec spec = capture(ClientTest.minimal().html(null).text(null).build());

        assertEquals(List.of("from", "to", "subject"), List.copyOf(spec.body().keySet()));
    }

    @Test
    void liftsTheIdempotencyKeyIntoAHeaderNotTheBody() {
        RequestSpec spec = capture(ClientTest.minimal().idempotencyKey("key-1").build());

        assertEquals("key-1", spec.headers().get("Idempotency-Key"));
        assertFalse(spec.body().containsKey("idempotency_key"));
    }

    @Test
    void base64EncodesAttachmentContent() {
        Attachment attachment = new Attachment("a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        RequestSpec spec =
                capture(ClientTest.minimal().attachments(List.of(attachment)).build());

        assertEquals(
                List.of(Map.of("filename", "a.txt", "content", "aGVsbG8=", "content_type", "text/plain")),
                spec.body().get("attachments"));
    }

    @Test
    void omitsTheContentTypeWhenTheServerShouldInferIt() {
        Attachment attachment = Attachment.of("a.txt", "hello".getBytes(StandardCharsets.UTF_8));

        RequestSpec spec =
                capture(ClientTest.minimal().attachments(List.of(attachment)).build());

        assertEquals(
                List.of(Map.of("filename", "a.txt", "content", "aGVsbG8=")),
                spec.body().get("attachments"));
    }

    @Test
    void treatsEmptyCollectionsAsAbsentRatherThanSendingEmptyArrays() {
        RequestSpec spec = capture(
                ClientTest.minimal().attachments(List.of()).tags(List.of()).build());

        assertEquals(List.of("from", "to", "subject"), List.copyOf(spec.body().keySet()));
    }

    @Test
    void treatsNullCollectionsAsAbsent() {
        RequestSpec spec = capture(ClientTest.minimal()
                .attachments(null)
                .tags(null)
                .scheduledAt(null)
                .build());

        assertEquals(List.of("from", "to", "subject"), List.copyOf(spec.body().keySet()));
    }

    @Test
    void sendsTagsAsNameValuePairs() {
        RequestSpec spec = capture(ClientTest.minimal()
                .tags(List.of(Tag.of("campaign"), new Tag("tier", "pro")))
                .build());

        assertEquals(
                List.of(Map.of("name", "campaign", "value", ""), Map.of("name", "tier", "value", "pro")),
                spec.body().get("tags"));
    }

    @Test
    void rendersAnInstantAsIso8601() {
        RequestSpec spec = capture(ClientTest.minimal()
                .scheduledAt(Instant.parse("2026-01-02T03:04:05Z"))
                .build());

        assertEquals("2026-01-02T03:04:05Z", spec.body().get("scheduled_at"));
    }

    @Test
    void carriesEveryOptionalFieldWhenTheCallerSetsThemAll() {
        // One maximal build, because a builder's setters are otherwise the easiest way for a
        // scaffold to ship methods no test ever calls.
        RequestSpec spec = capture(ClientTest.minimal()
                .html("<p>Hi</p>")
                .text("Hi")
                .cc(List.of("c@x.com"))
                .bcc(List.of("d@x.com"))
                .replyTo(List.of("reply@x.com"))
                .headers(Map.of("In-Reply-To", "<prev@msg>"))
                .templateId("tpl_1")
                .templateVersion("latest")
                .variables(Map.of("name", "Ada"))
                .topic("newsletter")
                .batchId("batch-1")
                .scheduledAt(Instant.parse("2026-01-02T03:04:05Z"))
                .idempotencyKey("key-1")
                .build());

        String encoded = Json.encode(spec.body());
        assertTrue(encoded.contains("\"reply_to\":[\"reply@x.com\"]"), encoded);
        assertTrue(encoded.contains("\"template_version\":\"latest\""), encoded);
        assertTrue(encoded.contains("\"batch_id\":\"batch-1\""), encoded);
        // Fifteen body fields; the idempotency key is the sixteenth setter and travels as a header.
        assertEquals(15, spec.body().size());
    }

    @Test
    void widensIntoTheScheduledShapeRatherThanReturningADifferentType() {
        Email immediate = new Email("abc", "<abc@msg>", false, null, null, null);
        Email scheduled = new Email("abc", null, false, "scheduled", "2026-01-02T03:04:05Z", "batch-1");

        assertFalse(immediate.isScheduled());
        assertNull(immediate.status());
        assertTrue(scheduled.isScheduled());
        assertEquals("batch-1", scheduled.batchId());
    }
}
