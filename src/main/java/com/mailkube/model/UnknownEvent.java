package com.mailkube.model;

import java.util.Map;

/**
 * An event whose {@code type} this SDK release does not recognize.
 *
 * <p>Not an error, and deliberately so: the platform must be able to introduce an event type
 * without breaking every receiver that has not upgraded. The type and the whole payload are both
 * here, so a handler can act on a new event before a typed record for it exists.
 *
 * <p>It is also why {@code type} is a component here but a constant elsewhere: this is the one
 * event whose type is not known at compile time.
 *
 * @param type the event type, as sent
 * @param createdAt when the event was raised
 * @param raw the whole decoded payload
 */
public record UnknownEvent(String type, String createdAt, Map<String, Object> raw) implements WebhookEvent {}
