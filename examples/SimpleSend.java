// Runnable documentation: the smallest useful program this SDK supports.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SimpleSend.java you@example.com
//
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.exception.RateLimitException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/SimpleSend.java <recipient@example.com>");
        System.exit(2);
    }

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder("Acme <hello@yourdomain.com>", List.of(args[0]), "Hello world")
                        .html("<p>It works!</p>")
                        .text("It works!")
                        // Set an idempotency key on anything you might retry: the API replays the
                        // original response instead of sending twice.
                        .idempotencyKey("example-" + System.currentTimeMillis())
                        .build());

        System.out.println("accepted " + email.id() + " (message-id "
                + (email.messageId() == null ? "none" : email.messageId()) + ")");
    } catch (RateLimitException e) {
        System.err.println("rate limited; retry after " + e.retryAfter() + "s");
        System.exit(1);
    } catch (MailkubeException e) {
        System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        System.exit(1);
    }
}
