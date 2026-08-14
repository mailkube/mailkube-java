package com.mailkube.model;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parameters for a send, built through {@link #builder(String, List, String)}.
 *
 * <p>Java has no keyword arguments, so a builder is how a call site stays readable with eighteen
 * optional fields. The three required ones are constructor parameters of the builder rather than
 * setters, so an incomplete send does not compile.
 *
 * <p><b>The builder assembles the wire body directly</b>, rather than holding eighteen fields and
 * mapping them later. That is deliberate: it gives one place where a null becomes an absent key
 * (rather than a {@code null} on the wire), it keeps the mapping next to the setter that owns it,
 * and it means this class has no accessors to write, test or keep in step.
 */
public final class SendEmailParams {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final Map<String, Object> body;
    private final String idempotencyKey;

    private SendEmailParams(Builder builder) {
        this.body = Collections.unmodifiableMap(new LinkedHashMap<>(builder.body));
        this.idempotencyKey = builder.idempotencyKey;
    }

    /**
     * Start building a send.
     *
     * @param from the sender address, optionally with a display name
     * @param to the recipient addresses
     * @param subject the subject line
     * @return the builder
     */
    public static Builder builder(String from, List<String> to, String subject) {
        return new Builder(from, to, subject);
    }

    /**
     * The wire body, with unset fields already absent.
     *
     * @return an unmodifiable view of the request body
     */
    public Map<String, Object> body() {
        return body;
    }

    /**
     * The idempotency key, which travels as a header rather than in the body.
     *
     * @return the key, or null when the caller set none
     */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Builds a {@link SendEmailParams}. Every setter returns {@code this}, so calls chain. */
    public static final class Builder {

        private final Map<String, Object> body = new LinkedHashMap<>();
        private String idempotencyKey;

        private Builder(String from, List<String> to, String subject) {
            put("from", from);
            put("to", to);
            put("subject", subject);
        }

        /**
         * The HTML body, for a raw-content send.
         *
         * @param html the HTML
         * @return this builder
         */
        public Builder html(String html) {
            return put("html", html);
        }

        /**
         * The plain-text body, for a raw-content send.
         *
         * @param text the text
         * @return this builder
         */
        public Builder text(String text) {
            return put("text", text);
        }

        /**
         * Carbon-copy recipients.
         *
         * @param cc the addresses
         * @return this builder
         */
        public Builder cc(List<String> cc) {
            return put("cc", cc);
        }

        /**
         * Blind carbon-copy recipients.
         *
         * @param bcc the addresses
         * @return this builder
         */
        public Builder bcc(List<String> bcc) {
            return put("bcc", bcc);
        }

        /**
         * The Reply-To addresses.
         *
         * @param replyTo the addresses
         * @return this builder
         */
        public Builder replyTo(List<String> replyTo) {
            return put("reply_to", replyTo);
        }

        /**
         * Custom message headers, e.g. In-Reply-To for threading.
         *
         * @param headers the headers
         * @return this builder
         */
        public Builder headers(Map<String, String> headers) {
            return put("headers", headers);
        }

        /**
         * File attachments. Raw content is base64-encoded here, once.
         *
         * @param attachments the attachments
         * @return this builder
         */
        public Builder attachments(List<Attachment> attachments) {
            if (attachments == null || attachments.isEmpty()) {
                return this;
            }
            List<Object> encoded = new ArrayList<>(attachments.size());
            for (Attachment attachment : attachments) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("filename", attachment.filename());
                entry.put("content", Base64.getEncoder().encodeToString(attachment.content()));
                if (attachment.contentType() != null) {
                    entry.put("content_type", attachment.contentType());
                }
                encoded.add(entry);
            }
            return put("attachments", encoded);
        }

        /**
         * Free-form name/value tags forwarded to the server.
         *
         * @param tags the tags
         * @return this builder
         */
        public Builder tags(List<Tag> tags) {
            if (tags == null || tags.isEmpty()) {
                return this;
            }
            List<Object> encoded = new ArrayList<>(tags.size());
            for (Tag tag : tags) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", tag.name());
                entry.put("value", tag.value());
                encoded.add(entry);
            }
            return put("tags", encoded);
        }

        /**
         * The UUID of a saved template to render instead of raw content.
         *
         * @param templateId the template id
         * @return this builder
         */
        public Builder templateId(String templateId) {
            return put("template_id", templateId);
        }

        /**
         * A template version number, or "latest".
         *
         * @param templateVersion the version
         * @return this builder
         */
        public Builder templateVersion(String templateVersion) {
            return put("template_version", templateVersion);
        }

        /**
         * Values for the template's placeholders.
         *
         * @param variables the variables
         * @return this builder
         */
        public Builder variables(Map<String, String> variables) {
            return put("variables", variables);
        }

        /**
         * The mailing-list topic slug this send is attributed to.
         *
         * @param topic the topic slug
         * @return this builder
         */
        public Builder topic(String topic) {
            return put("topic", topic);
        }

        /**
         * Schedule the send instead of delivering it now.
         *
         * @param scheduledAt when the send is due
         * @return this builder
         */
        public Builder scheduledAt(Instant scheduledAt) {
            return scheduledAt == null ? this : put("scheduled_at", ISO.format(scheduledAt));
        }

        /**
         * Group several scheduled sends under one label.
         *
         * @param batchId the batch label
         * @return this builder
         */
        public Builder batchId(String batchId) {
            return put("batch_id", batchId);
        }

        /**
         * Make a retry safe: the API replays the original response instead of sending twice.
         *
         * <p>This one is not a body field. It travels as the {@code Idempotency-Key} header.
         *
         * @param idempotencyKey the key
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Finish building.
         *
         * @return the immutable parameters
         */
        public SendEmailParams build() {
            return new SendEmailParams(this);
        }

        /**
         * The single place a null becomes an absent key rather than a {@code null} on the wire.
         *
         * <p>Every setter above routes through here, which is why adding a field is one method and
         * cannot get the omission rule wrong.
         */
        private Builder put(String key, Object value) {
            if (value != null) {
                body.put(key, value);
            }
            return this;
        }
    }
}
