package com.mailkube.internal;

import com.mailkube.Version;
import com.mailkube.exception.ConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Resolved client configuration: the key, the origin, the timeout and the default headers.
 *
 * <p>This is the only place configuration is read, and the only place a URL is built. Keeping the
 * origin guard here rather than in a resource protects every future link-following feature for
 * free. Instances are immutable, which is half of why one client is safe to share.
 */
public final class Config {

    /** Environment variable holding the API key. */
    public static final String ENV_API_KEY = "MAILKUBE_API_KEY";

    /** Environment variable overriding the API base URL. */
    public static final String ENV_BASE_URL = "MAILKUBE_BASE_URL";

    /** The API base URL used when nothing else is configured. */
    public static final String DEFAULT_BASE_URL = "https://api.mailkube.com/mta/v1/";

    /** The per-request timeout used when the caller sets none. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final URI baseUrl;
    private final Duration timeout;

    /**
     * Resolve configuration from the arguments, then the environment, then the defaults.
     *
     * @param apiKey the API key, or null to read {@value #ENV_API_KEY}
     * @param baseUrl the API base URL, or null to read {@value #ENV_BASE_URL}
     * @param timeout the per-request timeout, or null for thirty seconds
     * @param environment the environment lookup, injected so it can be driven from a test
     */
    public Config(String apiKey, String baseUrl, Duration timeout, Map<String, String> environment) {
        String key = apiKey != null ? apiKey : environment.get(ENV_API_KEY);
        if (key == null || key.isEmpty()) {
            throw new ConfigurationException("no API key provided: pass apiKey(...) or set " + ENV_API_KEY);
        }
        String resolved = baseUrl != null ? baseUrl : environment.getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL);
        this.apiKey = key;
        this.baseUrl = parse(resolved);
        this.timeout = Objects.requireNonNullElse(timeout, DEFAULT_TIMEOUT);
    }

    /**
     * The resolved API base URL.
     *
     * @return the base URL
     */
    public URI baseUrl() {
        return baseUrl;
    }

    /**
     * The per-request timeout.
     *
     * @return the timeout
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * The auth and non-browser identification headers sent on every request.
     *
     * <p>The User-Agent is required: the API rejects a request without one. It reports
     * {@link Version#current()}, which reads the jar manifest, so it cannot drift from the
     * released version.
     *
     * @return the default headers
     */
    public Map<String, String> defaultHeaders() {
        return Map.of(
                "Authorization",
                "Bearer " + apiKey,
                "User-Agent",
                "mailkube-java/" + Version.current(),
                "Content-Type",
                "application/json",
                "Accept",
                "application/json");
    }

    /**
     * Join a relative path onto the base URL, refusing any absolute URL off its origin.
     *
     * <p>Every request carries the Authorization header, so following a link that names a foreign
     * host would hand that host the API key.
     *
     * @param path a relative path, or an absolute URL the API itself issued
     * @return the absolute URL to request
     */
    public URI buildUrl(String path) {
        URI resolved = baseUrl.resolve(parse(path));
        boolean sameOrigin = Objects.equals(resolved.getScheme(), baseUrl.getScheme())
                && Objects.equals(resolved.getHost(), baseUrl.getHost())
                && resolved.getPort() == baseUrl.getPort();
        if (!sameOrigin) {
            throw new ConfigurationException(
                    "refusing to follow " + resolved + ": it is not on the configured API origin");
        }
        return resolved;
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("invalid URL \"" + value + "\": " + e.getMessage());
        }
    }
}
