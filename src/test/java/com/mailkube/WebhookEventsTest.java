package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.MailkubeException;
import com.mailkube.model.DomainStatusEvent;
import com.mailkube.model.EmailBouncedEvent;
import com.mailkube.model.EmailClickedEvent;
import com.mailkube.model.EmailDeliveredEvent;
import com.mailkube.model.EmailDeliveryDelayedEvent;
import com.mailkube.model.EmailFailedEvent;
import com.mailkube.model.EmailOpenedEvent;
import com.mailkube.model.EmailScheduledEvent;
import com.mailkube.model.EmailSentEvent;
import com.mailkube.model.EmailSuppressedEvent;
import com.mailkube.model.Tag;
import com.mailkube.model.UnknownEvent;
import com.mailkube.model.WebhookEvent;
import com.mailkube.model.WebhookStatusEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The webhook event catalogue: that every declared event parses into its own type, and that the
 * catalogue cannot fall out of step with the sealed hierarchy.
 */
class WebhookEventsTest {

    private static final String MESSAGE = """
            "email_id":"msg_1","created_at":"2026-03-01T10:00:00Z","domain":"yourdomain.com",\
            "subject":"Hello","to":["a@b.com"],"from":"hello@yourdomain.com",\
            "tags":[{"name":"campaign","value":"spring"}]""";

    private static WebhookEvent parse(String type, String data) {
        String payload = "{\"type\":\"" + type + "\",\"created_at\":\"2026-03-01T10:00:00Z\",\"data\":{" + data + "}}";
        return Webhooks.parseEvent(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static WebhookEvent messageEvent(String type, String block) {
        return parse(type, MESSAGE + "," + block);
    }

    /**
     * One sample of every event type, in the order they are permitted.
     *
     * @return the parsed events
     */
    private static List<WebhookEvent> everyEvent() {
        return List.of(
                messageEvent(EmailSentEvent.TYPE, "\"sent\":{\"recipient\":\"a@b.com\",\"timestamp\":\"T1\"}"),
                messageEvent(EmailDeliveredEvent.TYPE, "\"delivery\":{\"recipient\":\"a@b.com\",\"timestamp\":\"T2\"}"),
                messageEvent(
                        EmailBouncedEvent.TYPE,
                        "\"bounce\":{\"recipient\":\"a@b.com\",\"timestamp\":\"T3\",\"code\":550,"
                                + "\"reason\":\"mailbox unavailable\"}"),
                messageEvent(
                        EmailDeliveryDelayedEvent.TYPE,
                        "\"delay\":{\"recipient\":\"a@b.com\",\"timestamp\":\"T4\",\"code\":451,"
                                + "\"reason\":\"greylisted\"}"),
                messageEvent(
                        EmailSuppressedEvent.TYPE,
                        "\"suppression\":{\"recipients\":[\"a@b.com\"],\"timestamp\":\"T5\"}"),
                messageEvent(
                        EmailScheduledEvent.TYPE,
                        "\"scheduled\":{\"scheduled_at\":\"2026-03-02T10:00:00Z\",\"batch_id\":null}"),
                messageEvent(
                        EmailFailedEvent.TYPE,
                        "\"failed\":{\"reason\":\"suppressed_at_dispatch\",\"timestamp\":\"T6\"}"),
                messageEvent(
                        EmailOpenedEvent.TYPE,
                        "\"open\":{\"ipAddress\":\"203.0.113.7\",\"userAgent\":\"Mail/1\",\"timestamp\":\"T7\"}"),
                messageEvent(
                        EmailClickedEvent.TYPE,
                        "\"click\":{\"ipAddress\":\"203.0.113.7\",\"userAgent\":\"Mail/1\",\"timestamp\":\"T8\","
                                + "\"link\":\"https://example.com/x\"}"),
                parse(
                        DomainStatusEvent.TYPE,
                        "\"domain\":\"yourdomain.com\",\"status\":\"verified\",\"onboarding_state\":\"done\","
                                + "\"previous\":{\"status\":\"pending\",\"onboarding_state\":\"dns\"}"),
                parse(
                        WebhookStatusEvent.TYPE,
                        "\"endpoint_url\":\"https://hooks.example.com/mk\",\"is_active\":false,"
                                + "\"is_deleted\":false,\"disabled_reason\":\"too_many_failures\","
                                + "\"previous\":{\"is_active\":true,\"is_deleted\":false,\"disabled_reason\":\"\"}"),
                parse("something.invented_next_year", "\"whatever\":1"));
    }

    /**
     * The catalogue guard.
     *
     * <p>An exhaustive {@code switch} over the sealed interface, with <b>no</b> default arm. Adding
     * a record to {@code WebhookEvent}'s {@code permits} clause without adding an arm here is a
     * compile error, so the catalogue cannot grow a type that nothing has ever parsed. The
     * assertions inside each arm are the other half: an arm with no matching production entry
     * fails, because the payload would have parsed as {@link UnknownEvent} instead.
     *
     * @param event the event to check
     * @return its wire type, so the caller can assert the set is complete
     */
    private static String checkAndName(WebhookEvent event) {
        return switch (event) {
            case EmailSentEvent e -> {
                assertEquals("a@b.com", e.sent().recipient());
                yield e.type();
            }
            case EmailDeliveredEvent e -> {
                assertEquals("T2", e.delivery().timestamp());
                yield e.type();
            }
            case EmailBouncedEvent e -> {
                assertEquals(550, e.bounce().code());
                assertEquals("mailbox unavailable", e.bounce().reason());
                yield e.type();
            }
            case EmailDeliveryDelayedEvent e -> {
                assertEquals(451, e.delay().code());
                yield e.type();
            }
            case EmailSuppressedEvent e -> {
                assertEquals(List.of("a@b.com"), e.suppression().recipients());
                yield e.type();
            }
            case EmailScheduledEvent e -> {
                assertEquals("2026-03-02T10:00:00Z", e.scheduled().scheduledAt());
                // Sent as an explicit null, which means the send was not grouped into a batch.
                assertNull(e.scheduled().batchId());
                yield e.type();
            }
            case EmailFailedEvent e -> {
                assertEquals("suppressed_at_dispatch", e.failed().reason());
                yield e.type();
            }
            case EmailOpenedEvent e -> {
                assertEquals("203.0.113.7", e.open().ipAddress());
                assertEquals("Mail/1", e.open().userAgent());
                yield e.type();
            }
            case EmailClickedEvent e -> {
                assertEquals("https://example.com/x", e.click().link());
                assertEquals("203.0.113.7", e.click().ipAddress());
                yield e.type();
            }
            case DomainStatusEvent e -> {
                assertEquals("verified", e.status());
                assertEquals("pending", e.previous().status());
                yield e.type();
            }
            case WebhookStatusEvent e -> {
                assertEquals("too_many_failures", e.disabledReason());
                assertTrue(e.previous().isActive());
                yield e.type();
            }
            case UnknownEvent e -> {
                assertEquals(1L, e.raw().get("data") instanceof Map<?, ?> m ? m.get("whatever") : null);
                yield e.type();
            }
        };
    }

    @Test
    void parsesEveryDeclaredEventIntoItsOwnType() {
        List<String> types =
                everyEvent().stream().map(WebhookEventsTest::checkAndName).toList();

        assertEquals(
                List.of(
                        "email.sent",
                        "email.delivered",
                        "email.bounced",
                        "email.delivery_delayed",
                        "email.suppressed",
                        "email.scheduled",
                        "email.failed",
                        "email.opened",
                        "email.clicked",
                        "domain.status",
                        "webhook.status",
                        "something.invented_next_year"),
                types);
    }

    @Test
    void givesEveryEmailEventTheSameMessageContext() {
        for (WebhookEvent event : everyEvent()) {
            if (event instanceof EmailSentEvent || event instanceof EmailClickedEvent) {
                var message =
                        event instanceof EmailSentEvent sent ? sent.message() : ((EmailClickedEvent) event).message();
                assertEquals("msg_1", message.emailId());
                assertEquals("Hello", message.subject());
                assertEquals(List.of("a@b.com"), message.to());
                assertEquals("hello@yourdomain.com", message.from());
                assertEquals(List.of(new Tag("campaign", "spring")), message.tags());
            }
        }
    }

    @Test
    void keepsFieldsThisReleaseHasNoAccessorFor() {
        // The contract's "unknown fields are preserved, not dropped". Java has no lenient binder,
        // so `raw()` on the interface is how every event keeps the promise.
        WebhookEvent event = messageEvent(
                EmailSentEvent.TYPE,
                "\"sent\":{\"recipient\":\"a@b.com\",\"timestamp\":\"T1\"},\"invented_next_year\":\"kept\"");

        Map<?, ?> data = (Map<?, ?>) event.raw().get("data");
        assertEquals("kept", data.get("invented_next_year"));
    }

    @Test
    void routesAnUnrecognizedTypeRatherThanRaising() {
        WebhookEvent event = parse("email.teleported", "\"anything\":true");

        UnknownEvent unknown = assertInstanceOf(UnknownEvent.class, event);
        assertEquals("email.teleported", unknown.type());
        assertEquals("2026-03-01T10:00:00Z", unknown.createdAt());
    }

    @Test
    void toleratesAnEventWhoseDataBlockIsAbsentOrEmpty() {
        // An already-released client must not break on a payload it has not seen. Every accessor
        // is present; the values are simply absent.
        EmailSentEvent event = assertInstanceOf(
                EmailSentEvent.class,
                Webhooks.parseEvent("{\"type\":\"email.sent\"}".getBytes(StandardCharsets.UTF_8)));

        assertNull(event.createdAt());
        assertNull(event.message().emailId());
        // `to` was never sent, which is not the same as being sent empty.
        assertNull(event.message().to());
        assertEquals(List.of(), event.message().tags());
        assertNull(event.sent().recipient());
    }

    @Test
    void refusesAPayloadThatIsNotAJsonObject() {
        // A malformed body is a broken request, not a new event type, so this one does raise.
        for (String broken : new String[] {"<html>not json</html>", "[1,2]", ""}) {
            MailkubeException error = assertThrows(
                    MailkubeException.class,
                    () -> Webhooks.parseEvent(broken.getBytes(StandardCharsets.UTF_8)),
                    "should have refused: " + broken);
            assertTrue(error.getMessage().contains("the webhook payload"));
        }
    }

    @Test
    void exposesTheWholePayloadAsUnmodifiable() {
        WebhookEvent event = parse(DomainStatusEvent.TYPE, "\"domain\":\"yourdomain.com\"");

        assertThrows(UnsupportedOperationException.class, () -> event.raw().put("type", "spoofed"));
    }
}
