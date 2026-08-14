package com.mailkube;

import com.mailkube.internal.RequestSpec;
import com.mailkube.internal.SendTransport;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;

/**
 * The {@code emails} namespace, reached as {@code client.emails()}.
 *
 * <p>This is the worked example every new resource copies. Note what it does <i>not</i> do: it
 * holds no configuration, performs no I/O, and never imports {@code java.net.http}. It depends
 * only on the narrowest transport interface its verbs need.
 */
public final class Emails {

    private final SendTransport transport;

    /**
     * Bind the resource to the transport that performs its requests.
     *
     * @param transport the transport, or a test double satisfying the interface
     */
    Emails(SendTransport transport) {
        this.transport = transport;
    }

    /**
     * Send an email.
     *
     * <p>Supply {@code html} and/or {@code text} for a raw send, or {@code templateId} for a saved
     * template. {@code idempotencyKey} travels as the {@code Idempotency-Key} header rather than
     * in the body. Setting {@code scheduledAt} schedules the send instead of delivering it now;
     * the result then reports {@link Email#isScheduled()}.
     *
     * @param params the send parameters, from {@link SendEmailParams#builder}
     * @return the accepted-send result
     */
    public Email send(SendEmailParams params) {
        RequestSpec spec = RequestSpec.post("emails", params.body());
        if (params.idempotencyKey() != null) {
            spec = spec.withHeader("Idempotency-Key", params.idempotencyKey());
        }
        return transport.sendEmail(spec);
    }
}
