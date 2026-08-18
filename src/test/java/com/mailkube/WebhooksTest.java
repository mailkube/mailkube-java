package com.mailkube;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mailkube.exception.SignatureVerificationException;
import com.mailkube.model.EmailDeliveredEvent;
import com.mailkube.model.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhooksTest {

    private static final String SECRET = "whsec_test";
    private static final String ID = "evt_1";
    private static final byte[] PAYLOAD =
            "{\"type\":\"email.delivered\",\"data\":{\"id\":\"abc\"}}".getBytes(StandardCharsets.UTF_8);

    private static Map<String, String> headersFor(byte[] payload, String id, String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(payload);
            return Map.of(
                    "X-Webhook-Id", id,
                    "X-Webhook-Ts", timestamp,
                    "X-Webhook-Sig", "sha256=" + HexFormat.of().formatHex(mac.doFinal()));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String now() {
        return Instant.now().toString();
    }

    @Test
    void returnsThePayloadWhenTheSignatureAndTimestampAreGood() {
        Map<String, String> headers = headersFor(PAYLOAD, ID, now(), SECRET);

        assertArrayEquals(PAYLOAD, Webhooks.verifySignature(PAYLOAD, headers, SECRET));
    }

    @Test
    void matchesHeaderNamesCaseInsensitivelyAsHttpRequires() {
        Map<String, String> signed = headersFor(PAYLOAD, ID, now(), SECRET);
        Map<String, String> lowercased = Map.of(
                "x-webhook-id", signed.get("X-Webhook-Id"),
                "x-webhook-ts", signed.get("X-Webhook-Ts"),
                "x-webhook-sig", signed.get("X-Webhook-Sig"));

        assertArrayEquals(PAYLOAD, Webhooks.verifySignature(PAYLOAD, lowercased, SECRET));
    }

    @Test
    void rejectsASignatureMadeWithADifferentSecret() {
        Map<String, String> headers = headersFor(PAYLOAD, ID, now(), "wrong");

        SignatureVerificationException error = assertThrows(
                SignatureVerificationException.class, () -> Webhooks.verifySignature(PAYLOAD, headers, SECRET));

        assertTrue(error.getMessage().contains("signature mismatch"));
    }

    @Test
    void rejectsABodyAlteredAfterSigning() {
        Map<String, String> headers = headersFor(PAYLOAD, ID, now(), SECRET);
        byte[] tampered = "{\"type\":\"email.delivered\",\"data\":{\"id\":\"xyz\"}}".getBytes(StandardCharsets.UTF_8);

        assertThrows(SignatureVerificationException.class, () -> Webhooks.verifySignature(tampered, headers, SECRET));
    }

    @Test
    void rejectsATimestampOutsideTheFreshnessWindow() {
        String stale = Instant.now().minus(Duration.ofHours(1)).toString();
        Map<String, String> headers = headersFor(PAYLOAD, ID, stale, SECRET);

        SignatureVerificationException error = assertThrows(
                SignatureVerificationException.class, () -> Webhooks.verifySignature(PAYLOAD, headers, SECRET));

        assertTrue(error.getMessage().contains("freshness window"));
    }

    @Test
    void acceptsAStaleTimestampWhenTheCallerWidensTheTolerance() {
        String stale = Instant.now().minus(Duration.ofHours(1)).toString();
        Map<String, String> headers = headersFor(PAYLOAD, ID, stale, SECRET);

        assertArrayEquals(PAYLOAD, Webhooks.verifySignature(PAYLOAD, headers, SECRET, Duration.ofHours(2)));
    }

    @Test
    void rejectsAMalformedTimestamp() {
        Map<String, String> headers = headersFor(PAYLOAD, ID, "not-a-time", SECRET);

        SignatureVerificationException error = assertThrows(
                SignatureVerificationException.class, () -> Webhooks.verifySignature(PAYLOAD, headers, SECRET));

        assertTrue(error.getMessage().contains("malformed"));
    }

    @Test
    void rejectsARequestMissingTheSignatureHeaders() {
        SignatureVerificationException error = assertThrows(
                SignatureVerificationException.class, () -> Webhooks.verifySignature(PAYLOAD, Map.of(), SECRET));

        assertTrue(error.getMessage().contains("missing required"));
    }

    @Test
    void rejectsARequestMissingAnyOneOfTheThreeHeaders() {
        Map<String, String> signed = headersFor(PAYLOAD, ID, now(), SECRET);
        for (String omitted : new String[] {"X-Webhook-Id", "X-Webhook-Ts", "X-Webhook-Sig"}) {
            Map<String, String> partial = new java.util.HashMap<>(signed);
            partial.remove(omitted);

            assertThrows(
                    SignatureVerificationException.class,
                    () -> Webhooks.verifySignature(PAYLOAD, partial, SECRET),
                    "omitting " + omitted + " should have been rejected");
        }
    }

    @Test
    void rejectsASignatureThatIsNotEvenHex() {
        Map<String, String> signed = headersFor(PAYLOAD, ID, now(), SECRET);
        Map<String, String> broken = Map.of(
                "X-Webhook-Id", ID, "X-Webhook-Ts", signed.get("X-Webhook-Ts"), "X-Webhook-Sig", "sha256=not-hex");

        assertThrows(SignatureVerificationException.class, () -> Webhooks.verifySignature(PAYLOAD, broken, SECRET));
    }

    @Test
    void toleratesASignatureSentWithoutThePrefix() {
        Map<String, String> signed = headersFor(PAYLOAD, ID, now(), SECRET);
        Map<String, String> bare = Map.of(
                "X-Webhook-Id", ID,
                "X-Webhook-Ts", signed.get("X-Webhook-Ts"),
                "X-Webhook-Sig", signed.get("X-Webhook-Sig").substring("sha256=".length()));

        assertArrayEquals(PAYLOAD, Webhooks.verifySignature(PAYLOAD, bare, SECRET));
    }

    // The two below tie Webhooks.sign to that oracle from both directions: the value it produces,
    // and the verifier's acceptance of it.

    @Test
    void signMatchesAnIndependentlyComputedSignature() {
        String timestamp = "2026-01-01T00:00:00Z";
        String expected = headersFor(PAYLOAD, ID, timestamp, SECRET).get("X-Webhook-Sig");

        assertEquals(expected, Webhooks.sign(ID, timestamp, PAYLOAD, SECRET));
    }

    @Test
    void theVerifierAcceptsWhatSignProduces() {
        String timestamp = now();
        Map<String, String> headers = Map.of(
                "X-Webhook-Id", ID,
                "X-Webhook-Ts", timestamp,
                "X-Webhook-Sig", Webhooks.sign(ID, timestamp, PAYLOAD, SECRET));

        assertArrayEquals(PAYLOAD, Webhooks.verifySignature(PAYLOAD, headers, SECRET));
    }

    @Test
    void theVerifyThenParseFlowAReceiverActuallyWrites() {
        // Both halves in the order a handler runs them, with the delivery built by sign() rather
        // than a hand-rolled HMAC. This is what the README shows.
        String timestamp = now();
        Map<String, String> headers = Map.of(
                "X-Webhook-Id", ID,
                "X-Webhook-Ts", timestamp,
                "X-Webhook-Sig", Webhooks.sign(ID, timestamp, PAYLOAD, SECRET));

        WebhookEvent event = Webhooks.verify(PAYLOAD, headers, SECRET);

        assertInstanceOf(EmailDeliveredEvent.class, event);
    }

    @Test
    void theVerifyCombinatorHonoursAWidenedTolerance() {
        String stale = Instant.now().minus(Duration.ofHours(1)).toString();
        Map<String, String> headers = Map.of(
                "X-Webhook-Id", ID,
                "X-Webhook-Ts", stale,
                "X-Webhook-Sig", Webhooks.sign(ID, stale, PAYLOAD, SECRET));

        assertInstanceOf(EmailDeliveredEvent.class, Webhooks.verify(PAYLOAD, headers, SECRET, Duration.ofHours(2)));
    }
}
