package com.mailkube.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters for a scheduled-email listing, built through {@link #builder()}.
 *
 * <p>Every filter is optional and an omitted one is simply not applied, so
 * {@code ScheduledEmailListParams.none()} lists everything the window contains.
 *
 * <p>Like {@link SendEmailParams}, the builder assembles the wire form directly. Filters become a
 * {@code Map<String, String>} here rather than in the transport, so the one rule for rendering a
 * list (comma-joined, not a repeated parameter) and the one rule for rendering a timestamp both
 * live with the type that defines them.
 */
public final class ScheduledEmailListParams {

    private final Map<String, String> filters;

    private ScheduledEmailListParams(Builder builder) {
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.filters));
    }

    /**
     * Start building a filter set.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * No filters at all.
     *
     * @return an empty filter set
     */
    public static ScheduledEmailListParams none() {
        return builder().build();
    }

    /**
     * The filters in wire form.
     *
     * @return an unmodifiable view of the query parameters
     */
    public Map<String, String> filters() {
        return filters;
    }

    /** Builds a {@link ScheduledEmailListParams}. Every setter returns {@code this}, so calls chain. */
    public static final class Builder {

        private final Map<String, String> filters = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Restrict the listing to one status.
         *
         * <p>Only {@code scheduled}, {@code canceled} and {@code failed} can be listed: a sent
         * email has left the collection, so {@code "sent"} is a validation error server-side rather
         * than an empty result.
         *
         * @param status the status to match
         * @return this builder
         */
        public Builder status(String status) {
            return put("status", status);
        }

        /**
         * Restrict the listing to several statuses.
         *
         * <p>Comma-joined into one parameter rather than repeated, which is what the API expects
         * and what keeps the transport seam a flat string map.
         *
         * @param statuses the statuses to match
         * @return this builder
         */
        public Builder status(List<String> statuses) {
            return statuses == null || statuses.isEmpty() ? this : put("status", String.join(",", statuses));
        }

        /**
         * Restrict the listing to one batch.
         *
         * @param batchId the batch label
         * @return this builder
         */
        public Builder batchId(String batchId) {
            return put("batch_id", batchId);
        }

        /**
         * Only emails due at or after this instant.
         *
         * @param scheduledAtGte the lower bound
         * @return this builder
         */
        public Builder scheduledAtGte(Instant scheduledAtGte) {
            return put("scheduled_at_gte", Iso.render(scheduledAtGte));
        }

        /**
         * Only emails due at or after this instant, as text you already hold.
         *
         * @param scheduledAtGte the lower bound
         * @return this builder
         */
        public Builder scheduledAtGte(String scheduledAtGte) {
            return put("scheduled_at_gte", scheduledAtGte);
        }

        /**
         * Only emails due at or before this instant.
         *
         * @param scheduledAtLte the upper bound
         * @return this builder
         */
        public Builder scheduledAtLte(Instant scheduledAtLte) {
            return put("scheduled_at_lte", Iso.render(scheduledAtLte));
        }

        /**
         * Only emails due at or before this instant, as text you already hold.
         *
         * @param scheduledAtLte the upper bound
         * @return this builder
         */
        public Builder scheduledAtLte(String scheduledAtLte) {
            return put("scheduled_at_lte", scheduledAtLte);
        }

        /**
         * The 1-based page to fetch.
         *
         * <p>Only useful for fetching one page directly. Prefer
         * {@code scheduledEmails().iterateAll(...)}, which follows the server's own links.
         *
         * @param page the page number
         * @return this builder
         */
        public Builder page(int page) {
            return put("page", Integer.toString(page));
        }

        /**
         * Finish building.
         *
         * @return the filters
         */
        public ScheduledEmailListParams build() {
            return new ScheduledEmailListParams(this);
        }

        private Builder put(String key, String value) {
            if (value != null) {
                filters.put(key, value);
            }
            return this;
        }
    }
}
