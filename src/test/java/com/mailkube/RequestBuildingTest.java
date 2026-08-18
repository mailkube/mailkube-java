package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mailkube.internal.Config;
import com.mailkube.internal.PathSegment;
import com.mailkube.internal.Query;
import com.mailkube.internal.RequestSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The URL layer: path escaping, query rendering, and the spec that carries both.
 *
 * <p>These are the pieces every verb beyond {@code emails.send} is built on, and the two encoding
 * rules below are the ones that are easy to get subtly wrong in a way no happy-path test notices.
 */
class RequestBuildingTest {

    @Test
    void escapesAPathSegmentSoAnIdCannotRetargetTheRequest() {
        // The whole point of the rule: without escaping, this id walks out of the collection.
        assertEquals("x%2F..%2F..%2Fadmin", PathSegment.of("x/../../admin"));
        assertEquals("a%3Fb", PathSegment.of("a?b"));
        assertEquals("plain-id", PathSegment.of("plain-id"));
    }

    @Test
    void encodesASpaceInAPathAsPercent20NotPlus() {
        // URLEncoder is a FORM encoder: it renders a space as `+`, which is a literal plus inside
        // a path. Getting this wrong produces a 404 that looks like a server problem.
        assertEquals("a%20b", PathSegment.of("a b"));
    }

    @Test
    void rendersNoQueryStringAtAllWhenThereAreNoFilters() {
        assertNull(Query.render(Map.of()));
    }

    @Test
    void encodesQueryKeysAndValues() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("status", "scheduled,queued");
        filters.put("scheduled_at_gte", "2026-01-02T03:04:05Z");

        assertEquals("status=scheduled%2Cqueued&scheduled_at_gte=2026-01-02T03%3A04%3A05Z", Query.render(filters));
    }

    @Test
    void appendsFiltersToAPathThatHasNoQueryYet() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, null, Map.of());

        assertEquals(
                "https://api.example.test/v1/scheduled-emails?page=2",
                config.buildUrl("scheduled-emails", Map.of("page", "2")).toString());
    }

    @Test
    void joinsOntoAServerIssuedLinkThatAlreadyCarriesAQuery() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, null, Map.of());

        // A pagination link arrives complete. Appending with `?` here would produce two of them and
        // a route nobody serves.
        assertEquals(
                "https://api.example.test/v1/scheduled-emails?page=2&status=queued",
                config.buildUrl("https://api.example.test/v1/scheduled-emails?page=2", Map.of("status", "queued"))
                        .toString());
    }

    @Test
    void refusesFiltersOnALinkOffTheConfiguredOrigin() {
        Config config = new Config("mk_test", "https://api.example.test/v1/", null, null, null, Map.of());

        // The origin guard has to hold on the filtered overload too, or a `next` link on a foreign
        // host would take the Authorization header with it.
        assertThrows(
                com.mailkube.exception.ConfigurationException.class,
                () -> config.buildUrl("https://evil.example/v1/x", Map.of("page", "2")));
    }

    @Test
    void normalisesNullMapsSoNoCallerHasTo() {
        RequestSpec spec = new RequestSpec("emails", "POST", null, null, null);

        assertEquals(Map.of(), spec.query());
        assertEquals(Map.of(), spec.headers());
    }

    @Test
    void buildsEachVerbsSpecWithTheRightMethodAndBody() {
        assertEquals("POST", RequestSpec.post("emails", Map.of()).method());
        assertEquals("GET", RequestSpec.get("scheduled-emails").method());
        assertEquals("PATCH", RequestSpec.patch("scheduled-emails/1", Map.of()).method());
        assertEquals("DELETE", RequestSpec.delete("scheduled-emails/1").method());

        // A body-less verb sends no bytes at all, which is not the same as sending `{}`.
        assertNull(RequestSpec.get("x").body());
        assertNull(RequestSpec.delete("x").body());
        assertEquals(
                Map.of("page", "2"), RequestSpec.get("x", Map.of("page", "2")).query());
    }
}
