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
 * <p>Deliberately package-private: every parameter builder lives in this package, so nothing outside
 * it needs this. Keeping it here avoids both a permanent addition to the exported API and a
 * {@code model -> internal} dependency, which would invert the layering.
 */
final class Iso {

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
    static String render(Instant value) {
        return value == null ? null : ISO.format(value);
    }
}
