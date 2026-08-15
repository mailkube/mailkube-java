// Runnable documentation: attach files to a send.
//
//   export MAILKUBE_API_KEY=mk_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/SendWithAttachments.java you@example.com [file]
//
// Attachment content is raw bytes; the SDK base64-encodes it for the wire, so you never do that
// yourself. Attachment.of(filename, bytes) infers the content type from the filename.
//
// With no file argument the example builds a tiny valid PDF in memory, so it runs without you
// having to find a file first.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.MailkubeClient;
import com.mailkube.exception.MailkubeException;
import com.mailkube.model.Attachment;
import com.mailkube.model.Email;
import com.mailkube.model.SendEmailParams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

void main(String[] args) throws IOException {
    if (args.length == 0) {
        System.err.println("usage: java examples/SendWithAttachments.java <recipient@example.com> [file]");
        System.exit(2);
    }

    // The smallest thing a PDF reader will still open, so the example has something real to
    // attach. Declared inside main rather than as a top-level field: PMD 7.26.0 cannot parse a
    // top-level field in a compact source file (JEP 512) and silently analyzes nothing.
    byte[] minimalPdf = ("%PDF-1.4\n"
                    + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                    + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                    + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 50]>>endobj\n"
                    + "trailer<</Root 1 0 R>>\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII);

    byte[] content = minimalPdf;
    String filename = "invoice.pdf";
    if (args.length > 1) {
        content = Files.readAllBytes(Path.of(args[1]));
        filename = Path.of(args[1]).getFileName().toString();
    }

    try (MailkubeClient client = MailkubeClient.builder().build()) { // reads MAILKUBE_API_KEY
        Email email = client.emails()
                .send(SendEmailParams.builder(sender(), List.of(args[0]), "Your invoice")
                        .html("<p>Your invoice is attached.</p>")
                        .attachments(List.of(new Attachment(filename, content, "application/pdf")))
                        .build());

        System.out.println("accepted " + email.id() + " with 1 attachment (" + content.length + " bytes)");
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
