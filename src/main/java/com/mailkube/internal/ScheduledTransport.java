package com.mailkube.internal;

/**
 * What a resource needs to perform a typed request against any route.
 *
 * <p>This is the second capability, and it is a <i>sibling</i> of {@link SendTransport} rather than
 * an extension of it. The contract's rule is that a new capability adds an interface and never
 * widens an existing one: the emails resource sends, and must not acquire a dependency on listing,
 * fetching, updating or cancelling just because those verbs came along later.
 *
 * <p>One method covers every scheduled-email verb because the variation between them lives in the
 * {@link RequestSpec} and the {@link ResponseMapper}, not in the transport. That is a narrow
 * interface, not a wide one: a caller depends on exactly one member, and it is the one it uses.
 */
public interface ScheduledTransport {

    /**
     * Perform a request and map the decoded body.
     *
     * @param spec the request to perform
     * @param mapper builds the model from the decoded 2xx body
     * @param <T> the model produced
     * @return the model
     */
    <T> T request(RequestSpec spec, ResponseMapper<T> mapper);
}
