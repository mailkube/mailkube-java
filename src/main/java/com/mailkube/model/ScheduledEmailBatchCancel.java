package com.mailkube.model;

/**
 * The result of cancelling a whole batch.
 *
 * <p>Deliberately a separate type from {@link ScheduledEmailBatchUpdate} rather than one record
 * covering both. They are two verbs returning two stable wire shapes, and a merged record would
 * carry fields that are permanently null on half its uses. The contract's "widen, never union" rule
 * governs the evolution of one return type; it does not license unifying two.
 *
 * @param object the resource discriminator, always {@code "scheduled_email.batch"}
 * @param batchId the batch that was targeted
 * @param canceledCount how many pending emails were affected; an unknown batch is a no-op
 *     reporting zero, not an error
 */
public record ScheduledEmailBatchCancel(String object, String batchId, int canceledCount) {}
