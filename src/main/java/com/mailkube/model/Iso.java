package com.mailkube.model;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * The one place a caller-supplied timestamp becomes a wire string.
 *
 * <p>The contract requires every SDK to accept either a string or the language's own datetime type
 * on the way in, and to render through a <i>single</i> serializer. A second formatter is how two
 * verbs start disagreeing about what a timestamp looks like on the wire, and the disagreement is
 * invisible until a server-side parser rejects one of them.
 *
 * <p>Only the datetime half needs rendering: a string the caller already holds is passed through
 * untouched by the setter that takes it. The SDK makes values transmissible, it does not validate
 * them, and the server's error for a malformed timestamp is better than one this class could give.
 *
 * <p>It lives in the types layer, and is exported, because both layers above it need it: the
 * parameter builders in this package render a caller's {@code Instant} into the body they assemble,
 * and the resource layer renders one for a route whose whole payload is a timestamp. The obvious
 * alternative homes are both worse. In {@code internal} it would force a {@code model -> internal}
 * import and invert the layering the module descriptor enforces; duplicated in both places it would
 * stop being a single serializer, which is the one property this class exists to hold.
 *
 * <p>Being public is therefore a consequence rather than a goal, but it is a useful one: a caller
 * holding an {@code Instant} can produce exactly the wire form the API will receive.
 */
public final class Iso {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private Iso() {}

    /**
     * Render an instant for the wire.
     *
     * <p>Takes {@link Instant} rather than {@code Temporal} on purpose. A {@code LocalDateTime}
     * names no moment, so accepting one would only let a caller fail at runtime; a caller holding a
     * zoned type converts with {@code .toInstant()} and the ambiguity stays where they can see it.
     *
     * @param value the instant, or null
     * @return the wire text, or null when {@code value} is null
     */
    public static String render(Instant value) {
        return value == null ? null : ISO.format(value);
    }
}
