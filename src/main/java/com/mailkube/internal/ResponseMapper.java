package com.mailkube.internal;

import java.util.Map;

/**
 * Turns a decoded response body into a model.
 *
 * <p>This is what lets one generic request verb serve every listing and item route without the
 * transport knowing a single model type. The transport performs the round trip and maps failures;
 * the caller supplies the one function that knows the shape it asked for.
 *
 * @param <T> the model produced
 */
@FunctionalInterface
public interface ResponseMapper<T> {

    /**
     * Build the model from a decoded 2xx body.
     *
     * @param body the decoded response body
     * @return the model
     */
    T map(Map<String, Object> body);
}
