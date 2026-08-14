package com.mailkube.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Percent-encodes one interpolated path segment.
 *
 * <p>Not cosmetic. An identifier carrying an encoded {@code /} or {@code ?} would otherwise
 * re-target the request at a different route: {@code scheduled-emails/} + {@code "x/../../admin"}
 * is a request to somewhere nobody intended. Every verb that interpolates a caller-supplied value
 * into a path goes through here.
 *
 * <p>Named {@code PathSegment} rather than {@code Paths} on purpose: {@code java.nio.file.Paths} is
 * one unlucky import away, and a reader who mistakes one for the other misreads the URL layer.
 */
public final class PathSegment {

    private PathSegment() {}

    /**
     * Encode a value for use as a single path segment.
     *
     * <p>{@link URLEncoder} is a <i>form</i> encoder: it renders a space as {@code +}, which is
     * correct in a query string and wrong in a path, where {@code +} is a literal plus. The
     * substitution below is what makes it usable here.
     *
     * @param value the raw segment, such as a resource id
     * @return the encoded segment
     */
    public static String of(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
