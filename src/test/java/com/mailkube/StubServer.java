package com.mailkube;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A real HTTP server on a loopback port, used by the tests that need the transport in play.
 *
 * <p>The JDK's own {@code com.sun.net.httpserver} rather than a mocking library: what is under
 * test in ConcurrencyTest is what happens on the wire when several callers share a client, and a
 * library that intercepts calls before they reach a socket cannot answer that.
 */
final class StubServer implements AutoCloseable {

    private final HttpServer server;
    private final List<Request> received = new CopyOnWriteArrayList<>();

    /**
     * One request as the server saw it.
     *
     * <p>{@code query} is the <b>raw</b> query string, so an assertion can see the percent
     * encoding the SDK produced. Decoding it here would hide exactly the bug worth catching:
     * a comma or slash that reached the wire unencoded and re-targeted the request.
     */
    record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {

        /**
         * Look a header up case-insensitively.
         *
         * <p>Not a nicety: {@code com.sun.net.httpserver} rewrites header names to its own
         * capitalisation ({@code Idempotency-key}, {@code Content-type}), so an exact-match lookup
         * silently returns null and the assertions fail somewhere else entirely.
         *
         * @return the first value of the header, or null
         */
        String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    List<String> values = entry.getValue();
                    return values == null || values.isEmpty() ? null : values.getFirst();
                }
            }
            return null;
        }
    }

    /** What to answer with. */
    record Reply(int status, String body, Map<String, String> headers) {

        static Reply ok(String body) {
            return new Reply(200, body, Map.of());
        }
    }

    StubServer(Function<Request, Reply> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, handler));
        // A virtual thread per exchange, so a barrier in the handler parks rather than exhausting
        // a fixed pool and deadlocking the server itself.
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    List<Request> received() {
        return new ArrayList<>(received);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, Function<Request, Reply> handler) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Request request = new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                Map.copyOf(exchange.getRequestHeaders()),
                body);
        received.add(request);

        Reply reply = handler.apply(request);
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] payload = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
