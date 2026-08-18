// Runnable documentation: retry a send safely with an idempotency key.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithIdempotency.java you@example.com
//
// There are no built-in retries in this SDK, so retrying is your call — and a naive retry after a
// timeout can send the same message twice, because a request that never returned may still have
// succeeded. An idempotency key makes the retry safe: the server remembers the first response for
// that key (24 hours by default) and replays it byte for byte instead of sending again.
//
// The key is fingerprinted against the request body. Reusing a key with a DIFFERENT body is an
// error rather than a silent replay, which is what stops a recycled key from swallowing a real
// second message.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.ApiException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/SendWithIdempotency.java <recipient@example.com>");
        System.exit(2);
    }

    // In real code this is a stable id for the thing you are sending about — an order id, a job
    // id — not a random value, otherwise a retry generates a new key and sends twice.
    String key = "order-" + System.currentTimeMillis();

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email first = client.emails().send(message(args[0], "Sent at most once", key));
        System.out.println("first  call: " + first.id());

        // Pretend the first response never reached us and we retried.
        Email replay = client.emails().send(message(args[0], "Sent at most once", key));
        System.out.println("replayed   : " + replay.id());

        if (!first.id().equals(replay.id())) {
            System.err.println("expected the same id back, got " + first.id() + " then " + replay.id()
                    + " — that is a second send");
            System.exit(1);
        }
        System.out.println("same id returned: the retry was replayed, not resent");

        // Same key, different body: refused rather than replayed.
        try {
            client.emails().send(message(args[0], "A different message entirely", key));
            System.err.println("expected a reused key with a changed body to be rejected");
            System.exit(1);
        } catch (ApiException e) {
            System.out.println("key reuse with a changed body correctly rejected: " + e.errorName());
        }
    }
}

static SendEmailParams message(String recipient, String subject, String idempotencyKey) {
    return SendEmailParams.builder(sender(), List.of(recipient), subject)
            .html("<p>Retrying this send cannot duplicate it.</p>")
            .text("Retrying this send cannot duplicate it.")
            .idempotencyKey(idempotencyKey)
            .build();
}

// The verified sender this account may send from. Override per environment; the fallback is a
// placeholder and will be rejected until you set your own domain.
static String sender() {
    String from = System.getenv("MAILKUBE_FROM");
    return from == null || from.isBlank() ? "Acme <hello@yourdomain.com>" : from;
}
