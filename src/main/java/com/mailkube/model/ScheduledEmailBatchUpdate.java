package com.mailkube.model;

/**
 * The result of rescheduling a whole batch.
 *
 * <p>See {@link ScheduledEmailBatchCancel} for why the two batch results are separate types.
 *
 * @param object the resource discriminator, always {@code "scheduled_email.batch"}
 * @param batchId the batch that was targeted
 * @param rescheduledCount how many pending emails were moved; an unknown batch is a no-op
 *     reporting zero, not an error
 * @param scheduledAt the new due time applied to every moved email
 */
public record ScheduledEmailBatchUpdate(String object, String batchId, int rescheduledCount, String scheduledAt) {}
