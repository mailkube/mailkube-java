package com.mailkube.model;

/**
 * A send accepted for later transmission.
 *
 * @param scheduledAt when the send is due
 * @param batchId the batch label, or null when the send was not grouped
 */
public record ScheduledContext(String scheduledAt, String batchId) {}
