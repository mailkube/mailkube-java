package com.mailkube.model;

/**
 * A free-form name/value tag attached to an outgoing email.
 *
 * <p>Tags are forwarded to the server, which denormalizes them onto the sending log so you can
 * filter, export and dashboard sends by tag, and so they ride along on delivery webhooks. Tag
 * values are not encrypted, so do not put personal data in them.
 *
 * @param name the tag name
 * @param value the tag value; it may be empty
 */
public record Tag(String name, String value) {

    /**
     * A tag with no value.
     *
     * @param name the tag name
     * @return the tag
     */
    public static Tag of(String name) {
        return new Tag(name, "");
    }
}
