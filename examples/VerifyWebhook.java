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
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

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
        if (!check(path)) {
            failures++;
        }
    }

    if (failures > 0) {
        System.err.println(failures + " fixture(s) did not verify as expected");
        System.exit(1);
    }
    System.out.println("all fixtures behaved as expected");
}

// Reads one fixture and reports whether it behaved as the fixture says it should.
static boolean check(String path) throws IOException {
    String raw = Files.readString(Path.of(path));
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
    System.out.println((ok ? "ok   " : "BAD  ") + stringField(raw, "name") + ": "
            + (verified ? "verified" : "rejected") + " (expected " + (mustVerify ? "verified" : "rejected") + ") "
            + detail);
    return ok;
}

// A minimal JSON string reader, so this example stays dependency-free like the SDK itself.
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
        if (c == '"') {
            break;
        }
        if (c != '\\') {
            out.append(c);
        } else if (json.charAt(++i) == 'u') {
            out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
            i += 4;
        } else {
            out.append(unescape(json.charAt(i)));
        }
    }
    return out.toString();
}

// The one-character JSON escapes. The four-hex-digit form is handled by the caller, which also has
// to skip its digits. (It cannot be named in this comment: javac processes unicode escapes inside
// comments too, so writing it here is a compile error.)
static char unescape(char escaped) {
    return switch (escaped) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case 'r' -> '\r';
        case 'b' -> '\b';
        case 'f' -> '\f';
        default -> escaped;
    };
}
