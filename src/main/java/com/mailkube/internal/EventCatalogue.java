package com.mailkube.internal;

import com.mailkube.model.ClickContext;
import com.mailkube.model.DeliveryContext;
import com.mailkube.model.DomainStatusEvent;
import com.mailkube.model.DomainStatusPrevious;
import com.mailkube.model.EmailBouncedEvent;
import com.mailkube.model.EmailClickedEvent;
import com.mailkube.model.EmailDeliveredEvent;
import com.mailkube.model.EmailDeliveryDelayedEvent;
import com.mailkube.model.EmailFailedEvent;
import com.mailkube.model.EmailOpenedEvent;
import com.mailkube.model.EmailScheduledEvent;
import com.mailkube.model.EmailSentEvent;
import com.mailkube.model.EmailSuppressedEvent;
import com.mailkube.model.EngagementContext;
import com.mailkube.model.FailureContext;
import com.mailkube.model.MessageContext;
import com.mailkube.model.ScheduledContext;
import com.mailkube.model.SendFailureContext;
import com.mailkube.model.SuppressionContext;
import com.mailkube.model.Tag;
import com.mailkube.model.UnknownEvent;
import com.mailkube.model.WebhookEvent;
import com.mailkube.model.WebhookStatusEvent;
import com.mailkube.model.WebhookStatusPrevious;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Turns a decoded webhook payload into its typed event.
 *
 * <p>Dispatch is a lookup in a constant registry keyed by each event's own {@code TYPE}, not a
 * {@code switch} over the type string. Both would work; the registry stays one statement as the
 * catalogue grows, where a twelve-arm switch is a single method whose complexity climbs with every
 * event added. What guarantees the catalogue is complete is not this class but the exhaustive
 * {@code switch} over the sealed {@code WebhookEvent} in the tests, which stops compiling the
 * moment a permitted type has no arm.
 *
 * <p>It lives in {@code internal} because it is the only part of event handling that touches the
 * JSON codec. The models it builds are exported; the mapping is not.
 */
public final class EventCatalogue {

    /** Builds one event from the envelope, its {@code data} block, and the whole payload. */
    @FunctionalInterface
    private interface Factory {

        /**
         * Build the event.
         *
         * @param createdAt when the event was raised
         * @param data the event's {@code data} block
         * @param raw the whole decoded payload
         * @return the event
         */
        WebhookEvent create(String createdAt, Map<String, Object> data, Map<String, Object> raw);
    }

    private static final Map<String, Factory> BY_TYPE = Map.ofEntries(
            Map.entry(
                    EmailSentEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailSentEvent(createdAt, message(data), delivery(data, "sent"), raw)),
            Map.entry(
                    EmailDeliveredEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailDeliveredEvent(createdAt, message(data), delivery(data, "delivery"), raw)),
            Map.entry(
                    EmailBouncedEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailBouncedEvent(createdAt, message(data), failure(data, "bounce"), raw)),
            Map.entry(
                    EmailDeliveryDelayedEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailDeliveryDelayedEvent(createdAt, message(data), failure(data, "delay"), raw)),
            Map.entry(
                    EmailSuppressedEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailSuppressedEvent(createdAt, message(data), suppression(data), raw)),
            Map.entry(
                    EmailScheduledEvent.TYPE,
                    (createdAt, data, raw) -> new EmailScheduledEvent(createdAt, message(data), scheduled(data), raw)),
            Map.entry(
                    EmailFailedEvent.TYPE,
                    (createdAt, data, raw) -> new EmailFailedEvent(createdAt, message(data), sendFailure(data), raw)),
            Map.entry(
                    EmailOpenedEvent.TYPE,
                    (createdAt, data, raw) ->
                            new EmailOpenedEvent(createdAt, message(data), engagement(data, "open"), raw)),
            Map.entry(
                    EmailClickedEvent.TYPE,
                    (createdAt, data, raw) -> new EmailClickedEvent(createdAt, message(data), click(data), raw)),
            Map.entry(DomainStatusEvent.TYPE, (createdAt, data, raw) -> domainStatus(createdAt, data, raw)),
            Map.entry(WebhookStatusEvent.TYPE, (createdAt, data, raw) -> webhookStatus(createdAt, data, raw)));

    private EventCatalogue() {}

    /**
     * Parse a verified webhook payload into its typed event.
     *
     * @param payload the raw request body
     * @return the event; {@link UnknownEvent} for a type this release does not recognize
     * @throws com.mailkube.exception.MailkubeException if the payload is not a JSON object
     */
    public static WebhookEvent parse(byte[] payload) {
        Map<String, Object> raw = Collections.unmodifiableMap(
                Json.decodeObjectOrThrow(new String(payload, StandardCharsets.UTF_8), "the webhook payload"));
        String type = Json.text(raw, "type");
        String createdAt = Json.text(raw, "created_at");
        Factory factory = BY_TYPE.get(type);
        // An unrecognized type is routed, not raised: the platform introduces events without
        // forcing an upgrade on receivers that have not seen them yet.
        return factory == null ? new UnknownEvent(type, createdAt, raw) : factory.create(createdAt, block(raw), raw);
    }

    private static Map<String, Object> block(Map<String, Object> raw) {
        return Json.object(raw, "data");
    }

    private static MessageContext message(Map<String, Object> data) {
        List<String> to = data.containsKey("to") ? strings(data) : null;
        return new MessageContext(
                Json.text(data, "email_id"),
                Json.text(data, "created_at"),
                Json.text(data, "domain"),
                Json.text(data, "subject"),
                to,
                Json.text(data, "from"),
                tags(data));
    }

    private static List<String> strings(Map<String, Object> data) {
        List<String> out = new ArrayList<>();
        for (Object entry : Json.list(data, "to")) {
            out.add(entry == null ? null : entry.toString());
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Tag> tags(Map<String, Object> data) {
        List<Tag> out = new ArrayList<>();
        for (Map<String, Object> tag : Json.objects(data, "tags")) {
            out.add(new Tag(Json.text(tag, "name"), Json.text(tag, "value")));
        }
        return List.copyOf(out);
    }

    private static DeliveryContext delivery(Map<String, Object> data, String key) {
        Map<String, Object> nested = Json.object(data, key);
        return new DeliveryContext(Json.text(nested, "recipient"), Json.text(nested, "timestamp"));
    }

    private static FailureContext failure(Map<String, Object> data, String key) {
        Map<String, Object> nested = Json.object(data, key);
        return new FailureContext(
                Json.text(nested, "recipient"),
                Json.text(nested, "timestamp"),
                Json.integer(nested, "code", 0),
                Json.text(nested, "reason"));
    }

    private static EngagementContext engagement(Map<String, Object> data, String key) {
        Map<String, Object> nested = Json.object(data, key);
        // These are the only camelCase keys the API sends.
        return new EngagementContext(
                Json.text(nested, "ipAddress"), Json.text(nested, "userAgent"), Json.text(nested, "timestamp"));
    }

    private static ClickContext click(Map<String, Object> data) {
        Map<String, Object> nested = Json.object(data, "click");
        return new ClickContext(
                Json.text(nested, "ipAddress"),
                Json.text(nested, "userAgent"),
                Json.text(nested, "timestamp"),
                Json.text(nested, "link"));
    }

    private static SuppressionContext suppression(Map<String, Object> data) {
        Map<String, Object> nested = Json.object(data, "suppression");
        List<String> recipients = new ArrayList<>();
        for (Object entry : Json.list(nested, "recipients")) {
            recipients.add(entry == null ? null : entry.toString());
        }
        return new SuppressionContext(Collections.unmodifiableList(recipients), Json.text(nested, "timestamp"));
    }

    private static ScheduledContext scheduled(Map<String, Object> data) {
        Map<String, Object> nested = Json.object(data, "scheduled");
        return new ScheduledContext(Json.text(nested, "scheduled_at"), Json.text(nested, "batch_id"));
    }

    private static SendFailureContext sendFailure(Map<String, Object> data) {
        Map<String, Object> nested = Json.object(data, "failed");
        return new SendFailureContext(Json.text(nested, "reason"), Json.text(nested, "timestamp"));
    }

    private static DomainStatusEvent domainStatus(String createdAt, Map<String, Object> data, Map<String, Object> raw) {
        Map<String, Object> previous = Json.object(data, "previous");
        return new DomainStatusEvent(
                createdAt,
                Json.text(data, "domain"),
                Json.text(data, "status"),
                Json.text(data, "onboarding_state"),
                new DomainStatusPrevious(Json.text(previous, "status"), Json.text(previous, "onboarding_state")),
                raw);
    }

    private static WebhookStatusEvent webhookStatus(
            String createdAt, Map<String, Object> data, Map<String, Object> raw) {
        Map<String, Object> previous = Json.object(data, "previous");
        return new WebhookStatusEvent(
                createdAt,
                Json.text(data, "endpoint_url"),
                Json.bool(data, "is_active", false),
                Json.bool(data, "is_deleted", false),
                Json.text(data, "disabled_reason"),
                new WebhookStatusPrevious(
                        Json.bool(previous, "is_active", false),
                        Json.bool(previous, "is_deleted", false),
                        Json.text(previous, "disabled_reason")),
                raw);
    }
}
