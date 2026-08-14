package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * One client instance is safe to share across concurrent callers, and this proves it.
 *
 * <p>{@code SDK_CONTRACT.md} requires more than "no exception was thrown": it requires that
 * concurrent calls <b>do not observe each other's responses</b>. A client holding per-request
 * state does not throw when two callers overlap, it hands one caller the other's body. Inside a
 * single process that is a confidentiality bug, and it passes any assertion weaker than this one.
 *
 * <p>Two deliberate choices, neither optional:
 *
 * <ul>
 *   <li><b>Real sockets, and virtual threads.</b> Virtual threads are the whole reason this SDK
 *       ships one synchronous client, so they are what the test uses. They are also why the Java
 *       25 floor exists: before JEP 491 removed monitor pinning in 24, the JDK's own
 *       HttpClient could pin its carrier and a test like this would measure the JDK, not the SDK.
 *   <li><b>The server answers nobody until every request has arrived.</b> That proves the calls
 *       genuinely overlap, because a client that serialized them would never fill the barrier and
 *       this test would fail on the timeout rather than quietly passing on a client it never
 *       stressed. Releasing everyone at once then makes them contend for real.
 * </ul>
 */
class ConcurrencyTest {

    private static final int CALLS = 32;
    private static final int GATE_TIMEOUT_SECONDS = 30;

    @Test
    void doesNotLetConcurrentCallsObserveEachOthersResponses() throws Exception {
        CyclicBarrier gate = new CyclicBarrier(CALLS);

        try (StubServer server = new StubServer(request -> {
            String key = request.header("Idempotency-Key");
            await(gate);
            return StubServer.Reply.ok("{\"id\":\"" + key + "\"}");
        })) {
            List<String> keys = new ArrayList<>(CALLS);
            for (int i = 0; i < CALLS; i++) {
                keys.add("idem-%03d".formatted(i));
            }

            List<String> received = sendAll(server, keys);

            for (int i = 0; i < CALLS; i++) {
                assertEquals(
                        keys.get(i),
                        received.get(i),
                        keys.get(i) + " received the response for \"" + received.get(i)
                                + "\": concurrent calls observed each other");
            }
        }
    }

    private List<String> sendAll(StubServer server, List<String> keys)
            throws IOException, InterruptedException, ExecutionException {
        try (MailkubeClient client = ClientTest.client(server);
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>(keys.size());
            for (String key : keys) {
                futures.add(pool.submit(() -> client.emails()
                        .send(ClientTest.minimal().idempotencyKey(key).build())
                        .id()));
            }
            List<String> received = new ArrayList<>(keys.size());
            for (Future<String> future : futures) {
                received.add(future.get());
            }
            return received;
        }
    }

    private static void await(CyclicBarrier gate) {
        try {
            gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted at the barrier", e);
        } catch (Exception e) {
            throw new IllegalStateException("the calls did not overlap: the barrier never filled", e);
        }
    }
}
