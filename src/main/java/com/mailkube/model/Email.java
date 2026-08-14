package com.mailkube.model;

/**
 * The result of a successful send.
 *
 * <p>A <b>scheduled</b> send (one carrying {@code scheduledAt}) is acknowledged with 202 and a
 * richer body; {@code status}, {@code scheduledAt} and {@code batchId} are populated only then,
 * and {@link #isScheduled()} is the discriminator. An immediate send leaves all three null.
 *
 * <p>This is the worked example of the contract's <b>widen, never union</b> rule: one call can
 * return two shapes, and adding optional components plus a predicate keeps every existing caller
 * compiling, where returning one of two types would not.
 *
 * <p>Timestamps stay the verbatim ISO-8601 strings the server sent. The SDK does not validate or
 * reinterpret server data; call {@code Instant.parse} yourself if you want an object. Transport
 * metadata (response headers, the request id) belongs on the exception, not here.
 *
 * @param id the accepted message's UUID
 * @param messageId the RFC Message-ID, or null when the deployment returns none
 * @param idempotentReplayed true when this response replays an earlier identical request
 * @param status the scheduled email's status, on a scheduled ack only
 * @param scheduledAt when the send is due, on a scheduled ack only
 * @param batchId the batch label the send was grouped under, on a scheduled ack only
 */
public record Email(
        String id, String messageId, boolean idempotentReplayed, String status, String scheduledAt, String batchId) {

    /**
     * Whether the send was accepted for later delivery rather than sent now.
     *
     * @return true for a scheduled send
     */
    public boolean isScheduled() {
        return scheduledAt != null;
    }
}
