package com.mailkube.internal;

import com.mailkube.Version;
import com.mailkube.exception.ConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
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

    /** Environment variable enabling the built-in logging observer, holding a level name. */
    public static final String ENV_LOG = "MAILKUBE_LOG";

    /** The level {@value #ENV_LOG} falls back to when its value names no level. */
    public static final System.Logger.Level DEFAULT_LOG_LEVEL = System.Logger.Level.DEBUG;

    /** The API base URL used when nothing else is configured. */
    public static final String DEFAULT_BASE_URL = "https://api.mailkube.com/mta/v1/";

    /** The per-request timeout used when the caller sets none. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final URI baseUrl;
    private final Duration timeout;
    private final System.Logger.Level logLevel;

    /**
     * Resolve configuration from the arguments, then the environment, then the defaults.
     *
     * @param apiKey the API key, or null to read {@value #ENV_API_KEY}
     * @param baseUrl the API base URL, or null to read {@value #ENV_BASE_URL}
     * @param timeout the per-request timeout, or null for thirty seconds
     * @param logLevel the level to log exchanges at, or null to read {@value #ENV_LOG}
     * @param environment the environment lookup, injected so it can be driven from a test
     */
    public Config(
            String apiKey,
            String baseUrl,
            Duration timeout,
            System.Logger.Level logLevel,
            Map<String, String> environment) {
        String key = apiKey != null ? apiKey : environment.get(ENV_API_KEY);
        if (key == null || key.isEmpty()) {
            throw new ConfigurationException("no API key provided: pass apiKey(...) or set " + ENV_API_KEY);
        }
        String resolved = baseUrl != null ? baseUrl : environment.getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL);
        this.apiKey = key;
        this.baseUrl = parse(resolved);
        this.timeout = Objects.requireNonNullElse(timeout, DEFAULT_TIMEOUT);
        this.logLevel = logLevel != null ? logLevel : levelFromEnvironment(environment.get(ENV_LOG));
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
     * The level to log exchanges at.
     *
     * @return the level, or null when logging was not asked for
     */
    public System.Logger.Level logLevel() {
        return logLevel;
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
        return buildUrl(path, Map.of());
    }

    /**
     * Join a relative path and its filters onto the base URL, refusing any absolute URL off its
     * origin.
     *
     * <p>The origin guard lives here rather than in a resource so that every link-following feature
     * inherits it. A paginated listing follows the server's {@code next} link, and a link naming a
     * foreign host would hand that host the API key.
     *
     * @param path a relative path, or an absolute URL the API itself issued
     * @param query the filters, already stringified; empty to send none
     * @return the absolute URL to request
     */
    public URI buildUrl(String path, Map<String, String> query) {
        String rendered = Query.render(query);
        // A server-issued pagination link already carries its own query; appending to it would
        // produce two `?` and a route nobody serves. Filters only apply to a path we built.
        String target = rendered == null ? path : path + (path.indexOf('?') < 0 ? "?" : "&") + rendered;
        URI resolved = baseUrl.resolve(parse(target));
        boolean sameOrigin = Objects.equals(resolved.getScheme(), baseUrl.getScheme())
                && Objects.equals(resolved.getHost(), baseUrl.getHost())
                && resolved.getPort() == baseUrl.getPort();
        if (!sameOrigin) {
            throw new ConfigurationException(
                    "refusing to follow " + resolved + ": it is not on the configured API origin");
        }
        return resolved;
    }

    /**
     * Read {@value #ENV_LOG} as a level name, tolerating anything it does not recognize.
     *
     * <p>It is a level, not a switch, so {@code MAILKUBE_LOG=WARNING} really does suppress the
     * quieter records rather than merely turning logging on. An unrecognized value falls back to
     * {@link #DEFAULT_LOG_LEVEL} instead of raising: an environment variable that decides how
     * loudly to log must not be able to stop a client being built.
     */
    private static System.Logger.Level levelFromEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return System.Logger.Level.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT_LOG_LEVEL;
        }
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("invalid URL \"" + value + "\": " + e.getMessage());
        }
    }
}
