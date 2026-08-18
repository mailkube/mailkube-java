// Runnable documentation: the errors you will actually hit, and how to tell them apart.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/ErrorHandling.java you@example.com
//
// Every API failure arrives as an ApiException subclass carrying errorName() — the server's stable
// machine-readable name — alongside statusCode(), requestId() and retryAfter(). Branch on
// errorName() (compare against the ErrorName constants), never on the human-readable message,
// which is free to change.
//
// Nothing here sends a message: each call is designed to be refused.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.ApiException;
import com.mailkube.exception.ErrorName;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.SendEmailParams;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/ErrorHandling.java <recipient@example.com>");
        System.exit(2);
    }
    List<String> to = List.of(args[0]);
    // A local, not a top-level field: PMD 7.26.0 cannot parse a top-level field in a compact
    // source file (JEP 512) and silently analyzes nothing. `expect` takes the collector instead
    // of mutating shared state, which is the clearer shape regardless.
    List<String> failures = new ArrayList<>();

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY

        // A message with no body at all: html, text and templateId are mutually required-one-of.
        expect(failures, "missing body", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "No body").build()));

        // scheduledAt must be strictly in the future.
        expect(failures, "past scheduledAt", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "Yesterday")
                        .text("...")
                        .scheduledAt(Instant.now().minus(Duration.ofMinutes(1)))
                        .build()));

        // batchId is a grouping label for scheduled sends and means nothing without scheduledAt.
        expect(failures, "batchId without scheduledAt", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "Ungrouped")
                        .text("...")
                        .batchId("b1")
                        .build()));

        // A sent email has left the scheduled collection, so filtering for it is a contract error
        // rather than an empty page — the distinction tells you your assumption was wrong.
        expect(failures, "list status \"sent\"", ErrorName.VALIDATION_ERROR, () -> client.scheduledEmails()
                .list(ScheduledEmailListParams.builder().status("sent").build()));
    }

    // A bad key is refused identically whether it is malformed, unknown or absent, so nothing about
    // the key space leaks.
    try (MailkubeClient anonymous = MailkubeClient.builder()
            .apiKey("mk_notarealkey_" + "0".repeat(64))
            .build()) {
        expect(failures, "bad api key", ErrorName.INVALID_API_KEY, () -> anonymous.emails()
                .send(SendEmailParams.builder(sender(), to, "Nope").text("...").build()));
    }

    if (!failures.isEmpty()) {
        System.err.println(failures.size() + " case(s) did not behave as documented: " + String.join(", ", failures));
        System.exit(1);
    }
    System.out.println("all error cases behaved as documented");
}

// expect runs call and records label in failures unless it failed with the wanted error name.
static void expect(List<String> failures, String label, String want, Runnable call) {
    try {
        call.run();
    } catch (ApiException e) {
        boolean ok = want.equals(e.errorName());
        if (!ok) {
            failures.add(label);
        }
        System.out.println((ok ? "ok   " : "BAD  ") + label + ": " + e.errorName() + " (" + e.statusCode() + ")");
        return;
    }
    System.err.println("BAD  " + label + ": expected " + want + ", but the call succeeded");
    failures.add(label);
}

// The verified sender this account may send from. Override per environment; the fallback is a
// placeholder and will be rejected until you set your own domain.
static String sender() {
    String from = System.getenv("MAILKUBE_FROM");
    return from == null || from.isBlank() ? "Acme <hello@yourdomain.com>" : from;
}
