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
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.ApiException;
import com.mailkube.exception.ErrorName;
import com.mailkube.model.ScheduledEmailListParams;
import com.mailkube.model.SendEmailParams;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

static int failures = 0;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/ErrorHandling.java <recipient@example.com>");
        System.exit(2);
    }
    List<String> to = List.of(args[0]);

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY

        // A message with no body at all: html, text and templateId are mutually required-one-of.
        expect("missing body", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "No body").build()));

        // scheduledAt must be strictly in the future.
        expect("past scheduledAt", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "Yesterday")
                        .text("...")
                        .scheduledAt(Instant.now().minus(Duration.ofMinutes(1)))
                        .build()));

        // batchId is a grouping label for scheduled sends and means nothing without scheduledAt.
        expect("batchId without scheduledAt", ErrorName.VALIDATION_ERROR, () -> client.emails()
                .send(SendEmailParams.builder(sender(), to, "Ungrouped")
                        .text("...")
                        .batchId("b1")
                        .build()));

        // A sent email has left the scheduled collection, so filtering for it is a contract error
        // rather than an empty page — the distinction tells you your assumption was wrong.
        expect("list status \"sent\"", ErrorName.VALIDATION_ERROR, () -> client.scheduledEmails()
                .list(ScheduledEmailListParams.builder().status("sent").build()));
    }

    // A bad key is refused identically whether it is malformed, unknown or absent, so nothing about
    // the key space leaks.
    try (MailkubeClient anonymous = MailkubeClient.builder()
            .apiKey("mk_notarealkey_" + "0".repeat(64))
            .build()) {
        expect("bad api key", ErrorName.INVALID_API_KEY, () -> anonymous.emails()
                .send(SendEmailParams.builder(sender(), to, "Nope").text("...").build()));
    }

    if (failures > 0) {
        System.err.println(failures + " case(s) did not behave as documented");
        System.exit(1);
    }
    System.out.println("all error cases behaved as documented");
}

// expect runs call and reports whether it failed with the wanted error name.
static void expect(String label, String want, Runnable call) {
    try {
        call.run();
    } catch (ApiException e) {
        boolean ok = want.equals(e.errorName());
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "ok   " : "BAD  ") + label + ": " + e.errorName() + " (" + e.statusCode() + ")");
        return;
    }
    System.err.println("BAD  " + label + ": expected " + want + ", but the call succeeded");
    failures++;
}

// The verified sender this account may send from. Override per environment; the fallback is a
// placeholder and will be rejected until you set your own domain.
static String sender() {
    String from = System.getenv("MAILKUBE_FROM");
    return from == null || from.isBlank() ? "Acme <hello@yourdomain.com>" : from;
}
