package com.mailkube;

import com.mailkube.internal.Json;
import com.mailkube.internal.PathSegment;
import com.mailkube.internal.RequestSpec;
import com.mailkube.internal.ScheduledTransport;
import com.mailkube.model.CanceledScheduledEmail;
import com.mailkube.model.Iso;
import com.mailkube.model.PageSteps;
import com.mailkube.model.Pagination;
import com.mailkube.model.ScheduledEmail;
import com.mailkube.model.ScheduledEmailBatchCancel;
import com.mailkube.model.ScheduledEmailBatchUpdate;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.ScheduledEmailPage;
import com.mailkube.model.ScheduledEmailUpdateParams;
import com.mailkube.model.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The {@code scheduledEmails} namespace, reached as {@code client.scheduledEmails()}.
 *
 * <p>A send carrying {@code scheduledAt} is accepted but not delivered yet. Until it is due it
 * lives in this collection, where it can be listed, inspected, rescheduled or cancelled: one at a
 * time, or a whole batch at once through {@link #batches()}.
 *
 * <p>This resource is where the wire shape becomes a model. The mapping lives here rather than on
 * the records because the JSON codec is an internal detail: a {@code from(Map)} factory on an
 * exported record would make the types layer depend on the internal one and invert the layering the
 * module descriptor enforces. The verb that knows which shape it asked for supplies the mapping.
 */
public final class ScheduledEmails {

    private static final String COLLECTION = "scheduled-emails";
    private static final String BATCHES = "scheduled-emails/batches";

    private final ScheduledTransport transport;
    private final Batches batches;

    /**
     * Bind the resource and its batch sub-namespace to the transport that performs their requests.
     *
     * @param transport the transport, or a test double satisfying the interface
     */
    ScheduledEmails(ScheduledTransport transport) {
        this.transport = transport;
        this.batches = new Batches(transport);
    }

    /**
     * The batch operations.
     *
     * @return the {@code client.scheduledEmails().batches()} namespace
     */
    public Batches batches() {
        return batches;
    }

    /**
     * List one page of scheduled emails.
     *
     * @param params the filters, from {@link ScheduledEmailListParams#builder()}
     * @return one page, plus the pagination metadata
     */
    public ScheduledEmailPage list(ScheduledEmailListParams params) {
        return transport.request(RequestSpec.get(COLLECTION, params.filters()), ScheduledEmails::toPage);
    }

    /**
     * Every scheduled email matching the filters, across all pages.
     *
     * <p>Pages are fetched lazily, by following the links the API returns rather than by
     * incrementing a counter, so the server can change its pagination scheme without breaking a
     * released client. Nothing is requested until the stream is consumed, and abandoning it early
     * costs no further request.
     *
     * @param params the filters, as for {@link #list}; a {@code page} sets the starting page
     * @return the matching emails, page after page
     */
    public Stream<ScheduledEmail> iterateAll(ScheduledEmailListParams params) {
        return StreamSupport.stream(new PageWalk(params), false).flatMap(page -> page.data().stream());
    }

    /**
     * Retrieve one scheduled email.
     *
     * @param emailId the id the scheduled-send acknowledgement returned
     * @return the scheduled email
     */
    public ScheduledEmail get(String emailId) {
        return transport.request(RequestSpec.get(item(COLLECTION, emailId)), ScheduledEmails::toScheduledEmail);
    }

    /**
     * Reschedule one scheduled email.
     *
     * @param emailId the id the scheduled-send acknowledgement returned
     * @param params the new due time, and optionally a batch to move it into
     * @return the updated scheduled email
     */
    public ScheduledEmail update(String emailId, ScheduledEmailUpdateParams params) {
        return transport.request(
                RequestSpec.patch(item(COLLECTION, emailId), params.body()), ScheduledEmails::toScheduledEmail);
    }

    /**
     * Cancel one scheduled email.
     *
     * @param emailId the id the scheduled-send acknowledgement returned
     * @return the cancellation acknowledgement
     */
    public CanceledScheduledEmail cancel(String emailId) {
        return transport.request(
                RequestSpec.delete(item(COLLECTION, emailId)),
                body -> new CanceledScheduledEmail(
                        Json.text(body, "id"), Json.text(body, "object"), Json.text(body, "status")));
    }

    /**
     * The {@code batches} sub-namespace, reached as {@code client.scheduledEmails().batches()}.
     *
     * <p>Both verbs are no-ops on an unknown batch, reporting a count of zero rather than raising:
     * a batch is a label the caller chose at send time, not a resource with its own lifecycle.
     */
    public static final class Batches {

        private final ScheduledTransport transport;

        private Batches(ScheduledTransport transport) {
            this.transport = transport;
        }

        /**
         * Reschedule every pending email in a batch.
         *
         * @param batchId the batch label the sends were grouped under
         * @param scheduledAt when they should now go out
         * @return the batch result, including how many emails were moved
         */
        public ScheduledEmailBatchUpdate update(String batchId, Instant scheduledAt) {
            return update(batchId, Iso.render(scheduledAt));
        }

        /**
         * Reschedule every pending email in a batch, from a timestamp you already hold as text.
         *
         * <p>The batch route takes a due time and nothing else, so it takes the value directly
         * rather than a parameters object. There is no second field for a builder to guard.
         *
         * @param batchId the batch label the sends were grouped under
         * @param scheduledAt when they should now go out, in ISO-8601
         * @return the batch result, including how many emails were moved
         */
        public ScheduledEmailBatchUpdate update(String batchId, String scheduledAt) {
            RequestSpec spec = RequestSpec.patch(item(BATCHES, batchId), Map.of("scheduled_at", scheduledAt));
            return transport.request(
                    spec,
                    body -> new ScheduledEmailBatchUpdate(
                            Json.text(body, "object"),
                            Json.text(body, "batch_id"),
                            Json.integer(body, "rescheduled_count", 0),
                            Json.text(body, "scheduled_at")));
        }

        /**
         * Cancel every pending email in a batch.
         *
         * @param batchId the batch label the sends were grouped under
         * @return the batch result, including how many emails were cancelled
         */
        public ScheduledEmailBatchCancel cancel(String batchId) {
            return transport.request(
                    RequestSpec.delete(item(BATCHES, batchId)),
                    body -> new ScheduledEmailBatchCancel(
                            Json.text(body, "object"),
                            Json.text(body, "batch_id"),
                            Json.integer(body, "canceled_count", 0)));
        }
    }

    /**
     * Walks the pages of one listing, following the server's own {@code next} links.
     *
     * <p>A spliterator rather than an iterator: the stream is the only consumer, and an iterator
     * would have to answer "is there another page?" separately from fetching it, which on a walk
     * driven by response links means either fetching ahead or carrying a state flag whose exhausted
     * branch nothing can ever reach.
     *
     * <p>Lazy in both directions. No request is made until the stream is consumed, and each page is
     * fetched only when the previous one runs out, so a caller that stops early stops paying.
     */
    private final class PageWalk extends Spliterators.AbstractSpliterator<ScheduledEmailPage> {

        private final ScheduledEmailListParams params;
        private ScheduledEmailPage current;
        private boolean started;

        private PageWalk(ScheduledEmailListParams params) {
            // The total is unknown until the last page says so, which is exactly what an unsized
            // spliterator means. `pagination.totalCount` counts rows, not pages, and trusting it
            // would reintroduce the counter this walk exists to avoid.
            super(Long.MAX_VALUE, ORDERED | NONNULL);
            this.params = params;
        }

        /**
         * Fetch the next page, if the previous one said there is one.
         *
         * @param action receives the page
         * @return false once the walk is over
         */
        @Override
        public boolean tryAdvance(Consumer<? super ScheduledEmailPage> action) {
            if (started && !current.hasMore()) {
                return false;
            }
            // The link arrives absolute and complete. `Config.buildUrl` refuses one that is not on
            // the configured origin, so a `next` pointing elsewhere cannot carry the API key away.
            current = started
                    ? transport.request(
                            RequestSpec.get(current.pagination().steps().next()), ScheduledEmails::toPage)
                    : list(params);
            started = true;
            action.accept(current);
            return true;
        }
    }

    private static String item(String base, String identifier) {
        return base + "/" + PathSegment.of(identifier);
    }

    private static ScheduledEmailPage toPage(Map<String, Object> body) {
        Map<String, Object> pagination = Json.object(body, "pagination");
        Map<String, Object> steps = Json.object(pagination, "steps");
        List<ScheduledEmail> data = new ArrayList<>();
        for (Map<String, Object> row : Json.objects(body, "data")) {
            data.add(toScheduledEmail(row));
        }
        return new ScheduledEmailPage(
                new Pagination(
                        new PageSteps(Json.text(steps, "next"), Json.text(steps, "previous")),
                        Json.integer(pagination, "total_count", 0),
                        Json.integer(pagination, "current_page", 1)),
                data);
    }

    private static ScheduledEmail toScheduledEmail(Map<String, Object> body) {
        return new ScheduledEmail(
                Json.text(body, "id"),
                Json.text(body, "message_id"),
                Json.text(body, "object"),
                Json.text(body, "status"),
                Json.text(body, "scheduled_at"),
                Json.text(body, "created_at"),
                Json.text(body, "batch_id"),
                Json.text(body, "subject"),
                Json.text(body, "recipients"),
                Json.text(body, "topic"),
                toTags(body));
    }

    private static List<Tag> toTags(Map<String, Object> body) {
        List<Tag> tags = new ArrayList<>();
        for (Map<String, Object> tag : Json.objects(body, "tags")) {
            tags.add(new Tag(Json.text(tag, "name"), Json.text(tag, "value")));
        }
        return tags;
    }
}
