package com.mailkube.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The new due time for one scheduled email, built through {@link #builder}.
 *
 * <p>Deliberately not shared with the batch route. Rescheduling one email may also move it into a
 * batch; rescheduling a whole batch takes a due time and nothing else. One type covering both would
 * carry a field the batch route silently ignores, which is a worse API than two small ones.
 */
public final class ScheduledEmailUpdateParams {

    private final Map<String, Object> body;

    private ScheduledEmailUpdateParams(Builder builder) {
        this.body = Collections.unmodifiableMap(new LinkedHashMap<>(builder.body));
    }

    /**
     * Start building, from an instant.
     *
     * @param scheduledAt when the send should now go out
     * @return the builder
     */
    public static Builder builder(Instant scheduledAt) {
        return new Builder(Iso.render(scheduledAt));
    }

    /**
     * Start building, from a timestamp you already hold as text.
     *
     * <p>Passing a bare {@code null} to either overload is a compile error rather than a silent
     * choice between them. That is the intended outcome: a reschedule with no due time is not a
     * request this API can serve.
     *
     * @param scheduledAt when the send should now go out, in ISO-8601
     * @return the builder
     */
    public static Builder builder(String scheduledAt) {
        return new Builder(scheduledAt);
    }

    /**
     * The request body.
     *
     * @return an unmodifiable view of the body
     */
    public Map<String, Object> body() {
        return body;
    }

    /** Builds a {@link ScheduledEmailUpdateParams}. */
    public static final class Builder {

        private final Map<String, Object> body = new LinkedHashMap<>();

        private Builder(String scheduledAt) {
            body.put("scheduled_at", scheduledAt);
        }

        /**
         * Move the email into a batch as part of the reschedule.
         *
         * @param batchId the batch label
         * @return this builder
         */
        public Builder batchId(String batchId) {
            if (batchId != null) {
                body.put("batch_id", batchId);
            }
            return this;
        }

        /**
         * Finish building.
         *
         * @return the parameters
         */
        public ScheduledEmailUpdateParams build() {
            return new ScheduledEmailUpdateParams(this);
        }
    }
}
