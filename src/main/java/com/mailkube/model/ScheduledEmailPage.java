package com.mailkube.model;

import java.util.List;

/**
 * One page of scheduled emails.
 *
 * @param pagination page metadata, including the adjacent-page links
 * @param data the scheduled emails on this page
 */
public record ScheduledEmailPage(Pagination pagination, List<ScheduledEmail> data) {

    /** Freeze the row list so a page handed to several threads cannot be mutated underneath them. */
    public ScheduledEmailPage {
        data = data == null ? List.of() : List.copyOf(data);
    }

    /**
     * Whether the server offered a link to a following page.
     *
     * @return true when another page exists
     */
    public boolean hasMore() {
        return pagination != null
                && pagination.steps() != null
                && pagination.steps().next() != null;
    }
}
