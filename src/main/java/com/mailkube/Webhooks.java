package com.mailkube;

import com.mailkube.exception.SignatureVerificationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signature verification.
 *
 * <p>Verification is pure and dependency-free: no client instance, no configuration, so you call
 * it directly inside your webhook handler.
 */
public final class Webhooks {

    /** How stale a webhook timestamp may be before it is rejected. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private Webhooks() {}

    /**
     * Verify a webhook's signature and timestamp freshness over the raw body, with the default
     * five-minute tolerance.
     *
     * @param payload the raw request body
     * @param headers the request headers, in any casing
     * @param secret the endpoint's signing secret
     * @return the verified payload, so a caller can parse it in one expression
     */
    public static byte[] verifySignature(byte[] payload, Map<String, String> headers, String secret) {
        return verifySignature(payload, headers, secret, DEFAULT_TOLERANCE);
    }

    /**
     * Verify a webhook's signature and timestamp freshness over the raw body.
     *
     * <p>The signed input is {@code "{id}.{timestamp}."} followed by the <b>raw</b> body,
     * HMAC-SHA256 keyed by the endpoint's signing secret, hex-encoded, and sent as
     * {@code X-Webhook-Sig: sha256=<hex>}. {@code X-Webhook-Ts} is an ISO-8601 timestamp checked
     * for freshness; {@code X-Webhook-Id} is stable across retries, so use it to deduplicate.
     *
     * <p>Verify against the bytes you received. Never parse the body and re-serialize it first:
     * JSON round-tripping reorders keys and normalizes whitespace, and the signature will not
     * match.
     *
     * @param payload the raw request body
     * @param headers the request headers, in any casing
     * @param secret the endpoint's signing secret
     * @param tolerance the freshness window
     * @return the verified payload
     */
    public static byte[] verifySignature(
            byte[] payload, Map<String, String> headers, String secret, Duration tolerance) {
        String id = header(headers, "x-webhook-id");
        String timestamp = header(headers, "x-webhook-ts");
        String signature = header(headers, "x-webhook-sig");
        if (id == null || timestamp == null || signature == null) {
            throw new SignatureVerificationException("missing required webhook signature headers");
        }

        checkFreshness(timestamp, tolerance);
        checkSignature(payload, id, timestamp, signature, secret);
        return payload;
    }

    private static void checkFreshness(String timestamp, Duration tolerance) {
        Instant parsed;
        try {
            parsed = Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new SignatureVerificationException("malformed X-Webhook-Ts timestamp");
        }
        if (Duration.between(parsed, Instant.now()).abs().compareTo(tolerance) > 0) {
            throw new SignatureVerificationException("timestamp is outside the freshness window");
        }
    }

    private static void checkSignature(byte[] payload, String id, String timestamp, String signature, String secret) {
        byte[] expected = sign(payload, id, timestamp, secret);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(strip(signature));
        } catch (IllegalArgumentException e) {
            throw new SignatureVerificationException("signature mismatch");
        }
        // MessageDigest.isEqual is the constant-time comparison; a plain Arrays.equals would leak
        // how much of the digest matched.
        if (!MessageDigest.isEqual(provided, expected)) {
            throw new SignatureVerificationException("signature mismatch");
        }
    }

    private static byte[] sign(byte[] payload, String id, String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(payload);
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is mandatory on every conforming JRE, so this cannot happen in practice;
            // it is wrapped rather than declared so callers do not write a checked-exception
            // branch that can never run.
            throw new SignatureVerificationException("HMAC-SHA256 is unavailable: " + e.getMessage());
        }
    }

    private static String strip(String signature) {
        return signature.startsWith(PREFIX) ? signature.substring(PREFIX.length()) : signature;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
