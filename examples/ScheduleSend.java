// Runnable documentation: accept a send now, deliver it later.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/ScheduleSend.java you@example.com
//
// A send carrying scheduledAt returns 202 rather than 200, and the Email you get back is a
// scheduled one: isScheduled() is true and status() is "scheduled". The instant must be strictly
// in the future and no more than 30 days out.
//
// See examples/ManageScheduledEmails.java for listing, rescheduling and cancelling it afterwards.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/ScheduleSend.java <recipient@example.com>");
        System.exit(2);
    }

    Instant due = Instant.now().plus(Duration.ofHours(2));

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Your weekly digest")
                        .html("<p>Delivered later, accepted now.</p>")
                        .text("Delivered later, accepted now.")
                        .scheduledAt(due)
                        .build());

        System.out.println("accepted " + email.id());
        System.out.println("isScheduled: " + email.isScheduled() + ", status: " + email.status());
        System.out.println("due at: " + email.scheduledAt());
        System.out.println("cancel it with: java examples/ManageScheduledEmails.java " + args[0]);
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
