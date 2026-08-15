// Runnable documentation: send a saved template instead of raw content.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithTemplate.java you@example.com <uuid>
//
// A send carries EITHER raw content (html/text) or a template, never both. The template must exist
// on the sending domain and be published — a draft or deleted one is a template_not_found.
// templateVersion "latest" tracks the newest version; pin a number to freeze the content you
// tested against.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.util.List;
import java.util.Map;

void main(String[] args) {
    if (args.length < 2) {
        System.err.println("usage: java examples/SendWithTemplate.java <recipient@example.com> <template-uuid>");
        System.exit(2);
    }

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Welcome aboard")
                        .templateId(args[1])
                        .templateVersion("latest")
                        .variables(Map.of("first_name", "Ada", "plan", "Pro"))
                        .build());

        System.out.println("accepted " + email.id() + " from template " + args[1]);
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
