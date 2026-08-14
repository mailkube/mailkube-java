package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The JSON codec earns its own suite because it is the one piece of this SDK that exists only
 * because Java has no JSON in its standard library. It is hand-written, so it is tested as such.
 */
class JsonTest {

    @Test
    void encodesTheScalarsTheApiAccepts() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", "hi");
        body.put("count", 3);
        body.put("ratio", 1.5);
        body.put("flag", true);
        body.put("absent", null);

        assertEquals("{\"text\":\"hi\",\"count\":3,\"ratio\":1.5,\"flag\":true,\"absent\":null}", Json.encode(body));
    }

    @Test
    void encodesNestedObjectsAndArrays() {
        Map<String, Object> body = Map.of("items", List.of(Map.of("name", "a")));

        assertEquals("{\"items\":[{\"name\":\"a\"}]}", Json.encode(body));
    }

    @Test
    void escapesWhatJsonRequiresAndLeavesUnicodeAlone() {
        String encoded = Json.encode(Map.of("k", "a\"b\\c\nd\te\rf\bg\fhé"));

        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\\te\\rf\\bg\\fhé\"}", encoded);
    }

    @Test
    void escapesControlCharactersAsUnicodeSequences() {
        assertEquals("{\"k\":\"a\\u0001b\"}", Json.encode(Map.of("k", "a\u0001b")));
    }

    @Test
    void roundTripsAnObject() {
        String encoded = Json.encode(Map.of("id", "abc", "nested", Map.of("n", 1)));

        Map<String, Object> decoded = Json.decodeObject(encoded);

        assertEquals("abc", decoded.get("id"));
        assertEquals(Map.of("n", 1L), decoded.get("nested"));
    }

    @Test
    void decodesTheShapesTheApiReturns() {
        Map<String, Object> decoded = Json.decodeObject(
                "{ \"s\": \"x\", \"i\": 42, \"d\": 1.5e2, \"t\": true, \"f\": false, \"n\": null, \"a\": [1, \"b\"] }");

        assertEquals("x", decoded.get("s"));
        assertEquals(42L, decoded.get("i"));
        assertEquals(150.0, decoded.get("d"));
        assertEquals(true, decoded.get("t"));
        assertEquals(false, decoded.get("f"));
        assertNull(decoded.get("n"));
        assertEquals(List.of(1L, "b"), decoded.get("a"));
    }

    @Test
    void decodesEscapeSequencesIncludingUnicode() {
        Map<String, Object> decoded = Json.decodeObject("{\"k\":\"a\\\"b\\\\c\\nd\\te\\u00e9f\\/g\"}");

        assertEquals("a\"b\\c\nd\teéf/g", decoded.get("k"));
    }

    @Test
    void passesAnUnrecognizedEscapeThroughAsItsOwnCharacter() {
        assertEquals("aqb", Json.decodeObject("{\"k\":\"a\\qb\"}").get("k"));
    }

    @Test
    void decodesSignedAndExponentialNumbers() {
        Map<String, Object> decoded = Json.decodeObject("{\"a\":-7,\"b\":-1.5,\"c\":2E3,\"d\":0}");

        assertEquals(-7L, decoded.get("a"));
        assertEquals(-1.5, decoded.get("b"));
        assertEquals(2000.0, decoded.get("c"));
        assertEquals(0L, decoded.get("d"));
    }

    @Test
    void encodesAnEmptyArray() {
        assertEquals("{\"a\":[]}", Json.encode(Map.of("a", List.of())));
    }

    @Test
    void decodesEmptyContainers() {
        assertEquals(Map.of(), Json.decodeObject("{}"));
        assertEquals(List.of(), Json.decodeObject("{\"a\":[]}").get("a"));
        assertEquals(Map.of(), Json.decodeObject("{\"o\":{}}").get("o"));
    }

    @Test
    void treatsAnythingUndecodableAsAnEmptyObject() {
        // Best-effort by design: a server answering an error with an HTML page must still map by
        // status rather than throwing a parse error over the top of the real failure.
        assertEquals(Map.of(), Json.decodeObject("<html>502</html>"));
        assertEquals(Map.of(), Json.decodeObject("[1, 2, 3]"));
        assertEquals(Map.of(), Json.decodeObject("{\"unterminated\":"));
        assertEquals(Map.of(), Json.decodeObject("{\"k\": }"));
        assertEquals(Map.of(), Json.decodeObject("{\"k\": tru}"));
        assertEquals(Map.of(), Json.decodeObject(""));
        assertEquals(Map.of(), Json.decodeObject("   "));
        assertEquals(Map.of(), Json.decodeObject(null));
    }

    @Test
    void readsScalarsAsTextWhateverTypeTheyArrivedAs() {
        Map<String, Object> body = Json.decodeObject("{\"s\":\"x\",\"i\":42,\"b\":true,\"o\":{},\"n\":null}");

        assertEquals("x", Json.text(body, "s"));
        assertEquals("42", Json.text(body, "i"));
        assertEquals("true", Json.text(body, "b"));
        assertNull(Json.text(body, "o"));
        assertNull(Json.text(body, "n"));
        assertNull(Json.text(body, "missing"));
    }

    @Test
    void encodesAnUnrecognizedTypeAsItsStringForm() {
        assertTrue(Json.encode(Map.of("k", java.time.Month.MAY)).contains("\"MAY\""));
    }
}
