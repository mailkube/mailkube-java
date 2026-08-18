// Runnable documentation: a webhook endpoint, verified and typed.
//
//   export MAILKUBE_WEBHOOK_SECRET=whsec_...
//   ./gradlew jar
//   java -cp build/libs/mailkube-java-*.jar examples/WebhookReceiver.java 8080
//
// Point a mailkube webhook endpoint at http://<host>:8080/webhooks and watch it print each event.
// The server here is the JDK's own com.sun.net.httpserver, so the example needs nothing installed;
// in a real application this is your framework's request handler.
//
// Examples are compiled by CI (javac, against the built jar) and checked by `pmdExamples`, because
// they are copied by customers. They are not a Gradle source set, so they never reach the jar or
// the coverage denominator — nothing in CI runs them.

import com.mailkube.Webhooks;
import com.mailkube.exception.MailkubeException;
import com.mailkube.exception.SignatureVerificationException;
import com.mailkube.model.DomainStatusEvent;
import com.mailkube.model.EmailBouncedEvent;
import com.mailkube.model.EmailClickedEvent;
import com.mailkube.model.EmailDeliveredEvent;
import com.mailkube.model.EmailDeliveryDelayedEvent;
import com.mailkube.model.EmailFailedEvent;
import com.mailkube.model.EmailOpenedEvent;
import com.mailkube.model.EmailScheduledEvent;
import com.mailkube.model.EmailSentEvent;
import com.mailkube.model.EmailSuppressedEvent;
import com.mailkube.model.UnknownEvent;
import com.mailkube.model.WebhookEvent;
import com.mailkube.model.WebhookStatusEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

void main(String[] args) throws IOException {
    String secret = System.getenv("MAILKUBE_WEBHOOK_SECRET");
    if (secret == null) {
        System.err.println("set MAILKUBE_WEBHOOK_SECRET first");
        System.exit(2);
    }
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/webhooks", exchange -> handle(exchange, secret));
    server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    System.out.println("listening on http://localhost:" + port + "/webhooks");
}

void handle(HttpExchange exchange, String secret) throws IOException {
    // Read the RAW bytes. Parsing and re-serializing reorders keys and normalizes whitespace, and
    // the signature is over the exact bytes that were sent.
    byte[] raw = exchange.getRequestBody().readAllBytes();

    Map<String, String> headers = new HashMap<>();
    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.getFirst()));

    try {
        WebhookEvent event = Webhooks.parseEvent(Webhooks.verifySignature(raw, headers, secret));

        // X-Webhook-Id is stable across retries: deduplicate on it before acting, because a webhook
        // that timed out on your side will be delivered again.
        System.out.println("event " + exchange.getRequestHeaders().getFirst("X-Webhook-Id"));
        describe(event);

        // Answer 2xx quickly and do the slow work elsewhere; the platform retries on a timeout.
        respond(exchange, 204);

    } catch (SignatureVerificationException e) {
        // Someone who does not hold the signing secret, or a body that was altered in transit.
        System.err.println("rejected: " + e.getMessage());
        respond(exchange, 401);
    } catch (MailkubeException e) {
        // A body that is not a JSON object at all. An unrecognized event TYPE does not land here:
        // it parses as UnknownEvent, which is what keeps this receiver working across releases.
        System.err.println("malformed payload: " + e.getMessage());
        respond(exchange, 400);
    }
}

void describe(WebhookEvent event) {
    // No `default` arm on purpose. WebhookEvent is sealed, so when the SDK adds an event type this
    // switch stops compiling until it is handled — the compiler tells you, not your logs.
    String line = switch (event) {
        case EmailSentEvent e -> "sent to " + e.sent().recipient();
        case EmailDeliveredEvent e -> "delivered to " + e.delivery().recipient();
        case EmailBouncedEvent e -> "bounced " + e.bounce().code() + " " + e.bounce().reason();
        case EmailDeliveryDelayedEvent e -> "delayed " + e.delay().code() + ", will retry";
        case EmailSuppressedEvent e -> "suppressed " + e.suppression().recipients();
        case EmailScheduledEvent e -> "scheduled for " + e.scheduled().scheduledAt();
        case EmailFailedEvent e -> "dropped at dispatch: " + e.failed().reason();
        case EmailOpenedEvent e -> "opened from " + e.open().ipAddress();
        case EmailClickedEvent e -> "clicked " + e.click().link();
        case DomainStatusEvent e -> "domain " + e.domain() + " is now " + e.status();
        case WebhookStatusEvent e -> "endpoint " + e.endpointUrl() + " active=" + e.isActive();
        // A type this SDK release predates. The payload is still here in full.
        case UnknownEvent e -> "unhandled " + e.type() + ": " + e.raw();
    };
    System.out.println("  " + line);
}

void respond(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
}
