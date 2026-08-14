package com.mailkube.model;

import java.util.List;

/**
 * A scheduled email that has not been delivered yet.
 *
 * <p>Timestamps are the server's own strings, verbatim. The SDK does not reinterpret server data;
 * call {@link java.time.Instant#parse} yourself if you want an object.
 *
 * @param id the scheduled email's UUID, the same id the send acknowledgement returned
 * @param messageId the RFC Message-ID the message will carry
 * @param object the resource discriminator, always {@code "scheduled_email"}
 * @param status one of {@code scheduled}, {@code canceled}, {@code sent} or {@code failed}; a
 *     plain string rather than an enum, because a closed set turns a new server-side value into a
 *     parse error on an already-released client
 * @param scheduledAt when the send is due
 * @param createdAt when the send was accepted
 * @param batchId the batch label this send was grouped under, if any
 * @param subject the message subject
 * @param recipients a <b>summary string</b>, not a list: the first recipient plus an overflow
 *     count, such as {@code "a@b.com +2"}. The full recipient list stays server-side with the
 *     frozen payload, so this mirrors the wire rather than inventing a shape the API never sends
 * @param topic the mailing-list topic slug the send is attributed to, if any
 * @param tags the message tags attached at send time
 */
public record ScheduledEmail(
        String id,
        String messageId,
        String object,
        String status,
        String scheduledAt,
        String createdAt,
        String batchId,
        String subject,
        String recipients,
        String topic,
        List<Tag> tags) {

    /** Freeze the tag list so a page handed to several threads cannot be mutated underneath them. */
    public ScheduledEmail {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
