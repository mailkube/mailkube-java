// Runnable documentation: attach name/value tags to a send.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithTags.java you@example.com
//
// Tags are your own labels for a message; they come back on every webhook event the message
// generates, which is what makes them useful for attributing engagement to a campaign.
//
// The server's limits: at most 20 tags, names unique within a message, name at most 16 characters
// and value at most 32, both drawn from [A-Za-z0-9_-]. A value may be empty; the name may not.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import com.mailkube.model.Tag;
import java.util.List;

void main(String[] args) {
    if (args.length == 0) {
        System.err.println("usage: java examples/SendWithTags.java <recipient@example.com>");
        System.exit(2);
    }

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Tagged for reporting")
                        .html("<p>These tags come back on every webhook this message produces.</p>")
                        .text("These tags come back on every webhook this message produces.")
                        .tags(List.of(new Tag("campaign", "onboarding"), new Tag("variant", "b")))
                        .build());

        System.out.println("accepted " + email.id() + ", tagged campaign=onboarding variant=b");
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
