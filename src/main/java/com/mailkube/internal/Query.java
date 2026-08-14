package com.mailkube.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Renders an already-stringified filter map into a query string.
 *
 * <p>Encoding only. Values arrive as text because the parameter builders in {@code com.mailkube
 * .model} own every conversion — one timestamp renderer, one list-joining rule — and doing it there
 * keeps this class with a single job and the conversions with the types that define them.
 *
 * <p>Unlike a path segment, form encoding is exactly right here: {@code +} is a space in a query
 * string, so {@link URLEncoder} needs no correction.
 */
public final class Query {

    private Query() {}

    /**
     * Render filters as a query string.
     *
     * @param filters the filter map, already stringified; may be empty
     * @return the query string without its leading {@code ?}, or null when there is nothing to send
     */
    public static String render(Map<String, String> filters) {
        if (filters.isEmpty()) {
            return null;
        }
        StringJoiner joined = new StringJoiner("&");
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            joined.add(encode(filter.getKey()) + "=" + encode(filter.getValue()));
        }
        return joined.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
