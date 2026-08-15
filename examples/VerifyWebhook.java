// Runnable documentation: verify a webhook signature without running a server.
//
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/VerifyWebhook.java fixture.json [more.json...]
//
// examples/WebhookReceiver.java shows verification inside an HTTP handler. This one strips that
// away: it feeds captured deliveries straight to verifySignature so you can see exactly what is
// accepted and what is not. Useful for testing your own handler against saved payloads.
//
// A fixture is JSON: {secret, headers: {...}, body: "<raw body string>", must_verify: bool}.
// The body must be the EXACT bytes the server sent — re-serializing parsed JSON will not reproduce
// the signature, which is the single most common integration bug.
//
// Examples are excluded from lint, coverage and the duplication gate: they exist to be read and
// run, not to be shipped. Gradle never compiles this directory.

import com.mailkube.Webhooks;
import com.mailkube.exception.SignatureVerificationException;
import com.mailkube.model.WebhookEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

void main(String[] args) throws IOException {
    if (args.length == 0) {
        System.err.println("usage: java examples/VerifyWebhook.java <fixture.json> [more.json...]");
        System.exit(2);
    }

    int failures = 0;
    for (String path : args) {
        String raw = Files.readString(Path.of(path));
        String name = stringField(raw, "name");
        String secret = stringField(raw, "secret");
        String body = stringField(raw, "body");
        boolean mustVerify = raw.contains("\"must_verify\": true");

        Map<String, String> headers = new HashMap<>();
        for (String header : new String[] {"X-Webhook-Id", "X-Webhook-Ts", "X-Webhook-Sig"}) {
            headers.put(header, stringField(raw, header));
        }

        boolean verified = true;
        String detail;
        try {
            WebhookEvent event = Webhooks.parseEvent(
                    Webhooks.verifySignature(body.getBytes(StandardCharsets.UTF_8), headers, secret));
            detail = "event " + event.type();
        } catch (SignatureVerificationException e) {
            verified = false;
            detail = e.getMessage();
        }

        boolean ok = verified == mustVerify;
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "ok   " : "BAD  ") + name + ": " + (verified ? "verified" : "rejected")
                + " (expected " + (mustVerify ? "verified" : "rejected") + ") " + detail);
    }

    if (failures > 0) {
        System.err.println(failures + " fixture(s) did not verify as expected");
        System.exit(1);
    }
    System.out.println("all fixtures behaved as expected");
}

// A three-line JSON string reader, so this example stays dependency-free like the SDK itself.
// Real code uses whatever JSON library the application already has.
static String stringField(String json, String key) {
    int at = json.indexOf('"' + key + '"');
    if (at < 0) {
        return "";
    }
    int start = json.indexOf('"', json.indexOf(':', at) + 1) + 1;
    StringBuilder out = new StringBuilder();
    for (int i = start; i < json.length(); i++) {
        char c = json.charAt(i);
        if (c == '\\') {
            char next = json.charAt(++i);
            out.append(switch (next) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'u' -> (char) Integer.parseInt(json.substring(i + 1, i + 5), 16);
                default -> next;
            });
            if (next == 'u') {
                i += 4;
            }
        } else if (c == '"') {
            break;
        } else {
            out.append(c);
        }
    }
    return out.toString();
}
