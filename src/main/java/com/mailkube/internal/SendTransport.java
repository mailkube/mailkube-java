package com.mailkube.internal;

import com.mailkube.model.Email;

/**
 * What a resource needs of its transport: the one verb it calls, and nothing else.
 *
 * <p>There is deliberately one interface per capability rather than one wide one. A resource that
 * only sends must not acquire a dependency on every other verb. A new capability adds an
 * interface; it never widens an existing one.
 *
 * <p>This is also the test seam that does not go through HTTP at all, which is why the suite can
 * exercise resource behaviour without a server.
 */
public interface SendTransport {

    /**
     * Perform a send request and build the accepted-send result.
     *
     * @param spec the request to perform
     * @return the accepted-send result
     */
    Email sendEmail(RequestSpec spec);
}
