package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mailkube.model.MessageContext;
import com.mailkube.model.PageSteps;
import com.mailkube.model.Pagination;
import com.mailkube.model.ScheduledEmail;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.ScheduledEmailPage;
import com.mailkube.model.SuppressionContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The defensive edges of the model and parameter types.
 *
 * <p>These are the paths a malformed or unusual payload takes, and the ones a caller reaches by
 * omitting a filter. They are cheap to get wrong and expensive to notice, because every one of them
 * fails by returning something plausible rather than by raising.
 */
class ModelGuardsTest {

    @Test
    void treatsAnAbsentListAsEmptyRatherThanNull() {
        // A caller iterating a model should never have to null-check a collection first.
        assertEquals(List.of(), new SuppressionContext(null, "T").recipients());
        assertEquals(List.of(), new MessageContext("id", "T", null, null, null, null, null).tags());
        assertEquals(
                List.of(), new ScheduledEmail("id", null, null, null, null, null, null, null, null, null, null).tags());
        assertEquals(List.of(), new ScheduledEmailPage(null, null).data());
    }

    @Test
    void copiesListsSoALaterMutationCannotReachInside() {
        List<String> recipients = new ArrayList<>(List.of("a@b.com"));
        SuppressionContext context = new SuppressionContext(recipients, "T");

        recipients.add("c@d.com");

        assertEquals(List.of("a@b.com"), context.recipients());
    }

    @Test
    void reportsNoFurtherPageWhateverIsMissingFromTheMetadata() {
        // Three ways the server can say "this is the last page", and all of them must agree.
        assertFalse(new ScheduledEmailPage(null, List.of()).hasMore());
        assertFalse(new ScheduledEmailPage(new Pagination(null, 0, 1), List.of()).hasMore());
        assertFalse(new ScheduledEmailPage(new Pagination(new PageSteps(null, null), 0, 1), List.of()).hasMore());
    }

    @Test
    void appliesNoFilterForAnAbsentOrEmptyValue() {
        assertEquals(Map.of(), ScheduledEmailParamsHelper.emptyFilters());
    }

    @Test
    void rendersEveryFilterItIsGiven() {
        Map<String, String> filters = ScheduledEmailListParams.builder()
                .status("scheduled")
                .scheduledAtLte(Instant.parse("2026-04-01T00:00:00Z"))
                .page(3)
                .build()
                .filters();

        assertEquals(Map.of("status", "scheduled", "scheduled_at_lte", "2026-04-01T00:00:00Z", "page", "3"), filters);
    }

    @Test
    void rendersATextBoundExactlyAsGiven() {
        assertEquals(
                Map.of("scheduled_at_lte", "2026-04-01T00:00:00Z"),
                ScheduledEmailListParams.builder()
                        .scheduledAtLte("2026-04-01T00:00:00Z")
                        .build()
                        .filters());
    }

    @Test
    void readsATimestampBackAsTheServerSentIt() {
        // The SDK does not reinterpret server data, so a value it cannot parse is still handed over.
        ScheduledEmail email =
                new ScheduledEmail("id", null, null, null, "whenever", null, null, null, null, null, List.of());

        assertEquals("whenever", email.scheduledAt());
        assertNull(email.createdAt());
    }

    /** Keeps the empty-filter cases in one place, since each is a different way of saying nothing. */
    private static final class ScheduledEmailParamsHelper {

        private ScheduledEmailParamsHelper() {}

        private static Map<String, String> emptyFilters() {
            return ScheduledEmailListParams.builder()
                    .status((String) null)
                    .status(List.of())
                    .status((List<String>) null)
                    .batchId(null)
                    .scheduledAtGte((Instant) null)
                    .scheduledAtGte((String) null)
                    .scheduledAtLte((Instant) null)
                    .build()
                    .filters();
        }
    }
}
