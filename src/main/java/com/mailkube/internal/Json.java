package com.mailkube.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The minimal JSON this SDK needs, so the SDK needs no dependency to get it.
 *
 * <p>Java is the only language in this SDK family with no JSON in its standard library, and this
 * class is the price of staying dependency-free. That trade is made deliberately: this library is
 * installed into other people's applications, where a Jackson or Gson version becomes their
 * problem, and Spring Boot 4's move to Jackson 3 would otherwise put a second Jackson on every
 * consumer's classpath. See {@code .rules/SDK_DESIGN.md}.
 *
 * <p>It is deliberately not a general-purpose library. It encodes what the API accepts (objects,
 * arrays, strings, numbers, booleans, null) and decodes what the API returns. It does not stream,
 * it does not bind to types, and it should not grow to.
 */
public final class Json {

    private Json() {}

    /**
     * Encode a value as JSON text.
     *
     * @param value a Map, List, String, Number, Boolean or null
     * @return the JSON text
     */
    public static String encode(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /**
     * Decode a JSON object.
     *
     * <p>Best-effort by design: an empty, malformed or non-object body becomes an empty map, so a
     * server that answers an error with an HTML page still maps by status rather than throwing a
     * parse error over the top of the real failure.
     *
     * @param raw the response body
     * @return the decoded object, or an empty map
     */
    public static Map<String, Object> decodeObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = new Parser(raw).parseValue();
            return parsed instanceof Map<?, ?> map ? cast(map) : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * Read a value off a decoded body as text, whatever scalar type it arrived as.
     *
     * @param body the decoded body
     * @param key the field name
     * @return the value as text, or null when absent or not a scalar
     */
    public static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return switch (value) {
            case String s -> s;
            case Number n -> n.toString();
            case Boolean b -> b.toString();
            case null, default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String s -> writeString(s, out);
            case Boolean b -> out.append(b);
            case Number n -> out.append(n);
            case Map<?, ?> map -> writeObject(map, out);
            case Iterable<?> list -> writeArray(list, out);
            default -> writeString(value.toString(), out);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            write(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(Iterable<?> list, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(item, out);
        }
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> appendChar(c, out);
            }
        }
        out.append('"');
    }

    private static void appendChar(char c, StringBuilder out) {
        // Control characters must be escaped; everything else, including non-ASCII, goes through
        // as UTF-8, which is what the Content-Type declares.
        if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
        } else {
            out.append(c);
        }
    }

    /** A recursive-descent parser over the subset of JSON the API returns. */
    private static final class Parser {

        private final String source;
        private int index;

        Parser(String source) {
            this.source = source;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                } else {
                    expect('}');
                    return result;
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                } else {
                    expect(']');
                    return result;
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    out.append(unescape());
                } else {
                    out.append(c);
                }
            }
        }

        private char unescape() {
            char c = next();
            return switch (c) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'u' -> readUnicode();
                default -> c;
            };
        }

        private char readUnicode() {
            String hex = source.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Object parseBoolean() {
            if (source.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            expectLiteral("false");
            return Boolean.FALSE;
        }

        private Object parseNull() {
            expectLiteral("null");
            return null;
        }

        private Object parseNumber() {
            int start = index;
            while (index < source.length() && "+-.eE0123456789".indexOf(source.charAt(index)) >= 0) {
                index++;
            }
            String literal = source.substring(start, index);
            if (literal.isEmpty()) {
                throw new IllegalStateException("not a JSON value at " + start);
            }
            // Whole numbers stay integral so an id or a count does not come back as "1.0".
            return literal.contains(".") || literal.contains("e") || literal.contains("E")
                    ? (Object) Double.valueOf(literal)
                    : (Object) Long.valueOf(literal);
        }

        private void expectLiteral(String literal) {
            if (!source.startsWith(literal, index)) {
                throw new IllegalStateException("expected " + literal + " at " + index);
            }
            index += literal.length();
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private char peek() {
            if (index >= source.length()) {
                throw new IllegalStateException("unexpected end of JSON");
            }
            return source.charAt(index);
        }

        private char next() {
            char c = peek();
            index++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw new IllegalStateException("expected " + expected + " at " + (index - 1));
            }
        }
    }
}
