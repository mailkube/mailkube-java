// Runnable documentation: send against a mailing-list topic.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithTopic.java you@example.com newsletter
//
// A topic is a subscription group your recipients can opt out of individually, and topic is the
// slug you configured for it (16 characters max). Sending under one means the unsubscribe link
// removes the recipient from that topic rather than from everything you send.
//
// The slug must already exist and be enabled on the sending domain's apex. An unknown or disabled
// slug is rejected outright, BEFORE the message is charged or queued — so a typo costs you
// nothing, but it does not silently fall back to sending untopiced either. The second half of this
// example triggers that rejection on purpose.
//
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.ApiException;
import com.mailkube.exception.ErrorName;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/SendWithTopic.java <recipient@example.com> [topic-slug]");
        System.exit(2);
    }
    String topic = args.length > 1 ? args[1] : "newsletter";

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Sent under the \"" + topic + "\" topic")
                        .html("<p>Unsubscribing from this removes you from this topic only.</p>")
                        .text("Unsubscribing from this removes you from this topic only.")
                        .topic(topic)
                        .build());
        System.out.println("accepted " + email.id() + " under topic " + topic);

        // The negative case: a slug that was never configured.
        try {
            client.emails()
                    .send(SendEmailParams.builder(
                                    sender(), List.of(args[0]), "This one never leaves the building")
                            .text("You should not be reading this.")
                            .topic("no-such-topic")
                            .build());
            System.err.println("expected an unknown topic to be rejected, but it was accepted");
            System.exit(1);
        } catch (ApiException e) {
            if (!ErrorName.TOPIC_NOT_FOUND.equals(e.errorName())) {
                throw e;
            }
            System.out.println("unknown topic correctly rejected: " + e.errorName());
        }
    }
}

// The verified sender this account may send from. Override per environment; the fallback is a
// placeholder and will be rejected until you set your own domain.
static String sender() {
    String from = System.getenv("MAILKUBE_FROM");
    return from == null || from.isBlank() ? "Acme <hello@yourdomain.com>" : from;
}
