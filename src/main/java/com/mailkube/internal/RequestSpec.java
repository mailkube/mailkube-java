package com.mailkube.internal;

import java.util.Map;

/**
 * A fully-built request, ready to send.
 *
 * @param path relative to the base URL, or an absolute URL the API itself issued (a pagination
 *     link); {@link Config#buildUrl(String)} refuses an absolute URL off the configured origin
 * @param method the HTTP method
 * @param body the JSON request body, or null for a body-less request
 * @param headers per-request headers, merged over the client defaults
 */
public record RequestSpec(String path, String method, Map<String, Object> body, Map<String, String> headers) {

    /**
     * A POST with a body and no extra headers.
     *
     * @param path the path to request
     * @param body the JSON request body
     * @return the spec
     */
    public static RequestSpec post(String path, Map<String, Object> body) {
        return new RequestSpec(path, "POST", body, Map.of());
    }

    /**
     * The same request with one header added.
     *
     * @param name the header name
     * @param value the header value
     * @return a new spec
     */
    public RequestSpec withHeader(String name, String value) {
        return new RequestSpec(path, method, body, Map.of(name, value));
    }
}
