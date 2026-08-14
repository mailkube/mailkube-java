package com.mailkube.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully-built request, ready to send.
 *
 * @param path relative to the base URL, or an absolute URL the API itself issued (a pagination
 *     link); {@link Config#buildUrl(String)} refuses an absolute URL off the configured origin
 * @param method the HTTP method
 * @param body the JSON request body, or null for a body-less request
 * @param query filter parameters, already stringified; empty for most requests
 * @param headers per-request headers, merged over the client defaults
 */
public record RequestSpec(
        String path, String method, Map<String, Object> body, Map<String, String> query, Map<String, String> headers) {

    /**
     * Normalise the two maps so no caller has to, and freeze them so no caller can mutate a spec
     * another thread is already sending.
     *
     * <p>{@code body} is deliberately left nullable: an absent body and an empty one are different
     * requests, and only the first must send no bytes at all.
     */
    public RequestSpec {
        query = query == null ? Map.of() : Map.copyOf(query);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * A POST with a body.
     *
     * @param path the path to request
     * @param body the JSON request body
     * @return the spec
     */
    public static RequestSpec post(String path, Map<String, Object> body) {
        return new RequestSpec(path, "POST", body, Map.of(), Map.of());
    }

    /**
     * A GET with no filters.
     *
     * @param path the path to request
     * @return the spec
     */
    public static RequestSpec get(String path) {
        return new RequestSpec(path, "GET", null, Map.of(), Map.of());
    }

    /**
     * A GET with filters.
     *
     * @param path the path to request
     * @param query the filters, already stringified
     * @return the spec
     */
    public static RequestSpec get(String path, Map<String, String> query) {
        return new RequestSpec(path, "GET", null, query, Map.of());
    }

    /**
     * A PATCH with a body.
     *
     * @param path the path to request
     * @param body the JSON request body
     * @return the spec
     */
    public static RequestSpec patch(String path, Map<String, Object> body) {
        return new RequestSpec(path, "PATCH", body, Map.of(), Map.of());
    }

    /**
     * A DELETE.
     *
     * @param path the path to request
     * @return the spec
     */
    public static RequestSpec delete(String path) {
        return new RequestSpec(path, "DELETE", null, Map.of(), Map.of());
    }

    /**
     * The same request with one header added, keeping any already set.
     *
     * <p>Replacing the map rather than merging into it is a bug that stays invisible while exactly
     * one header is ever added, and silently drops the first the moment a second appears.
     *
     * @param name the header name
     * @param value the header value
     * @return a new spec
     */
    public RequestSpec withHeader(String name, String value) {
        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.put(name, value);
        return new RequestSpec(path, method, body, query, merged);
    }
}
