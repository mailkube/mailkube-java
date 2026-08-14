package com.mailkube.internal;

import com.mailkube.RequestObserver;
import java.time.Duration;

/**
 * The built-in observer: writes one line per exchange to {@link System.Logger}.
 *
 * <p>{@code System.Logger} rather than a logging facade, for the same reason this SDK has no other
 * dependency. It is {@code java.base}, and it is a real facade: an application that installs a
 * {@code LoggerFinder}, or runs Spring Boot's {@code jul-to-slf4j} bridge, gets these records in
 * its own logging framework with no adapter from us.
 *
 * <p>It is also silent by default <em>by construction</em> rather than by a flag. With no
 * {@code LoggerFinder} installed the backing implementation is {@code java.util.logging}, whose
 * root level is {@code INFO}, so records below it are dropped before this class is even reached.
 * Turning this observer on and seeing nothing means the host's logging is not configured for that
 * level, which is the host's decision to make.
 */
public final class LoggingObserver implements RequestObserver {

    private static final System.Logger LOGGER = System.getLogger("com.mailkube");

    private final System.Logger.Level level;

    /**
     * Log every exchange at one level.
     *
     * @param level the level to log at
     */
    public LoggingObserver(System.Logger.Level level) {
        this.level = level;
    }

    @Override
    public void onResponse(
            String method, String path, Integer status, String requestId, Duration elapsed, Throwable error) {
        if (!LOGGER.isLoggable(level)) {
            return;
        }
        // Method, path, status, request id, elapsed. Nothing else exists to log: the observer
        // contract carries no headers and no bodies, so there is nothing here to redact.
        if (error == null) {
            LOGGER.log(
                    level,
                    "{0} {1} -> {2} in {3}ms (request {4})",
                    method,
                    path,
                    status,
                    elapsed.toMillis(),
                    requestId);
        } else {
            LOGGER.log(level, "{0} {1} -> no response after {2}ms: {3}", method, path, elapsed.toMillis(), error);
        }
    }
}
