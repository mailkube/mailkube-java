// Runnable documentation: schedule several sends as one batch, then move or cancel the batch.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/ScheduleBatch.java you@example.com
//
// A batch is just a label shared by several scheduled sends, and batchId is only valid alongside
// scheduledAt. Pick your own label: it is how you move or cancel the sends as a unit later.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator: nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Email;
import com.mailkube.model.ScheduledEmailBatchCancel;
import com.mailkube.model.ScheduledEmailBatchUpdate;
import com.mailkube.model.SendEmailParams;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/ScheduleBatch.java <recipient@example.com>");
        System.exit(2);
    }

    String batchId = "digest-" + Instant.now().getEpochSecond();
    Instant due = Instant.now().plus(Duration.ofHours(3));

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        for (int part = 1; part <= 3; part++) {
            Email email = client.emails()
                    .send(SendEmailParams.builder(sender(), List.of(args[0]), "Digest part " + part)
                            .html("<p>Part " + part + " of 3.</p>")
                            .scheduledAt(due)
                            .batchId(batchId)
                            .build());
            System.out.println("scheduled " + email.id() + " in batch " + email.batchId());
        }

        // Move every pending email in the batch at once. The path names the batch, so the body
        // carries only the new due time.
        ScheduledEmailBatchUpdate moved =
                client.scheduledEmails().batches().update(batchId, due.plus(Duration.ofHours(1)));
        System.out.println("moved " + moved.rescheduledCount() + " emails to " + moved.scheduledAt());

        // Canceling an unknown batch is a no-op reporting 0, not an error.
        ScheduledEmailBatchCancel canceled = client.scheduledEmails().batches().cancel(batchId);
        System.out.println("canceled " + canceled.canceledCount() + " emails in batch " + canceled.batchId());
    } catch (MailkubeException e) {
        System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        System.exit(1);
    }
}

// The verified sender this account may send from. Override per environment; the fallback is a
// placeholder and will be rejected until you set your own domain.
static String sender() {
    String from = System.getenv("MAILKUBE_FROM");
    return from == null || from.isBlank() ? "Acme <hello@yourdomain.com>" : from;
}
