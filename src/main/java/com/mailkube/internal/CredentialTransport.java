package com.mailkube.internal;

/**
 * What the credential resource needs: one typed request, describing the client's own key.
 *
 * <p>A sibling of {@link ScheduledTransport} with the same single member, rather than a reuse of
 * it. The contract's rule is that a new capability adds an interface and never widens or borrows an
 * existing one, so the credential resource does not acquire a dependency named after scheduled
 * sends, and a test can substitute either capability without touching the other.
 */
public interface CredentialTransport {
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
