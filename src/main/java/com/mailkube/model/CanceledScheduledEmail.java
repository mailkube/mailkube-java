package com.mailkube.model;

/**
 * The acknowledgement of cancelling one scheduled email.
 *
 * @param id the cancelled scheduled email's UUID
 * @param object the resource discriminator, always {@code "scheduled_email"}
 * @param status the resulting status, always {@code "canceled"}
 */
public record CanceledScheduledEmail(String id, String object, String status) {}
