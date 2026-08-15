// Runnable documentation: every recipient field and custom headers on one message.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithRecipients.java you@example.com
//
// to, cc, bcc and replyTo are all List<String>. The account limit is 50 recipients per message,
// counted across to + cc + bcc.
//
// Custom headers carry your own metadata. The API caps them at 20 per message, header names match
// [A-Za-z0-9-] up to 64 characters, and no value may contain CR or LF.
//
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;
import java.util.Map;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/SendWithRecipients.java <recipient@example.com>");
        System.exit(2);
    }

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Every recipient field at once")
                        .cc(List.of(args[0]))
                        .bcc(List.of(args[0]))
                        // Replies go somewhere other than the sending address.
                        .replyTo(List.of("support@yourdomain.com"))
                        .html("<p>to, cc, bcc and reply-to on a single message.</p>")
                        .text("to, cc, bcc and reply-to on a single message.")
                        .headers(Map.of("X-Campaign-Id", "recipients-demo", "X-Customer-Tier", "gold"))
                        .build());

        System.out.println("accepted " + email.id());
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
